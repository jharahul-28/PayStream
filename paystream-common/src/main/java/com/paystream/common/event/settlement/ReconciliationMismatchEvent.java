package com.paystream.common.event.settlement;

public record ReconciliationMismatchEvent(
        String batchId,
        String discrepancyType,
        long   expectedAmount,
        long   actualAmount,
        String correlationId
) {}
