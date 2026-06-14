package com.paystream.auth.domain.model;

/**
 * Roles available in the PayStream system.
 * Roles are stored as strings in the database to allow future expansion
 * without a schema migration.
 */
public enum Role {
    CUSTOMER,
    MERCHANT,
    FRAUD_ANALYST,
    FINANCE_OPS,
    ADMIN
}
