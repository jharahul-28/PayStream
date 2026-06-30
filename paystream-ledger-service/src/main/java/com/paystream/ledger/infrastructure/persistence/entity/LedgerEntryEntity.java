package com.paystream.ledger.infrastructure.persistence.entity;

import com.paystream.ledger.domain.model.EntryType;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for the {@code ledger_entries} table.
 * Immutable by design: no setters except what JPA requires internally.
 * Must remain separate from the domain {@link com.paystream.ledger.domain.model.LedgerEntry}.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

    @Id
    @Column(name = "id", length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private String referenceId;

    @Column(name = "reference_type", nullable = false, updatable = false)
    private String referenceType;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntryEntity() {}

    public LedgerEntryEntity(String id, String accountId, EntryType entryType, long amount,
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
