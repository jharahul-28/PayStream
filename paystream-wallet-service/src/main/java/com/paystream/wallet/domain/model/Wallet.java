package com.paystream.wallet.domain.model;

import com.paystream.common.constant.ErrorCode;
import com.paystream.common.exception.DomainException;
import com.paystream.common.exception.InsufficientFundsException;

import java.time.Instant;

/**
 * Wallet domain entity. Pure Java — zero framework or JPA annotations.
 * All state mutation goes through behaviour methods so that invariants are enforced locally.
 *
 * Invariants:
 *  - balance is always >= 0 (also enforced by DB CHECK constraint)
 *  - debit / credit are only allowed on ACTIVE wallets
 *  - version is incremented on every mutation (supports optimistic locking in the adapter)
 */
public class Wallet {

    private final String       id;
    private final String       userId;
    private long               balance;   // minor units — never double/float
    private final String       currency;  // ISO 4217
    private WalletStatus       status;
    private int                version;
    private final Instant      createdAt;
    private Instant            updatedAt;

    public Wallet(String id, String userId, long balance, String currency,
                  WalletStatus status, int version, Instant createdAt, Instant updatedAt) {
        this.id        = id;
        this.userId    = userId;
        this.balance   = balance;
        this.currency  = currency;
        this.status    = status;
        this.version   = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------------------------
    // Behaviour methods
    // -------------------------------------------------------------------------

    /**
     * Debits {@code amount} minor units from this wallet.
     * Throws if wallet is not ACTIVE or balance would go negative.
     */
    public void debit(long amount) {
        requireActive();
        if (amount <= 0) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Debit amount must be positive");
        }
        if (balance < amount) {
            throw new InsufficientFundsException(balance, amount);
        }
        this.balance -= amount;
        this.version++;
        this.updatedAt = Instant.now();
    }

    /**
     * Credits {@code amount} minor units to this wallet.
     * Throws if wallet is not ACTIVE.
     */
    public void credit(long amount) {
        requireActive();
        if (amount <= 0) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Credit amount must be positive");
        }
        this.balance += amount;
        this.version++;
        this.updatedAt = Instant.now();
    }

    /** Freezes the wallet, preventing any debit or credit. */
    public void freeze() {
        if (status == WalletStatus.CLOSED) {
            throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION, "Cannot freeze a closed wallet");
        }
        this.status    = WalletStatus.FROZEN;
        this.updatedAt = Instant.now();
    }

    /** Unfreezes a previously frozen wallet. */
    public void unfreeze() {
        if (status != WalletStatus.FROZEN) {
            throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Can only unfreeze a FROZEN wallet, current status: " + status);
        }
        this.status    = WalletStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == WalletStatus.ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String       getId()        { return id; }
    public String       getUserId()    { return userId; }
    public long         getBalance()   { return balance; }
    public String       getCurrency()  { return currency; }
    public WalletStatus getStatus()    { return status; }
    public int          getVersion()   { return version; }
    public Instant      getCreatedAt() { return createdAt; }
    public Instant      getUpdatedAt() { return updatedAt; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireActive() {
        if (status != WalletStatus.ACTIVE) {
            throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Wallet is not ACTIVE — current status: " + status);
        }
    }
}
