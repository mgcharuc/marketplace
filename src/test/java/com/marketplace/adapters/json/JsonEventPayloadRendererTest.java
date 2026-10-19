package com.marketplace.adapters.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.order.OrderHeader;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.QuoteHeader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JsonEventPayloadRendererTest {

    private static final CurrencyConfig COP = new CurrencyConfig("COP", 0, RoundingMode.HALF_UP);
    private static final Instant CREATED = Instant.parse("2026-10-16T15:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final String ORDER_BODY =
            "{\"orderId\":\"o_00000000000000000000000001\",\"totals\":{\"total\":\"56882\"}}";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsonEventPayloadRenderer renderer = new JsonEventPayloadRenderer(mapper);

    @Test
    @DisplayName("El mensaje al ERP identifica el evento y la orden e incluye la orden completa")
    void rendersErpPayload() {
        JsonNode payload = mapper.readTree(renderer.erp(EVENT_ID, header(), ORDER_BODY));

        assertThat(payload.propertyNames())
                .containsExactly(
                        "schemaVersion",
                        "eventId",
                        "eventType",
                        "tenantId",
                        "distributorId",
                        "orderId",
                        "orderNumber",
                        "order");
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("eventId").asString())
                .isEqualTo("00000000-0000-7000-8000-000000000001");
        assertThat(payload.get("eventType").asString()).isEqualTo("ORDER_CONFIRMED");
        assertThat(payload.get("tenantId").asString()).isEqualTo("CO-MARCA-A");
        assertThat(payload.get("distributorId").asString()).isEqualTo("DIST-NORTE");
        assertThat(payload.get("orderId").asString()).isEqualTo("o_00000000000000000000000001");
        assertThat(payload.get("orderNumber").asString()).isEqualTo("CO-A-00000001");
        assertThat(payload.get("order")).isEqualTo(mapper.readTree(ORDER_BODY));
    }

    @Test
    @DisplayName(
            "La notificación al tendero lleva el número y el total de la orden y se agrupa por orden")
    void rendersPushPayload() {
        JsonNode payload = mapper.readTree(renderer.push(EVENT_ID, header()));

        assertThat(payload.propertyNames())
                .containsExactly(
                        "schemaVersion",
                        "eventId",
                        "eventType",
                        "tenantId",
                        "customerId",
                        "orderId",
                        "orderNumber",
                        "total",
                        "currency",
                        "collapseKey");
        assertThat(payload.get("customerId").asString()).isEqualTo("CLI-CREDITO-ALTO");
        assertThat(payload.get("total").asString()).isEqualTo("56882");
        assertThat(payload.get("currency").asString()).isEqualTo("COP");
        assertThat(payload.get("collapseKey").asString()).isEqualTo("o_00000000000000000000000001");
    }

    private static OrderHeader header() {
        var quote =
                new QuoteHeader(
                        "q_00000000000000000000000001",
                        "CO-MARCA-A",
                        "CLI-CREDITO-ALTO",
                        "DIST-NORTE",
                        1,
                        COP,
                        PaymentMethod.CREDIT,
                        CREATED,
                        CREATED.plusSeconds(900));
        return new OrderHeader(
                "o_00000000000000000000000001",
                "CO-A-00000001",
                quote,
                new Money(new BigDecimal("56882"), COP),
                CREATED.plusSeconds(300),
                new Money(new BigDecimal("943118"), COP));
    }
}
