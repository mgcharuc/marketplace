package com.marketplace.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.application.order.ConfirmOrder;
import com.marketplace.application.order.ConfirmOrderCommand;
import com.marketplace.application.quote.QuoteCart;
import com.marketplace.application.quote.QuoteCartCommand;
import com.marketplace.support.FakeErp;
import com.marketplace.support.FakePushProvider;
import com.marketplace.support.FixedJitter;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import com.marketplace.support.OrderFlow;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayIT {

    private static final String ERP_EVENT = "00000000-0000-7000-8000-000000000001";
    private static final String PUSH_EVENT = "00000000-0000-7000-8000-000000000002";
    private static final String ORDER = "o_00000000000000000000000001";
    private static final Instant START = FixedTimeSource.START;

    @Autowired OutboxRelay relay;
    @Autowired QuoteCart quoteCart;
    @Autowired ConfirmOrder confirmOrder;
    @Autowired FakeErp erp;
    @Autowired FakePushProvider push;
    @Autowired FixedTimeSource time;
    @Autowired FixedJitter jitter;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("Con los destinos disponibles cada evento se entrega una vez y queda enviado")
    void deliversToBothDestinations() {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "GASEOSA-350");

        assertThat(relay.runOnce()).isEqualTo(new RelayResult(2, 0, 0, 0));

        assertThat(erp.received()).containsOnlyKeys(ERP_EVENT);
        assertThat(erp.calls()).isOne();
        JsonNode order = mapper.readTree(erp.received().get(ERP_EVENT));
        assertThat(order.get("distributorId").asString()).isEqualTo("DIST-NORTE");
        assertThat(order.at("/order/orderId").asString()).isEqualTo(ORDER);
        assertThat(push.sent())
                .extracting(FakePushProvider.Sent::collapseKey)
                .containsExactly(ORDER);
        JsonNode notification = mapper.readTree(push.sent().get(0).payload());
        assertThat(notification.get("customerId").asString()).isEqualTo("CLI-CONTADO");
        assertThat(notification.get("total").asString()).isEqualTo("2380");
        assertThat(event(ERP_EVENT))
                .containsEntry("status", "SENT")
                .containsEntry("sent_at", at(START));
        assertThat(event(PUSH_EVENT)).containsEntry("status", "SENT");
        assertThat(deliveries())
                .containsExactlyInAnyOrder(ERP_EVENT + " ERP", PUSH_EVENT + " PUSH");
        assertThat(relay.runOnce()).isEqualTo(new RelayResult(0, 0, 0, 0));
    }

    @Test
    @DisplayName("Entrega los eventos de todos los tenants en la misma pasada")
    void deliversEveryTenant() {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "GASEOSA-350");
        confirm("EC-MARCA-A", "CLI-EC-01", "DIST-EC-01", "AGUA-500");

        assertThat(relay.runOnce()).isEqualTo(new RelayResult(4, 0, 0, 0));
    }

    @Test
    @DisplayName("Con el ERP caído la orden se confirma y su evento espera cada vez el doble")
    void erpDownIsRetriedWithBackoff() throws Exception {
        erp.setAvailable(false);
        var flow = new OrderFlow(mvc);
        String quoteId =
                flow.quoteId(
                        flow.quote(
                                "CO-MARCA-A",
                                "CLI-CONTADO",
                                "DIST-NORTE",
                                "CASH",
                                "GASEOSA-350",
                                1));
        flow.confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", quoteId).andExpect(status().isCreated());

        assertThat(relay.runOnce()).isEqualTo(new RelayResult(1, 1, 0, 0));
        assertThat(event(ERP_EVENT))
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 1)
                .containsEntry("next_attempt_at", at(START.plusSeconds(1)))
                .containsEntry("last_error", "ERP no disponible");

        assertThat(relay.runOnce()).isEqualTo(new RelayResult(0, 0, 0, 0));

        time.advance(Duration.ofSeconds(1));
        assertThat(relay.runOnce()).isEqualTo(new RelayResult(0, 1, 0, 0));
        assertThat(event(ERP_EVENT))
                .containsEntry("attempts", 2)
                .containsEntry("next_attempt_at", at(START.plusSeconds(3)));
    }

    @Test
    @DisplayName("Cuando el ERP se recupera el evento se entrega y el ERP lo registra una sola vez")
    void erpRecovers() {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "GASEOSA-350");
        erp.setAvailable(false);
        relay.runOnce();

        erp.setAvailable(true);
        time.advance(Duration.ofSeconds(1));

        assertThat(relay.runOnce()).isEqualTo(new RelayResult(1, 0, 0, 0));
        assertThat(erp.received()).containsOnlyKeys(ERP_EVENT);
        assertThat(erp.calls()).isEqualTo(2);
        assertThat(event(ERP_EVENT)).containsEntry("status", "SENT").containsEntry("attempts", 1);
    }

    @Test
    @DisplayName("Un error inesperado del entregador cuenta como un intento fallido")
    void unexpectedErrorCountsAsAttempt() {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "GASEOSA-350");
        erp.failWith(new IllegalStateException("Respuesta inesperada del ERP"));

        assertThat(relay.runOnce()).isEqualTo(new RelayResult(1, 1, 0, 0));
        assertThat(event(ERP_EVENT))
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 1)
                .containsEntry("last_error", "Respuesta inesperada del ERP");
    }

    @Test
    @DisplayName("El próximo intento suma la espera adicional al azar")
    void retryAddsJitter() {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "GASEOSA-350");
        erp.setAvailable(false);
        jitter.set(Duration.ofMillis(500));

        relay.runOnce();

        assertThat(event(ERP_EVENT))
                .containsEntry("next_attempt_at", at(START.plusSeconds(1).plusMillis(500)));
    }

    @Test
    @DisplayName("El ERP caído sigue pendiente hasta cumplir 72 horas y entonces queda fallido")
    void erpFailsWhenItsWindowCloses(CapturedOutput output) {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "GASEOSA-350");
        erp.setAvailable(false);

        time.set(START.plus(Duration.ofHours(72)).minusSeconds(1));
        relay.runOnce();
        assertThat(event(ERP_EVENT)).containsEntry("status", "PENDING");

        time.set(START.plus(Duration.ofHours(72)));
        assertThat(relay.runOnce()).isEqualTo(new RelayResult(0, 0, 1, 0));
        assertThat(event(ERP_EVENT))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", 2)
                .containsEntry("last_error", "ERP no disponible");
        assertThat(erp.received()).isEmpty();
        assertThat(output.getOut())
                .containsPattern("ERROR .*El evento " + ERP_EVENT + " no llegó al ERP");
    }

    @Test
    @DisplayName(
            "Con el proveedor caído la notificación se descarta sin enviar cuando ya pasó la hora")
    void pushIsDiscardedWhenItsWindowCloses(CapturedOutput output) {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "GASEOSA-350");
        push.setAvailable(false);
        relay.runOnce();

        time.set(START.plus(Duration.ofHours(1)).minusSeconds(1));
        assertThat(relay.runOnce()).isEqualTo(new RelayResult(0, 1, 0, 0));
        assertThat(event(PUSH_EVENT)).containsEntry("status", "PENDING");

        time.set(START.plus(Duration.ofHours(1)).plusSeconds(1));
        assertThat(relay.runOnce()).isEqualTo(new RelayResult(0, 0, 0, 1));
        assertThat(event(PUSH_EVENT)).containsEntry("status", "DISCARDED");
        assertThat(push.sent()).isEmpty();
        assertThat(deliveries()).containsExactly(ERP_EVENT + " ERP");
        assertThat(output.getOut())
                .containsPattern("WARN .*Notificación " + PUSH_EVENT + " descartada");
    }

    @Test
    @DisplayName(
            "Una notificación pendiente desde hace una hora o más se descarta aunque el proveedor responda")
    void expiredPushIsDiscardedWithoutSending() {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "GASEOSA-350");
        time.set(START.plus(Duration.ofHours(1)));

        assertThat(relay.runOnce()).isEqualTo(new RelayResult(1, 0, 0, 1));
        assertThat(event(PUSH_EVENT))
                .containsEntry("status", "DISCARDED")
                .containsEntry("attempts", 0);
        assertThat(push.sent()).isEmpty();
        assertThat(erp.received()).containsOnlyKeys(ERP_EVENT);
    }

    @Test
    @DisplayName("Si un evento falla fuera de la entrega se pospone y los demás se entregan igual")
    void failingEventDoesNotBlockTheOthers(CapturedOutput output) {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "GASEOSA-350");
        erp.failWith(new IllegalStateException("byte nulo \u0000 en el error"));

        assertThat(relay.runOnce()).isEqualTo(new RelayResult(1, 1, 0, 0));
        assertThat(event(ERP_EVENT))
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 1)
                .containsEntry("next_attempt_at", at(START.plusSeconds(1)))
                .containsEntry("last_error", "Error al procesar el evento");
        assertThat(event(PUSH_EVENT)).containsEntry("status", "SENT");
        assertThat(output.getOut()).contains("No se pudo procesar el evento " + ERP_EVENT);
    }

    private void confirm(String tenantId, String customerId, String distributorId, String sku) {
        var quote =
                quoteCart.handle(
                        new QuoteCartCommand(
                                tenantId,
                                customerId,
                                distributorId,
                                "CASH",
                                List.of(new QuoteCartCommand.Item(sku, 1))));
        String quoteId = quote.header().quoteId();
        confirmOrder.handle(
                new ConfirmOrderCommand(tenantId, customerId, quoteId, "key-" + quoteId, quoteId));
    }

    private Map<String, Object> event(String id) {
        return jdbc.queryForMap(
                "SELECT status, attempts, next_attempt_at, last_error, sent_at FROM outbox_event"
                        + " WHERE id = ?::uuid",
                id);
    }

    private List<String> deliveries() {
        return jdbc.queryForList(
                "SELECT event_id::text || ' ' || destination FROM delivered_event", String.class);
    }

    private static Timestamp at(Instant instant) {
        return Timestamp.from(instant);
    }
}
