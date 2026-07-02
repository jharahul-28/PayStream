package com.paystream.settlement;

import com.paystream.settlement.application.service.ReconciliationService;
import com.paystream.settlement.infrastructure.messaging.SettlementEventPublisher;
import com.paystream.settlement.infrastructure.persistence.entity.ReconciliationAlertEntity;
import com.paystream.settlement.infrastructure.persistence.entity.SettlementBatchEntity;
import com.paystream.settlement.infrastructure.persistence.repository.ReconciliationAlertJpaRepository;
import com.paystream.settlement.infrastructure.persistence.repository.SettlementBatchJpaRepository;
import com.paystream.settlement.infrastructure.persistence.repository.SettlementItemJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationTest {

    @Mock private SettlementBatchJpaRepository     batchRepo;
    @Mock private SettlementItemJpaRepository      itemRepo;
    @Mock private ReconciliationAlertJpaRepository alertRepo;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(
                batchRepo, itemRepo, alertRepo, new SimpleMeterRegistry()
        );
    }

    @Test
    void matchingTotals_marksBatchReconciled_noAlert() {
        SettlementBatchEntity batch = createBatch("batch-1", 100000L);
        when(itemRepo.sumAmountByBatchId("batch-1")).thenReturn(100000L);

        reconciliationService.reconcileBatch(batch);

        // Alert NOT created
        verify(alertRepo, never()).save(any());
        // Batch marked reconciled
        verify(batchRepo).save(batch);
        assertThat(batch.isReconciled()).isTrue();
    }

    @Test
    void mismatchedTotals_createsAlert_incrementsMetric() {
        SettlementBatchEntity batch = createBatch("batch-2", 100000L);
        when(itemRepo.sumAmountByBatchId("batch-2")).thenReturn(95000L); // 5000 discrepancy

        reconciliationService.reconcileBatch(batch);

        // Alert IS created
        ArgumentCaptor<ReconciliationAlertEntity> alertCaptor =
                ArgumentCaptor.forClass(ReconciliationAlertEntity.class);
        verify(alertRepo).save(alertCaptor.capture());
        ReconciliationAlertEntity alert = alertCaptor.getValue();
        assertThat(alert.getBatchId()).isEqualTo("batch-2");
        assertThat(alert.getDiscrepancyType()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(alert.getExpectedAmount()).isEqualTo(95000L);
        assertThat(alert.getActualAmount()).isEqualTo(100000L);

        // Batch NOT marked reconciled
        verify(batchRepo, never()).save(any());
        assertThat(batch.isReconciled()).isFalse();
    }

    @Test
    void zeroDiscrepancy_isConsideredMatch() {
        SettlementBatchEntity batch = createBatch("batch-3", 0L);
        when(itemRepo.sumAmountByBatchId("batch-3")).thenReturn(0L);

        reconciliationService.reconcileBatch(batch);

        verify(alertRepo, never()).save(any());
        verify(batchRepo).save(batch);
    }

    private SettlementBatchEntity createBatch(String id, long grossAmount) {
        SettlementBatchEntity batch = new SettlementBatchEntity(id, "merchant-1", "SETTLED", "INR", LocalDate.now());
        batch.markSettled(10, grossAmount, (long)(grossAmount * 0.01));
        return batch;
    }
}
