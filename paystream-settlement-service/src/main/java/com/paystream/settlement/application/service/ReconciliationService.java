package com.paystream.settlement.application.service;

import com.paystream.common.util.IdGenerator;
import com.paystream.settlement.infrastructure.persistence.entity.ReconciliationAlertEntity;
import com.paystream.settlement.infrastructure.persistence.entity.SettlementBatchEntity;
import com.paystream.settlement.infrastructure.persistence.repository.ReconciliationAlertJpaRepository;
import com.paystream.settlement.infrastructure.persistence.repository.SettlementBatchJpaRepository;
import com.paystream.settlement.infrastructure.persistence.repository.SettlementItemJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Nightly reconciliation job — runs at 03:30 IST (30 min after settlement scheduler).
 *
 * For each SETTLED batch that has not been reconciled:
 *   expected = SUM(settlement_items.amount) WHERE batch_id = ?
 *   actual   = settlement_batch.gross_amount
 *   If expected != actual: create reconciliation_alert, increment metric.
 *   If match: mark batch reconciled = TRUE.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final SettlementBatchJpaRepository      batchRepo;
    private final SettlementItemJpaRepository       itemRepo;
    private final ReconciliationAlertJpaRepository  alertRepo;
    private final Counter                           discrepancyCounter;

    public ReconciliationService(SettlementBatchJpaRepository batchRepo,
                                  SettlementItemJpaRepository itemRepo,
                                  ReconciliationAlertJpaRepository alertRepo,
                                  MeterRegistry meterRegistry) {
        this.batchRepo  = batchRepo;
        this.itemRepo   = itemRepo;
        this.alertRepo  = alertRepo;
        this.discrepancyCounter = Counter.builder("settlement.reconciliation.discrepancies.total")
                .description("Total reconciliation discrepancies detected")
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    public void reconcileSettledBatches() {
        Instant since = Instant.now().minus(2, ChronoUnit.DAYS);
        List<SettlementBatchEntity> batches = batchRepo.findSettledNotReconciled(since);
        log.info("Reconciliation job started — {} batches to reconcile correlationId={}",
                batches.size(), MDC.get("correlationId"));

        for (SettlementBatchEntity batch : batches) {
            try {
                reconcileBatch(batch);
            } catch (Exception e) {
                log.error("Reconciliation error batchId={} error={}", batch.getId(), e.getMessage(), e);
            }
        }
        log.info("Reconciliation job completed");
    }

    /**
     * Exposed for testing and manual triggers.
     */
    @Transactional
    public void reconcileBatch(SettlementBatchEntity batch) {
        long expected = itemRepo.sumAmountByBatchId(batch.getId());
        long actual   = batch.getGrossAmount();

        if (expected != actual) {
            log.error("RECONCILIATION DISCREPANCY batchId={} expected={} actual={} diff={}",
                    batch.getId(), expected, actual, Math.abs(expected - actual));

            ReconciliationAlertEntity alert = new ReconciliationAlertEntity(
                    IdGenerator.generate(),
                    batch.getId(),
                    "AMOUNT_MISMATCH",
                    expected,
                    actual,
                    "Settlement gross_amount=" + actual + " does not match sum of items=" + expected,
                    Instant.now()
            );
            alertRepo.save(alert);
            discrepancyCounter.increment();
        } else {
            batch.markReconciled();
            batchRepo.save(batch);
            log.info("Batch reconciled successfully batchId={} grossAmount={}", batch.getId(), actual);
        }
    }
}
