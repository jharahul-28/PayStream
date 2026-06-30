package com.paystream.ledger.domain.model;

import java.time.Instant;

/**
 * Immutable ledger entry — a single leg of a double-entry transaction.
 * Once created, entries must never be modified or deleted.
 *
 * Amount convention:
 *  - DEBIT  entries carry a negative amount (money leaves the account)
 *  - CREDIT entries carry a positive amount (money arrives in the account)
 *
 * Double-entry invariant: for every reference_id, the sum of all amounts must equal zero.
 */
public final class LedgerEntry {

    private final String    id;
    private final String    accountId;
    private final EntryType entryType;
    private final long      amount;       // signed minor units
    private final String    currency;
    private final String    referenceId;
    private final String    referenceType;
    private final String    description;
    private final Instant   createdAt;

    public LedgerEntry(String id, String accountId, EntryType entryType, long amount,
                       String currency, String referenceId, String referenceType,
                       String description, Instant createdAt) {
        this.id            = id;
        this.accountId     = accountId;
        this.entryType     = entryType;
        this.amount        = amount;
        this.currency      = currency;
        this.referenceId   = referenceId;
        this.referenceType = referenceType;
        this.description   = description;
        this.createdAt     = createdAt;
    }

    public String    getId()            { return id; }
    public String    getAccountId()     { return accountId; }
    public EntryType getEntryType()     { return entryType; }
    public long      getAmount()        { return amount; }
    public String    getCurrency()      { return currency; }
    public String    getReferenceId()   { return referenceId; }
    public String    getReferenceType() { return referenceType; }
    public String    getDescription()   { return description; }
    public Instant   getCreatedAt()     { return createdAt; }
}
