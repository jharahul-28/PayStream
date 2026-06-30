package com.paystream.wallet.infrastructure.persistence.entity;

import com.paystream.wallet.domain.model.WalletStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity for the {@code wallets} table.
 * Must remain separate from the domain {@link com.paystream.wallet.domain.model.Wallet}.
 * Only the persistence adapter reads/writes this class.
 */
@Entity
@Table(name = "wallets")
public class WalletEntity {

    @Id
    @Column(name = "id", length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WalletStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WalletEntity() {}

    public WalletEntity(String id, String userId, long balance, String currency, WalletStatus status) {
        this.id        = id;
        this.userId    = userId;
        this.balance   = balance;
        this.currency  = currency;
        this.status    = status;
        this.createdAt = Instant.now();
    }

    // Mutators — only used by the persistence adapter to sync domain state
    public void setBalance(long balance) { this.balance = balance; }
    public void setStatus(WalletStatus status) { this.status = status; }

    public String       getId()        { return id; }
    public String       getUserId()    { return userId; }
    public long         getBalance()   { return balance; }
    public String       getCurrency()  { return currency; }
    public WalletStatus getStatus()    { return status; }
    public Integer      getVersion()   { return version; }
    public Instant      getCreatedAt() { return createdAt; }
    public Instant      getUpdatedAt() { return updatedAt; }
}
