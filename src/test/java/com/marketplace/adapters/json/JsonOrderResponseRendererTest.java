package com.marketplace.adapters.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.order.OrderHeader;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.QuoteHeader;
import com.marketplace.support.Resources;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JsonOrderResponseRendererTest {

    private static final CurrencyConfig COP = new CurrencyConfig("COP", 0, RoundingMode.HALF_UP);
    private static final CurrencyConfig USD = new CurrencyConfig("USD", 2, RoundingMode.HALF_UP);
    private static final Instant CREATED = Instant.parse("2026-10-16T15:00:00Z");
    private static final Instant CONFIRMED = Instant.parse("2026-10-16T15:05:00Z");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsonOrderResponseRenderer renderer = new JsonOrderResponseRenderer(mapper);

    @Test
    @DisplayName("La orden lleva sus datos y después el desglose copiado del snapshot")
    void rendersOrderWithQuotedBreakdown() {
        String snapshot = Resources.read("api/quote-combo-fiesta.json");
        var header = header(quote(PaymentMethod.CASH, COP), money("22610", COP), null);

        JsonNode order = mapper.readTree(renderer.render(header, snapshot));
        JsonNode quote = mapper.readTree(snapshot);

        assertThat(order.propertyNames())
                .containsExactly(
                        "orderId",
                        "orderNumber",
                        "quoteId",
                        "tenantId",
                        "customerId",
                        "distributorId",
                        "status",
                        "confirmedAt",
                        "configVersion",
                        "currency",
                        "paymentMethod",
                        "lines",
                        "discounts",
                        "gifts",
                        "totals",
                        "credit");
        assertThat(order.get("orderId").asString()).isEqualTo("o_00000000000000000000000001");
        assertThat(order.get("orderNumber").asString()).isEqualTo("CO-A-00000001");
        assertThat(order.get("quoteId").asString()).isEqualTo("q_00000000000000000000000001");
        assertThat(order.get("tenantId").asString()).isEqualTo("CO-MARCA-A");
        assertThat(order.get("customerId").asString()).isEqualTo("CLI-CONTADO");
        assertThat(order.get("distributorId").asString()).isEqualTo("DIST-NORTE");
        assertThat(order.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(order.get("confirmedAt").asString()).isEqualTo("2026-10-16T15:05:00Z");
        for (String field :
                List.of(
                        "configVersion",
                        "currency",
                        "paymentMethod",
                        "lines",
                        "discounts",
                        "gifts",
                        "totals")) {
            assertThat(order.get(field)).as(field).isEqualTo(quote.get(field));
        }
        assertThat(order.get("credit").isNull()).isTrue();
    }

    @Test
    @DisplayName("A crédito la orden informa el cupo consumido y el que queda")
    void rendersConsumedCredit() {
        String snapshot =
                Resources.read("api/quote-combo-fiesta.json")
                        .replace("\"paymentMethod\": \"CASH\"", "\"paymentMethod\": \"CREDIT\"");
        var header =
                header(quote(PaymentMethod.CREDIT, COP), money("22610", COP), money("977390", COP));

        JsonNode credit = mapper.readTree(renderer.render(header, snapshot)).get("credit");

        assertThat(credit.propertyNames()).containsExactly("consumed", "availableAfter");
        assertThat(credit.get("consumed").asString()).isEqualTo("22610");
        assertThat(credit.get("availableAfter").asString()).isEqualTo("977390");
    }

    @Test
    @DisplayName("Los montos se copian del snapshot y se informan con la escala de la moneda")
    void keepsAmountsAsQuoted() {
        String snapshot =
                """
                {"configVersion": 1, "currency": {"code": "USD", "scale": 2, "roundingMode": "HALF_UP"},
                 "paymentMethod": "CREDIT", "lines": [], "discounts": [], "gifts": [],
                 "totals": {"total": "2.30"}, "credit": null}
                """;
        var header =
                header(quote(PaymentMethod.CREDIT, USD), money("2.3", USD), money("497.7", USD));

        String order = renderer.render(header, snapshot);

        assertThat(order)
                .contains("\"totals\":{\"total\":\"2.30\"}")
                .contains("\"credit\":{\"consumed\":\"2.30\",\"availableAfter\":\"497.70\"}");
    }

    @Test
    @DisplayName("Un snapshot sin alguna parte del desglose no se convierte en orden")
    void rejectsIncompleteSnapshot() {
        var header = header(quote(PaymentMethod.CASH, COP), money("22610", COP), null);

        assertThatThrownBy(() -> renderer.render(header, "{\"configVersion\": 1}"))
                .isInstanceOf(JacksonException.class)
                .hasMessageContaining("currency");
    }

    private static OrderHeader header(QuoteHeader quote, Money total, Money availableAfter) {
        return new OrderHeader(
                "o_00000000000000000000000001",
                "CO-A-00000001",
                quote,
                total,
                CONFIRMED,
                availableAfter);
    }

    private static QuoteHeader quote(PaymentMethod method, CurrencyConfig currency) {
        return new QuoteHeader(
                "q_00000000000000000000000001",
                "CO-MARCA-A",
                "CLI-CONTADO",
                "DIST-NORTE",
                1,
                currency,
                method,
                CREATED,
                CREATED.plusSeconds(900));
    }

    private static Money money(String amount, CurrencyConfig currency) {
        return new Money(new BigDecimal(amount), currency);
    }
}
