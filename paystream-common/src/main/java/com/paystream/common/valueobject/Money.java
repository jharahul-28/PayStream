package com.paystream.common.valueobject;

import com.paystream.common.exception.ValidationException;

import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary value expressed in minor units (e.g. paise for INR, cents for USD).
 * NEVER use double or float for money arithmetic — this class enforces long throughout.
 *
 * Invariants:
 *  - amount is always non-negative
 *  - currency is always a valid ISO 4217 code
 *  - arithmetic operations require the same currency
 */
public final class Money {

    private final long   amount;   // minor units — e.g. 100000 paise = ₹1000
    private final String currency; // ISO 4217 — e.g. "INR", "USD"

    private Money(long amount, String currency) {
        this.amount   = amount;
        this.currency = currency;
    }

    public static Money of(long minorUnits, String currencyCode) {
        validateCurrency(currencyCode);
        if (minorUnits < 0) {
            throw new ValidationException("Money amount cannot be negative: " + minorUnits);
        }
        return new Money(minorUnits, currencyCode.toUpperCase());
    }

    public static Money fromMinorUnits(long minorUnits, String currencyCode) {
        return of(minorUnits, currencyCode);
    }

    // -------------------------------------------------------------------------
    // Arithmetic
    // -------------------------------------------------------------------------

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount + other.amount, this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        if (other.amount > this.amount) {
            throw new ValidationException(
                    String.format("Subtraction would produce negative money: %d - %d", this.amount, other.amount));
        }
        return new Money(this.amount - other.amount, this.currency);
    }

    /** Returns a percentage of this amount, truncating fractional minor units. */
    public Money percentage(int basisPoints) {
        return new Money((this.amount * basisPoints) / 10000L, this.currency);
    }

    // -------------------------------------------------------------------------
    // Predicates
    // -------------------------------------------------------------------------

    public boolean isPositive() { return amount > 0; }
    public boolean isZero()     { return amount == 0; }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public long   toMinorUnits() { return amount; }
    public String getCurrency()  { return currency; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new ValidationException(
                    String.format("Currency mismatch: %s vs %s", this.currency, other.currency));
        }
    }

    private static void validateCurrency(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("Currency code must not be blank");
        }
        try {
            Currency.getInstance(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid ISO 4217 currency code: " + code);
        }
    }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money m)) return false;
        return amount == m.amount && currency.equals(m.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
