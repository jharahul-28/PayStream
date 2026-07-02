package com.paystream.settlement.infrastructure.messaging;

import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.settlement.ReconciliationMismatchEvent;
import com.paystream.common.event.settlement.SettlementCompletedEvent;
import com.paystream.common.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

@Component
public class SettlementEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SettlementEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SettlementEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishSettlementCompleted(String batchId, String merchantId,
                                            long grossAmount, long feeAmount, long netAmount,
                                            int paymentCount, String currency,
                                            LocalDate settlementDate) {
        try {
            SettlementCompletedEvent payload = new SettlementCompletedEvent(
                    batchId, merchantId, grossAmount, feeAmount, netAmount,
                    paymentCount, currency, settlementDate, MDC.get("correlationId")
            );
            BaseEvent<SettlementCompletedEvent> envelope = new BaseEvent<>(
                    IdGenerator.generate(), "SettlementCompleted", "1.0", Instant.now(),
                    MDC.get("correlationId"), "settlement-service", payload
            );
            kafkaTemplate.send(KafkaTopics.SETTLEMENT_COMPLETED, merchantId, envelope);
            log.debug("SettlementCompleted published batchId={}", batchId);
        } catch (Exception e) {
            log.warn("Failed to publish SettlementCompleted batchId={} error={}", batchId, e.getMessage());
        }
    }

    public void publishReconciliationMismatch(String batchId, long expected, long actual) {
        try {
            ReconciliationMismatchEvent payload = new ReconciliationMismatchEvent(
                    batchId, "AMOUNT_MISMATCH", expected, actual, MDC.get("correlationId")
            );
            BaseEvent<ReconciliationMismatchEvent> envelope = new BaseEvent<>(
                    IdGenerator.generate(), "ReconciliationMismatch", "1.0", Instant.now(),
                    MDC.get("correlationId"), "settlement-service", payload
            );
            kafkaTemplate.send(KafkaTopics.RECONCILIATION_MISMATCH, batchId, envelope);
        } catch (Exception e) {
            log.warn("Failed to publish ReconciliationMismatch batchId={} error={}", batchId, e.getMessage());
        }
    }
}
