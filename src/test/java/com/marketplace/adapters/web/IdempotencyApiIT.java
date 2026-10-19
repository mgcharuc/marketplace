package com.marketplace.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import com.marketplace.support.OrderFlow;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class IdempotencyApiIT {

    private static final String FIRST_ORDER = "o_00000000000000000000000001";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FixedTimeSource time;

    private OrderFlow flow;

    @BeforeEach
    void createFlow() {
        flow = new OrderFlow(mvc);
    }

    @Test
    @DisplayName(
            "Reintentar con la misma clave y el mismo cuerpo repite el 201 original, byte a byte")
    void replaysTheOriginalResponse() throws Exception {
        String quoteId = creditQuote();
        MockHttpServletResponse first =
                flow.confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                        .andExpect(status().isCreated())
                        .andExpect(header().doesNotExist("Idempotent-Replayed"))
                        .andReturn()
                        .getResponse();

        MockHttpServletResponse retry =
                flow.confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Location", "/v1/orders/" + FIRST_ORDER))
                        .andExpect(header().string("Idempotent-Replayed", "true"))
                        .andReturn()
                        .getResponse();

        assertThat(retry.getContentAsByteArray()).isEqualTo(first.getContentAsByteArray());
        assertSingleOrder();
    }

    @Test
    @DisplayName(
            "El cuerpo se compara normalizado, así que los espacios no cuentan como otro cuerpo")
    void comparesNormalizedBodies() throws Exception {
        String quoteId = creditQuote();
        flow.confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                .andExpect(status().isCreated());

        flow.confirmBody(
                        "CO-MARCA-A",
                        "CLI-CREDITO-ALTO",
                        "key-1",
                        "{ \"quoteId\" :  \" " + quoteId + " \" }")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertSingleOrder();
    }

    @Test
    @DisplayName("La misma clave con otro cuerpo responde 422 y no crea otra orden")
    void sameKeyWithOtherBodyIsRejected() throws Exception {
        String first = creditQuote();
        String second = creditQuote();
        flow.confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", first)
                .andExpect(status().isCreated());

        flow.confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", second)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertSingleOrder();
    }

    @Test
    @DisplayName(
            "Veinte reintentos simultáneos con la misma clave crean una orden y reciben el mismo 201")
    void concurrentRetriesCreateOneOrder() throws Exception {
        String quoteId = creditQuote();
        var start = new CountDownLatch(1);
        var responses = new ArrayList<Future<MockHttpServletResponse>>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                responses.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return flow.confirm(
                                                    "CO-MARCA-A",
                                                    "CLI-CREDITO-ALTO",
                                                    "key-1",
                                                    quoteId)
                                            .andReturn()
                                            .getResponse();
                                }));
            }
            start.countDown();

            var bodies = new ArrayList<String>();
            var locations = new ArrayList<String>();
            int replayed = 0;
            for (Future<MockHttpServletResponse> future : responses) {
                MockHttpServletResponse response = future.get(30, TimeUnit.SECONDS);
                assertThat(response.getStatus()).isEqualTo(201);
                bodies.add(HexFormat.of().formatHex(response.getContentAsByteArray()));
                locations.add(response.getHeader("Location"));
                if ("true".equals(response.getHeader("Idempotent-Replayed"))) {
                    replayed++;
                }
            }
            assertThat(bodies).containsOnly(bodies.get(0));
            assertThat(locations).containsOnly("/v1/orders/" + FIRST_ORDER);
            assertThat(replayed).isEqualTo(19);
        }
        assertSingleOrder();
    }

    @Test
    @DisplayName("La misma clave en dos tenants crea dos órdenes independientes")
    void keysAreScopedToTenant() throws Exception {
        String inA = cashQuote("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE");
        String inB = cashQuote("CO-MARCA-B", "CLI-B-01", "DIST-B-01");

        flow.confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", inA).andExpect(status().isCreated());
        flow.confirm("CO-MARCA-B", "CLI-B-01", "key-1", inB)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.tenantId").value("CO-MARCA-B"));

        assertThat(count("orders")).isEqualTo(2);
    }

    @Test
    @DisplayName("La misma clave en dos tenderos del mismo tenant crea dos órdenes independientes")
    void keysAreScopedToCustomer() throws Exception {
        String cash = cashQuote("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE");
        String credit = creditQuote();

        flow.confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", cash).andExpect(status().isCreated());
        flow.confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", credit)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.customerId").value("CLI-CREDITO-ALTO"));

        assertThat(count("orders")).isEqualTo(2);
    }

    @Test
    @DisplayName("Un rechazo no guarda la clave, y al reintentar con cupo liberado se confirma")
    void rejectionIsEvaluatedAgainOnRetry() throws Exception {
        String quoteId =
                flow.quoteId(
                        flow.quote(
                                "CO-MARCA-A",
                                "CLI-CREDITO-BAJO",
                                "DIST-NORTE",
                                "CREDIT",
                                "GASEOSA-350",
                                25));
        flow.confirm("CO-MARCA-A", "CLI-CREDITO-BAJO", "key-1", quoteId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_EXCEEDED"));
        assertThat(count("idempotency_record")).isZero();

        jdbc.update(
                "UPDATE credit_account SET available = 100000 WHERE tenant_id = 'CO-MARCA-A'"
                        + " AND customer_id = 'CLI-CREDITO-BAJO' AND distributor_id = 'DIST-NORTE'");

        flow.confirm("CO-MARCA-A", "CLI-CREDITO-BAJO", "key-1", quoteId)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.credit.availableAfter").value("43118"));
    }

    @Test
    @DisplayName(
            "Con la clave vencida, reintentar la misma cotización responde 409 con la orden existente")
    void expiredKeyIsNotReplayed() throws Exception {
        String quoteId = creditQuote();
        flow.confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                .andExpect(status().isCreated());
        time.set(FixedTimeSource.START.plus(Duration.ofHours(24)));

        flow.confirm("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1", quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_ALREADY_CONFIRMED"))
                .andExpect(jsonPath("$.orderId").value(FIRST_ORDER));
        assertSingleOrder();
    }

    private void assertSingleOrder() {
        assertThat(count("orders")).isOne();
        assertThat(count("credit_movement")).isOne();
        assertThat(count("outbox_event")).isEqualTo(2);
        assertThat(count("idempotency_record")).isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT available FROM credit_account WHERE tenant_id = 'CO-MARCA-A'"
                                        + " AND customer_id = 'CLI-CREDITO-ALTO'"
                                        + " AND distributor_id = 'DIST-NORTE'",
                                BigDecimal.class))
                .isEqualByComparingTo("943118");
    }

    private String creditQuote() throws Exception {
        return flow.quoteId(
                flow.quote(
                        "CO-MARCA-A",
                        "CLI-CREDITO-ALTO",
                        "DIST-NORTE",
                        "CREDIT",
                        "GASEOSA-350",
                        25));
    }

    private String cashQuote(String tenantId, String customerId, String distributorId)
            throws Exception {
        return flow.quoteId(
                flow.quote(tenantId, customerId, distributorId, "CASH", "GASEOSA-350", 1));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
