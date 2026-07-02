package com.paystream.common.valueobject;

import com.paystream.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Money value object.
 * Verifies arithmetic correctness, immutability, and invalid input rejection.
 */
@DisplayName("Money Value Object")
class MoneyValueObjectTest {

    @Test
    @DisplayName("Money.of creates correct amount and currency")
    void of_createsCorrect() {
        Money m = Money.of(100000L, "INR");
        assertThat(m.toMinorUnits()).isEqualTo(100000L);
        assertThat(m.getCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("add() returns new Money with summed amount (immutable)")
    void add_returnsNewInstance() {
        Money a = Money.of(100L, "INR");
        Money b = Money.of(50L, "INR");
        Money result = a.add(b);
        assertThat(result.toMinorUnits()).isEqualTo(150L);
        assertThat(a.toMinorUnits()).isEqualTo(100L); // original unchanged
    }

    @Test
    @DisplayName("subtract() returns correct remainder")
    void subtract_correct() {
        Money a = Money.of(200L, "INR");
        Money b = Money.of(80L, "INR");
        assertThat(a.subtract(b).toMinorUnits()).isEqualTo(120L);
    }

    @Test
    @DisplayName("subtract() throws when result would be negative")
    void subtract_wouldGoNegative_throws() {
        Money a = Money.of(10L, "INR");
        Money b = Money.of(20L, "INR");
        assertThatThrownBy(() -> a.subtract(b)).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("percentage() computes correct basis-point fraction")
    void percentage_correct() {
        Money m = Money.of(100000L, "INR"); // ₹1000
        Money onePercent = m.percentage(100); // 100 bps = 1%
        assertThat(onePercent.toMinorUnits()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("isPositive() and isZero() work correctly")
    void predicates() {
        assertThat(Money.of(1L, "INR").isPositive()).isTrue();
        assertThat(Money.of(0L, "INR").isZero()).isTrue();
        assertThat(Money.of(0L, "INR").isPositive()).isFalse();
    }

    @Test
    @DisplayName("Currency mismatch on arithmetic throws ValidationException")
    void currencyMismatch_throws() {
        Money inr = Money.of(100L, "INR");
        Money usd = Money.of(100L, "USD");
        assertThatThrownBy(() -> inr.add(usd)).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Negative amount throws ValidationException")
    void negativeAmount_throws() {
        assertThatThrownBy(() -> Money.of(-1L, "INR")).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Invalid currency code throws ValidationException")
    void invalidCurrency_throws() {
        assertThatThrownBy(() -> Money.of(100L, "XYZ")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Money.of(100L, "")).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("equals() is value-based")
    void equals_valueBased() {
        Money a = Money.of(100L, "INR");
        Money b = Money.of(100L, "INR");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
