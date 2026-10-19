package com.marketplace.domain.credit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreditMovementTest {

    @Test
    @DisplayName("Una orden no registra un consumo de cupo en cero")
    void rejectsZeroAmount() {
        var key = new CreditKey("CO-MARCA-A", "CLI-CREDITO-ALTO", "DIST-NORTE");
        var zero = new Money(BigDecimal.ZERO, new CurrencyConfig("COP", 0, RoundingMode.HALF_UP));

        assertThatThrownBy(
                        () ->
                                new CreditMovement(
                                        "o_1", key, zero, Instant.parse("2026-10-16T15:05:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
