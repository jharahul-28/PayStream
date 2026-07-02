package com.paystream.common.event.settlement;

public record SettlementBatchTriggerEvent(
        String batchId,
        String merchantId,
        String currency,
        String settlementDate,
        String correlationId
) {}
