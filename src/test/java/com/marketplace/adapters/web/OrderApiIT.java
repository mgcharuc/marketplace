package com.marketplace.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.application.port.ConfigCodec;
import com.marketplace.application.port.ConfigRepository;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.domain.config.TenantConfigHistory;
import com.marketplace.support.FailingOutbox;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import com.marketplace.support.OrderFlow;
import com.marketplace.support.Resources;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class OrderApiIT {

    private static final String FIRST_ORDER = "o_00000000000000000000000001";
    private static final List<String> QUOTED_FIELDS =
            List.of(
                    "configVersion",
                    "currency",
                    "paymentMethod",
                    "lines",
                    "discounts",
                    "gifts",
                    "totals");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FixedTimeSource time;
    @Autowired FailingOutbox outbox;
    @Autowired TenantRepository tenants;
    @Autowired ConfigRepository configs;
    @Autowired ConfigCodec codec;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("Confirmar responde 201 con la orden y el mismo desglose de la cotización")
    void confirmsQuoteIntoOrder() throws Exception {
        String quoteId = quote("CLI-CREDITO-ALTO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25);
        JsonNode quote = mapper.readTree(getQuoteBody("CLI-CREDITO-ALTO", quoteId));
        time.advance(Duration.ofMinutes(5));

        String body =
                confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Location", "/v1/orders/" + FIRST_ORDER))
                        .andExpect(header().exists("X-Correlation-Id"))
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.orderId").value(FIRST_ORDER))
                        .andExpect(jsonPath("$.orderNumber", matchesPattern("^CO-A-\\d{8,}$")))
                        .andExpect(jsonPath("$.quoteId").value(quoteId))
                        .andExpect(jsonPath("$.tenantId").value("CO-MARCA-A"))
                        .andExpect(jsonPath("$.customerId").value("CLI-CREDITO-ALTO"))
                        .andExpect(jsonPath("$.distributorId").value("DIST-NORTE"))
                        .andExpect(jsonPath("$.status").value("CONFIRMED"))
                        .andExpect(jsonPath("$.confirmedAt").value("2026-10-16T15:05:00Z"))
                        .andExpect(jsonPath("$.totals.total").value("56882"))
                        .andExpect(jsonPath("$.credit.consumed").value("56882"))
                        .andExpect(jsonPath("$.credit.availableAfter").value("943118"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        JsonNode order = mapper.readTree(body);
        for (String field : QUOTED_FIELDS) {
            assertThat(order.get(field)).as(field).isEqualTo(quote.get(field));
        }
    }

    @Test
    @DisplayName("Consultar la orden devuelve el mismo cuerpo, byte a byte, que al confirmarla")
    void getReturnsTheSameBytes() throws Exception {
        String quoteId = quote("CLI-CONTADO", "DIST-NORTE", "CASH", "GASEOSA-1500", 3);
        byte[] created =
                confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", quoteId)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.credit").value(nullValue()))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        byte[] read =
                getOrder("CO-MARCA-A", "CLI-CONTADO", FIRST_ORDER)
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        assertThat(read).isEqualTo(created);
    }

    @Test
    @DisplayName("A crédito, el cupo baja exactamente el total y queda un movimiento de la orden")
    void creditOrderConsumesAndRecordsMovement() throws Exception {
        String quoteId = quote("CLI-CREDITO-ALTO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25);

        confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId).andExpect(status().isCreated());

        assertThat(available("CLI-CREDITO-ALTO", "DIST-NORTE")).isEqualByComparingTo("943118");
        assertThat(
                        jdbc.queryForMap(
                                "SELECT order_id, tenant_id, customer_id, distributor_id,"
                                        + " amount::text AS amount FROM credit_movement"))
                .containsEntry("order_id", FIRST_ORDER)
                .containsEntry("tenant_id", "CO-MARCA-A")
                .containsEntry("customer_id", "CLI-CREDITO-ALTO")
                .containsEntry("distributor_id", "DIST-NORTE")
                .containsEntry("amount", "56882.0000");
    }

    @Test
    @DisplayName("De contado no se toca el cupo ni se registra movimiento")
    void cashOrderLeavesCreditAlone() throws Exception {
        String quoteId = quote("CLI-CREDITO-BAJO", "DIST-NORTE", "CASH", "GASEOSA-1500", 5);

        confirm("CO-MARCA-A", "CLI-CREDITO-BAJO", "key-1", quoteId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credit").value(nullValue()));

        assertThat(available("CLI-CREDITO-BAJO", "DIST-NORTE")).isEqualByComparingTo("50000");
        assertThat(count("credit_movement")).isZero();
    }

    @Test
    @DisplayName("La orden deja pendientes el aviso al ERP y después la notificación al tendero")
    void enqueuesErpAndPushEvents() throws Exception {
        String quoteId = quote("CLI-CREDITO-ALTO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25);
        String body =
                confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        List<Map<String, Object>> events =
                jdbc.queryForList(
                        "SELECT id::text AS id, destination, status, aggregate_id,"
                                + " next_attempt_at = created_at AS due_now, payload::text AS payload"
                                + " FROM outbox_event ORDER BY next_attempt_at, id");

        assertThat(events).hasSize(2);
        assertThat(events)
                .extracting("destination", "status", "aggregate_id", "due_now")
                .containsExactly(
                        tuple("ERP", "PENDING", FIRST_ORDER, true),
                        tuple("PUSH", "PENDING", FIRST_ORDER, true));
        JsonNode erp = mapper.readTree((String) events.get(0).get("payload"));
        JsonNode push = mapper.readTree((String) events.get(1).get("payload"));
        assertThat(erp.get("eventId").asString()).isEqualTo(events.get(0).get("id"));
        assertThat(erp.get("order")).isEqualTo(mapper.readTree(body));
        assertThat(push.get("eventId").asString()).isEqualTo(events.get(1).get("id"));
        assertThat(push.get("total").asString()).isEqualTo("56882");
        assertThat(push.get("collapseKey").asString()).isEqualTo(FIRST_ORDER);
    }

    @Test
    @DisplayName("Una cotización vencida no se confirma y no deja orden, consumo ni eventos")
    void expiredQuoteIsRejected() throws Exception {
        String quoteId = quote("CLI-CREDITO-ALTO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25);
        time.advance(Duration.ofMinutes(16));

        confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-16T15:15:00Z"));

        assertNothingConfirmed();
        assertThat(available("CLI-CREDITO-ALTO", "DIST-NORTE")).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("En el instante exacto de su vencimiento la cotización ya no se confirma")
    void quoteExpiresExactlyAtExpiresAt() throws Exception {
        String quoteId = quote("CLI-CONTADO", "DIST-NORTE", "CASH", "GASEOSA-350", 1);
        time.set(FixedTimeSource.START.plus(Duration.ofMinutes(15)));

        confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));
    }

    @Test
    @DisplayName("Un segundo antes del vencimiento la cotización todavía se confirma")
    void quoteIsValidUntilTheSecondBeforeExpiresAt() throws Exception {
        String quoteId = quote("CLI-CONTADO", "DIST-NORTE", "CASH", "GASEOSA-350", 1);
        time.set(FixedTimeSource.START.plus(Duration.ofMinutes(15)).minusSeconds(1));

        confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", quoteId).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Otro tendero, otro tenant o una cotización inexistente responden 404")
    void quoteOfAnotherCustomerIsNotFound() throws Exception {
        String quoteId = quote("CLI-CONTADO", "DIST-NORTE", "CASH", "GASEOSA-350", 1);

        confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        confirm("CO-MARCA-B", "CLI-B-01", "key-1", quoteId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", "q_00000000000000000000000099")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));

        assertNothingConfirmed();
    }

    @Test
    @DisplayName("Confirmar otra vez con otra clave responde 409 con la orden existente")
    void secondConfirmationReturnsExistingOrder() throws Exception {
        String quoteId = quote("CLI-CREDITO-ALTO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25);
        confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId).andExpect(status().isCreated());

        confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-2", quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_ALREADY_CONFIRMED"))
                .andExpect(jsonPath("$.orderId").value(FIRST_ORDER));

        assertThat(count("orders")).isOne();
        assertThat(count("credit_movement")).isOne();
        assertThat(count("outbox_event")).isEqualTo(2);
        assertThat(available("CLI-CREDITO-ALTO", "DIST-NORTE")).isEqualByComparingTo("943118");
    }

    @Test
    @DisplayName("Una cotización ya confirmada responde con su orden aunque además haya vencido")
    void confirmedQuoteReportsOrderEvenAfterExpiring() throws Exception {
        String quoteId = quote("CLI-CONTADO", "DIST-NORTE", "CASH", "GASEOSA-350", 1);
        confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", quoteId).andExpect(status().isCreated());
        time.advance(Duration.ofMinutes(16));

        confirm("CO-MARCA-A", "CLI-CONTADO", "key-2", quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_ALREADY_CONFIRMED"))
                .andExpect(jsonPath("$.orderId").value(FIRST_ORDER));
    }

    @Test
    @DisplayName(
            "Sin cupo suficiente al confirmar responde 422 y no deja orden, consumo ni eventos")
    void creditLimitExceededIsRejected() throws Exception {
        String quoteId = quote("CLI-CREDITO-BAJO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25);

        confirm("CO-MARCA-A", "CLI-CREDITO-BAJO", "key-1", quoteId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.available").value("50000"))
                .andExpect(jsonPath("$.required").value("56882"));

        assertNothingConfirmed();
        assertThat(available("CLI-CREDITO-BAJO", "DIST-NORTE")).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("Si la cuenta de crédito desaparece entre cotizar y confirmar responde 422")
    void missingCreditAccountIsRejected() throws Exception {
        String quoteId = quote("CLI-CREDITO-ALTO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25);
        jdbc.update(
                "DELETE FROM credit_account WHERE tenant_id = 'CO-MARCA-A'"
                        + " AND customer_id = 'CLI-CREDITO-ALTO' AND distributor_id = 'DIST-NORTE'");

        confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CREDIT_ACCOUNT_NOT_FOUND"));

        assertNothingConfirmed();
    }

    @Test
    @DisplayName("El consumo afecta solo el cupo con el distribuidor de la cotización")
    void consumesOnlyTheQuotedDistributor() throws Exception {
        String quoteId = quote("CLI-CREDITO-BAJO", "DIST-SUR", "CREDIT", "GASEOSA-350", 25);

        confirm("CO-MARCA-A", "CLI-CREDITO-BAJO", "key-1", quoteId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credit.availableAfter").value("43118"));

        assertThat(available("CLI-CREDITO-BAJO", "DIST-SUR")).isEqualByComparingTo("43118");
        assertThat(available("CLI-CREDITO-BAJO", "DIST-NORTE")).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("La orden respeta lo cotizado aunque después se publique otra versión comercial")
    void orderKeepsQuotedTotalAfterNewVersion() throws Exception {
        String quoteId =
                quoteId(
                        quoteRequest("CLI-CREDITO-ALTO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25)
                                .andExpect(jsonPath("$.configVersion").value(1))
                                .andExpect(jsonPath("$.totals.total").value("56882")));
        var second = codec.decode(Resources.read("seed/co-marca-a.v2.json"));
        var history =
                new TenantConfigHistory(
                        tenants.find("CO-MARCA-A").orElseThrow(),
                        configs.current("CO-MARCA-A").number());
        configs.publish(history.publish(second), time.now());

        confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.configVersion").value(1))
                .andExpect(jsonPath("$.totals.total").value("56882"))
                .andExpect(jsonPath("$.credit.availableAfter").value("943118"));

        quoteRequest("CLI-CREDITO-ALTO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25)
                .andExpect(jsonPath("$.configVersion").value(2))
                .andExpect(jsonPath("$.totals.total").value("74375"));
    }

    @Test
    @DisplayName("Si falla la escritura en el outbox responde 500 y no queda nada de la orden")
    void failureBeforeCommitLeavesNothing() throws Exception {
        String quoteId = quote("CLI-CREDITO-ALTO", "DIST-NORTE", "CREDIT", "GASEOSA-350", 25);
        outbox.arm();

        String body =
                confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("Exception", "outbox");
        assertNothingConfirmed();
        assertThat(available("CLI-CREDITO-ALTO", "DIST-NORTE")).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("En USD el cupo consumido y el restante salen con dos decimales")
    void usdOrderReportsCreditInCurrencyScale() throws Exception {
        String quoteId =
                quoteId(
                        quoteRequest(
                                "EC-MARCA-A", "CLI-EC-01", "DIST-EC-01", "CREDIT", "AGUA-500", 1));

        confirm("EC-MARCA-A", "CLI-EC-01", "key-1", quoteId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber", matchesPattern("^EC-A-\\d{8,}$")))
                .andExpect(jsonPath("$.credit.consumed").value("0.40"))
                .andExpect(jsonPath("$.credit.availableAfter").value("499.60"));
    }

    @Test
    @DisplayName("Otro tendero, otro tenant o un id mal formado no encuentran la orden")
    void orderIsVisibleOnlyToItsCustomer() throws Exception {
        String quoteId = quote("CLI-CONTADO", "DIST-NORTE", "CASH", "GASEOSA-350", 1);
        confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", quoteId).andExpect(status().isCreated());

        getOrder("CO-MARCA-A", "CLI-CREDITO-ALTO", FIRST_ORDER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        getOrder("CO-MARCA-B", "CLI-B-01", FIRST_ORDER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        getOrder("CO-MARCA-A", "CLI-CONTADO", "o_123")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        getOrder("CO-MARCA-A", "CLI-CONTADO", "o_00000000000000000000000099")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    private void assertNothingConfirmed() {
        assertThat(count("orders")).isZero();
        assertThat(count("credit_movement")).isZero();
        assertThat(count("outbox_event")).isZero();
        assertThat(count("idempotency_record")).isZero();
    }

    private String quote(
            String customerId, String distributorId, String method, String sku, int quantity)
            throws Exception {
        return quoteId(quoteRequest(customerId, distributorId, method, sku, quantity));
    }

    private ResultActions quoteRequest(
            String customerId, String distributorId, String method, String sku, int quantity)
            throws Exception {
        return quoteRequest("CO-MARCA-A", customerId, distributorId, method, sku, quantity);
    }

    private ResultActions quoteRequest(
            String tenantId,
            String customerId,
            String distributorId,
            String method,
            String sku,
            int quantity)
            throws Exception {
        return flow().quote(tenantId, customerId, distributorId, method, sku, quantity);
    }

    private String quoteId(ResultActions quote) throws Exception {
        return flow().quoteId(quote);
    }

    private String getQuoteBody(String customerId, String quoteId) throws Exception {
        return mvc.perform(
                        get("/v1/quotes/{quoteId}", quoteId)
                                .header("X-Tenant-Id", "CO-MARCA-A")
                                .header("X-Customer-Id", customerId))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private ResultActions confirm(String tenantId, String customerId, String key, String quoteId)
            throws Exception {
        return flow().confirm(tenantId, customerId, key, quoteId);
    }

    private OrderFlow flow() {
        return new OrderFlow(mvc);
    }

    private ResultActions getOrder(String tenantId, String customerId, String orderId)
            throws Exception {
        return mvc.perform(
                get("/v1/orders/{orderId}", orderId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Customer-Id", customerId));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private BigDecimal available(String customerId, String distributorId) {
        return jdbc.queryForObject(
                "SELECT available FROM credit_account"
                        + " WHERE tenant_id = 'CO-MARCA-A' AND customer_id = ? AND distributor_id = ?",
                BigDecimal.class,
                customerId,
                distributorId);
    }
}
