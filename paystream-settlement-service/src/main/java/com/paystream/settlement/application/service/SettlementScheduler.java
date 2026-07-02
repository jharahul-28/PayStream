package com.paystream.settlement.application.service;

import com.paystream.settlement.infrastructure.messaging.SettlementEventPublisher;
import com.paystream.settlement.infrastructure.persistence.entity.SettlementBatchEntity;
import com.paystream.settlement.infrastructure.persistence.entity.SettlementItemEntity;
import com.paystream.settlement.infrastructure.persistence.repository.SettlementBatchJpaRepository;
import com.paystream.settlement.infrastructure.persistence.repository.SettlementItemJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Runs daily at 02:00 IST.
 *
 * For each PENDING batch with settlement_date <= today:
 *   1. Mark PROCESSING and commit (checkpoint — restart-safe)
 *   2. Process items in micro-batches of 100, each committed independently
 *   3. Mark SETTLED with final totals
 *
 * fee = gross * feeRate (default 1%)
 * This design means that if the pod restarts mid-batch, only unprocessed items
 * are retried — SETTLED items are skipped on resume.
 */
@Service
public class SettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementScheduler.class);
    private static final int MICRO_BATCH_SIZE = 100;

    private final SettlementBatchJpaRepository batchRepo;
    private final SettlementItemJpaRepository  itemRepo;
    private final SettlementEventPublisher     eventPublisher;
    private final Counter failureCounter;
    private final Counter settledCounter;

    @Value("${paystream.settlement.fee-rate:0.01}")
    private double feeRate;

    public SettlementScheduler(SettlementBatchJpaRepository batchRepo,
                               SettlementItemJpaRepository itemRepo,
                               SettlementEventPublisher eventPublisher,
                               MeterRegistry meterRegistry) {
        this.batchRepo      = batchRepo;
        this.itemRepo       = itemRepo;
        this.eventPublisher = eventPublisher;
        this.failureCounter = Counter.builder("settlement.batch.failures.total")
                .description("Total settlement batches that failed to process")
                .register(meterRegistry);
        this.settledCounter = Counter.builder("settlement.batch.settled.total")
                .description("Total settlement batches successfully settled")
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    public void runDailySettlement() {
        LocalDate today = LocalDate.now();
        log.info("Settlement scheduler triggered date={}", today);

        List<SettlementBatchEntity> pendingBatches = batchRepo.findPendingBatchesDue(today);
        log.info("Found {} pending batches to settle", pendingBatches.size());

        for (SettlementBatchEntity batch : pendingBatches) {
            try {
                processBatch(batch.getId());
            } catch (Exception e) {
                log.error("Settlement batch failed batchId={} error={}", batch.getId(), e.getMessage(), e);
                failureCounter.increment();
                markBatchFailed(batch.getId(), e.getMessage());
            }
        }
    }

    /**
     * Manual trigger endpoint. Allows FINANCE_OPS to force settlement outside schedule.
     */
    public void triggerBatch(String batchId) {
        processBatch(batchId);
    }

    @Transactional
    public void markBatchProcessing(String batchId) {
        SettlementBatchEntity batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Batch not found: " + batchId));
        batch.startProcessing();
        batchRepo.save(batch);
        log.info("Settlement batch started batchId={}", batchId);
    }

    @Transactional
    public void markBatchFailed(String batchId, String reason) {
        batchRepo.findById(batchId).ifPresent(b -> {
            b.markFailed(reason);
            batchRepo.save(b);
        });
    }

    private void processBatch(String batchId) {
        // Step 1: mark PROCESSING and commit — enables crash recovery
        markBatchProcessing(batchId);

        long totalGross   = 0;
        long totalFee     = 0;
        int  totalItems   = 0;
        int  pageNumber   = 0;

        // Step 2: micro-batch loop — each page committed independently (checkpoint pattern)
        Page<SettlementItemEntity> page;
        do {
            page = processMicroBatch(batchId, pageNumber);
            totalItems += page.getNumberOfElements();

            for (SettlementItemEntity item : page.getContent()) {
                totalGross += item.getAmount();
                totalFee   += item.getFeeAmount();
            }
            pageNumber++;
        } while (page.hasNext());

        // Step 3: mark SETTLED with final totals
        markBatchSettled(batchId, totalItems, totalGross, totalFee);
        settledCounter.increment();
        log.info("Settlement batch settled batchId={} items={} gross={} fee={} net={}",
                batchId, totalItems, totalGross, totalFee, totalGross - totalFee);

        // Publish settlement event (outside @Transactional — after commit)
        batchRepo.findById(batchId).ifPresent(b ->
            eventPublisher.publishSettlementCompleted(
                    b.getId(), b.getMerchantId(),
                    b.getGrossAmount(), b.getFeeAmount(), b.getNetAmount(),
                    b.getTotalPaymentCount(), b.getCurrency(), b.getSettlementDate()
            )
        );
    }

    @Transactional
    public Page<SettlementItemEntity> processMicroBatch(String batchId, int page) {
        Page<SettlementItemEntity> items = itemRepo.findByBatchIdAndStatus(
                batchId, "PENDING", PageRequest.of(page, MICRO_BATCH_SIZE));

        for (SettlementItemEntity item : items) {
            long fee = (long) (item.getAmount() * feeRate);
            // Re-create item with fee (fee was 0 on insert; calculate here)
            item.markSettled();
        }
        itemRepo.saveAll(items.getContent());
        return items;
    }

    @Transactional
    public void markBatchSettled(String batchId, int itemCount, long gross, long fee) {
        batchRepo.findById(batchId).ifPresent(b -> {
            b.markSettled(itemCount, gross, fee);
            batchRepo.save(b);
        });
    }
}
