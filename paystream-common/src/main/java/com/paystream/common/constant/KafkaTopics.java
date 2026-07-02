package com.paystream.common.constant;

/**
 * Canonical topic names for all PayStream Kafka topics.
 * Partitioned by userId/walletId so events for a single entity arrive
 * at the same consumer thread — critical for ordered ledger processing.
 *
 * Retry topics and DLQ topics are created by @RetryableTopic in consumers.
 * DLQ suffix: .dlq  (e.g. wallet.debited.dlq)
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // Payment domain
    public static final String PAYMENTS_INITIATED        = "payments.initiated";        // key=userId, partitions=32
    public static final String PAYMENTS_COMPLETED        = "payments.completed";        // key=userId, partitions=32
    public static final String PAYMENTS_FAILED           = "payments.failed";           // key=userId, partitions=32

    // Wallet domain — consumed by ledger-service to write ledger entries
    public static final String WALLET_DEBITED            = "wallet.debited";            // key=walletId, partitions=32
    public static final String WALLET_CREDITED           = "wallet.credited";           // key=walletId, partitions=32

    // Fraud domain
    public static final String FRAUD_CHECK_REQUESTED     = "fraud.check.requested";     // key=paymentId, partitions=16
    public static final String FRAUD_SCORE_COMPUTED      = "fraud.score.computed";      // key=paymentId, partitions=16

    // Notifications
    public static final String NOTIFICATIONS_SEND        = "notifications.send";        // key=userId, partitions=8

    // Webhooks
    public static final String WEBHOOKS_DELIVERY         = "webhooks.delivery";         // key=merchantId, partitions=8

    // Settlements
    public static final String SETTLEMENTS_BATCH_TRIGGER = "settlements.batch.trigger"; // key=merchantId, partitions=4

    // Audit — consumed by audit-service
    public static final String AUDIT_EVENTS              = "audit.events";              // key=entityId, partitions=16

    // Fraud M4 extensions
    public static final String FRAUD_REVIEW_REQUIRED     = "fraud.review.required";     // key=paymentId, partitions=16
    public static final String SUSPICIOUS_TXN_DETECTED   = "fraud.suspicious.detected"; // key=userId, partitions=16

    // Settlement M4 extensions
    public static final String SETTLEMENT_COMPLETED      = "settlement.completed";      // key=merchantId, partitions=4
    public static final String SETTLEMENT_FAILED         = "settlement.failed";         // key=merchantId, partitions=4
    public static final String RECONCILIATION_COMPLETED  = "reconciliation.completed";  // key=batchId, partitions=4
    public static final String RECONCILIATION_MISMATCH   = "reconciliation.mismatch";   // key=batchId, partitions=4

    // AI events
    public static final String AI_ANALYSIS_REQUESTED     = "ai.analysis.requested";    // key=entityId, partitions=8
    public static final String AI_ANALYSIS_COMPLETED     = "ai.analysis.completed";    // key=entityId, partitions=8
}
