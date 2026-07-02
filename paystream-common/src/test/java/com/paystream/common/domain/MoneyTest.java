package com.paystream.common.domain;

import com.paystream.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void factory_createsCorrectMinorUnits() {
        Money money = Money.of(100_000L, "INR");
        assertThat(money.toMinorUnits()).isEqualTo(100_000L);
        assertThat(money.getCurrencyCode()).isEqualTo("INR");
    }

    @Test
    void add_returnsSumWithSameCurrency() {
        Money a = Money.of(50_000L, "INR");
        Money b = Money.of(30_000L, "INR");
        assertThat(a.add(b).toMinorUnits()).isEqualTo(80_000L);
    }

    @Test
    void subtract_returnsDifferenceWithSameCurrency() {
        Money a = Money.of(100_000L, "INR");
        Money b = Money.of(40_000L, "INR");
        assertThat(a.subtract(b).toMinorUnits()).isEqualTo(60_000L);
    }

    @Test
    void add_throwsOnCurrencyMismatch() {
        Money inr = Money.of(100L, "INR");
        Money usd = Money.of(100L, "USD");
        assertThatThrownBy(() -> inr.add(usd))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void percentage_computesCorrectly() {
        Money amount = Money.of(100_000L, "INR");
        Money onePercent = amount.percentage(1);
        assertThat(onePercent.toMinorUnits()).isEqualTo(1_000L);
    }

    @Test
    void percentage_zero_returnsZero() {
        Money amount = Money.of(100_000L, "INR");
        assertThat(amount.percentage(0).toMinorUnits()).isZero();
    }

    @Test
    void percentage_hundred_returnsSameAmount() {
        Money amount = Money.of(100_000L, "INR");
        assertThat(amount.percentage(100).toMinorUnits()).isEqualTo(100_000L);
    }

    @Test
    void percentage_outOfRange_throwsValidationException() {
        Money amount = Money.of(100_000L, "INR");
        assertThatThrownBy(() -> amount.percentage(101))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> amount.percentage(-1))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void isPositive_isZero_isNegative_correctForEdgeCases() {
        assertThat(Money.of(1L, "INR").isPositive()).isTrue();
        assertThat(Money.of(0L, "INR").isZero()).isTrue();
        assertThat(Money.of(-1L, "INR").isNegative()).isTrue();
    }

    @Test
    void invalidCurrencyCode_throwsValidationException() {
        assertThatThrownBy(() -> Money.of(100L, "XYZ"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid ISO-4217 currency code");
    }

    @Test
    void nullCurrencyCode_throwsValidationException() {
        assertThatThrownBy(() -> Money.of(100L, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void immutability_operationsReturnNewInstance() {
        Money original = Money.of(100_000L, "INR");
        Money added    = original.add(Money.of(1L, "INR"));
        assertThat(original.toMinorUnits()).isEqualTo(100_000L); // unchanged
        assertThat(added.toMinorUnits()).isEqualTo(100_001L);
    }

    @Test
    void equals_andHashCode_basedOnValueNotIdentity() {
        Money a = Money.of(500L, "INR");
        Money b = Money.of(500L, "INR");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void isGreaterThan_comparesCorrectly() {
        Money large = Money.of(200L, "INR");
        Money small = Money.of(100L, "INR");
        assertThat(large.isGreaterThan(small)).isTrue();
        assertThat(small.isGreaterThan(large)).isFalse();
    }
}
