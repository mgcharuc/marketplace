package com.marketplace.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.QuoteHeader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderHeaderTest {

    private static final CurrencyConfig COP = new CurrencyConfig("COP", 0, RoundingMode.HALF_UP);
    private static final CurrencyConfig USD = new CurrencyConfig("USD", 2, RoundingMode.HALF_UP);
    private static final Instant CREATED = Instant.parse("2026-10-16T15:00:00Z");
    private static final Instant CONFIRMED = Instant.parse("2026-10-16T15:05:00Z");

    @Test
    @DisplayName("La clave de la orden incluye el tenant y el tendero de la cotización")
    void keyCarriesTenantAndCustomer() {
        var header = header(PaymentMethod.CASH, cop("22610"), null);

        assertThat(header.key()).isEqualTo(new OrderKey("CO-MARCA-A", "CLI-CONTADO", "o_1"));
    }

    @Test
    @DisplayName("Una orden a crédito lleva el cupo que queda y una de contado no lleva cupo")
    void creditCarriesAvailableAfterAndCashDoesNot() {
        assertThat(header(PaymentMethod.CREDIT, cop("56882"), cop("943118")).availableAfter())
                .isEqualTo(cop("943118"));
        assertThatThrownBy(() -> header(PaymentMethod.CREDIT, cop("56882"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> header(PaymentMethod.CASH, cop("22610"), cop("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El total y el cupo que queda están en la moneda de la cotización")
    void rejectsAmountsInAnotherCurrency() {
        var usd = new Money(BigDecimal.ONE, USD);

        assertThatThrownBy(() -> header(PaymentMethod.CASH, usd, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD");
        assertThatThrownBy(() -> header(PaymentMethod.CREDIT, cop("1"), usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD");
    }

    @Test
    @DisplayName("La secuencia de una orden empieza en 1")
    void rejectsSequenceBelowOne() {
        var header = header(PaymentMethod.CASH, cop("22610"), null);

        assertThat(new Order(header, 1, "{}").sequence()).isOne();
        assertThatThrownBy(() -> new Order(header, 0, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OrderHeader header(PaymentMethod method, Money total, Money availableAfter) {
        var quote =
                new QuoteHeader(
                        "q_1",
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        "DIST-NORTE",
                        1,
                        COP,
                        method,
                        CREATED,
                        CREATED.plusSeconds(900));
        return new OrderHeader("o_1", "CO-A-00000001", quote, total, CONFIRMED, availableAfter);
    }

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }
}
