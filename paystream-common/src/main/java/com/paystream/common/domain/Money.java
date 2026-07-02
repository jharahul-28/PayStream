package com.paystream.common.domain;

import com.paystream.common.exception.ValidationException;

import java.util.Currency;
import java.util.Objects;

/**
 * Immutable money value object.
 * All amounts are stored in the currency's <em>minor unit</em> (e.g. paise for INR,
 * cents for USD). Floating-point arithmetic is never used — the underlying
 * representation is a {@code long}, which eliminates IEEE-754 rounding errors.
 *
 * <pre>
 *   Money amount = Money.of(100_000L, "INR"); // Rs. 1,000.00
 *   Money fee    = amount.percentage(1);       // Rs. 10.00
 *   Money net    = amount.subtract(fee);       // Rs. 990.00
 * </pre>
 */
public final class Money {

    private final long minorUnits;
    private final String currencyCode;

    private Money(long minorUnits, String currencyCode) {
        this.minorUnits   = minorUnits;
        this.currencyCode = currencyCode;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    public static Money of(long minorUnits, String currencyCode) {
        validateCurrency(currencyCode);
        return new Money(minorUnits, currencyCode);
    }

    public static Money zero(String currencyCode) {
        validateCurrency(currencyCode);
        return new Money(0L, currencyCode);
    }

    public static Money fromMinorUnits(long minorUnits, String currencyCode) {
        return of(minorUnits, currencyCode);
    }

    // -------------------------------------------------------------------------
    // Arithmetic — all operations return new instances (immutability)
    // -------------------------------------------------------------------------

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.minorUnits + other.minorUnits, this.currencyCode);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.minorUnits - other.minorUnits, this.currencyCode);
    }

    /**
     * Returns {@code percent}% of this amount, truncated to the minor unit.
     * e.g. {@code Money.of(100_000, "INR").percentage(1)} = 1000 paise = Rs. 10.
     */
    public Money percentage(int percent) {
        if (percent < 0 || percent > 100) {
            throw new ValidationException("Percentage must be between 0 and 100, got: " + percent);
        }
        return new Money((this.minorUnits * percent) / 100L, this.currencyCode);
    }

    // -------------------------------------------------------------------------
    // Predicates
    // -------------------------------------------------------------------------

    public boolean isPositive() {
        return minorUnits > 0;
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    public boolean isNegative() {
        return minorUnits < 0;
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.minorUnits > other.minorUnits;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.minorUnits < other.minorUnits;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public long toMinorUnits() {
        return minorUnits;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    private static void validateCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new ValidationException("Currency code must not be blank");
        }
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid ISO-4217 currency code: " + currencyCode);
        }
    }

    private void assertSameCurrency(Money other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new ValidationException(
                    "Currency mismatch: cannot operate on " + this.currencyCode + " and " + other.currencyCode);
        }
    }

    // -------------------------------------------------------------------------
    // Standard overrides
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return minorUnits == money.minorUnits && Objects.equals(currencyCode, money.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minorUnits, currencyCode);
    }

    @Override
    public String toString() {
        return minorUnits + " " + currencyCode;
    }
}
