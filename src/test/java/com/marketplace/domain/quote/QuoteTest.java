package com.marketplace.domain.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuoteTest {

    private static final CurrencyConfig COP = new CurrencyConfig("COP", 0, RoundingMode.HALF_UP);
    private static final CurrencyConfig USD = new CurrencyConfig("USD", 2, RoundingMode.HALF_UP);
    private static final Instant CREATED = Instant.parse("2026-10-16T15:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-10-16T15:15:00Z");

    @Test
    @DisplayName("La clave de la cotización la ata a su tenant y a su tendero")
    void keyCarriesTenantAndCustomer() {
        assertThat(header(CREATED, EXPIRES).key())
                .isEqualTo(new QuoteKey("CO-MARCA-A", "CLI-CONTADO", "q_1"));
    }

    @Test
    @DisplayName("Una cotización no puede vencer en el mismo instante en que se crea ni antes")
    void rejectsExpirationNotAfterCreation() {
        assertThatThrownBy(() -> header(CREATED, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> header(EXPIRES, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El total de la cotización está en su moneda")
    void rejectsTotalInAnotherCurrency() {
        var header = header(CREATED, EXPIRES);

        assertThatThrownBy(() -> new Quote(header, new Money(BigDecimal.ONE, USD), "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD");
    }

    private static QuoteHeader header(Instant createdAt, Instant expiresAt) {
        return new QuoteHeader(
                "q_1",
                "CO-MARCA-A",
                "CLI-CONTADO",
                "DIST-NORTE",
                1,
                COP,
                PaymentMethod.CASH,
                createdAt,
                expiresAt);
    }
}
