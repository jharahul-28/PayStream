package com.paystream.common.event.settlement;

import java.time.LocalDate;

public record SettlementCompletedEvent(
        String    batchId,
        String    merchantId,
        long      grossAmount,
        long      feeAmount,
        long      netAmount,
        int       paymentCount,
        String    currency,
        LocalDate settlementDate,
        String    correlationId
) {}
