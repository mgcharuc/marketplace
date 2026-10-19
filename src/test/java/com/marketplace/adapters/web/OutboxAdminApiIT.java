package com.marketplace.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.application.order.ConfirmOrder;
import com.marketplace.application.order.ConfirmOrderCommand;
import com.marketplace.application.outbox.OutboxRelay;
import com.marketplace.application.outbox.RelayResult;
import com.marketplace.application.quote.QuoteCart;
import com.marketplace.application.quote.QuoteCartCommand;
import com.marketplace.support.FakeErp;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class OutboxAdminApiIT {

    private static final String ERP_EVENT = "00000000-0000-7000-8000-000000000001";
    private static final String PUSH_EVENT = "00000000-0000-7000-8000-000000000002";
    private static final Instant ERP_WINDOW_END = FixedTimeSource.START.plus(Duration.ofHours(72));

    @Autowired MockMvc mvc;
    @Autowired OutboxRelay relay;
    @Autowired QuoteCart quoteCart;
    @Autowired ConfirmOrder confirmOrder;
    @Autowired FakeErp erp;
    @Autowired FixedTimeSource time;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName(
            "Reencolar un evento fallido responde 202 y el relay lo entrega cuando el ERP vuelve")
    void requeuedFailedEventIsDelivered() throws Exception {
        failErpEvent();
        erp.setAvailable(true);
        time.advance(Duration.ofMinutes(30));
        Instant now = time.now();

        retry(ERP_EVENT).andExpect(status().isAccepted()).andExpect(content().string(""));

        assertThat(event(ERP_EVENT))
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 0)
                .containsEntry("next_attempt_at", Timestamp.from(now))
                .containsEntry("retry_window_start", Timestamp.from(now));
        assertThat(relay.runOnce()).isEqualTo(new RelayResult(1, 0, 0, 0));
        assertThat(erp.received()).containsOnlyKeys(ERP_EVENT);
        assertThat(event(ERP_EVENT)).containsEntry("status", "SENT");
    }

    @Test
    @DisplayName("Un evento reencolado tiene otra vez 72 horas antes de quedar fallido")
    void requeuedEventGetsANewWindow() throws Exception {
        failErpEvent();
        retry(ERP_EVENT).andExpect(status().isAccepted());

        time.set(ERP_WINDOW_END.plus(Duration.ofHours(72)).minusSeconds(1));
        assertThat(relay.runOnce()).isEqualTo(new RelayResult(0, 1, 0, 0));
        assertThat(event(ERP_EVENT)).containsEntry("status", "PENDING");

        time.set(ERP_WINDOW_END.plus(Duration.ofHours(72)));
        assertThat(relay.runOnce()).isEqualTo(new RelayResult(0, 0, 1, 0));
        assertThat(event(ERP_EVENT)).containsEntry("status", "FAILED");
    }

    @Test
    @DisplayName("Reencolar una notificación descartada responde 409 con su estado")
    void discardedEventCannotBeRequeued() throws Exception {
        confirm();
        time.set(FixedTimeSource.START.plus(Duration.ofHours(1)));
        relay.runOnce();

        retry(PUSH_EVENT)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FAILED"))
                .andExpect(jsonPath("$.eventStatus").value("DISCARDED"))
                .andExpect(jsonPath("$.status").value(409));
        assertThat(event(PUSH_EVENT)).containsEntry("status", "DISCARDED");
    }

    @Test
    @DisplayName("Reencolar un evento todavía pendiente responde 409 y no lo cambia")
    void pendingEventCannotBeRequeued() throws Exception {
        confirm();
        erp.setAvailable(false);
        relay.runOnce();
        Map<String, Object> before = event(ERP_EVENT);

        retry(ERP_EVENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FAILED"))
                .andExpect(jsonPath("$.eventStatus").value("PENDING"));
        assertThat(event(ERP_EVENT)).isEqualTo(before);
    }

    @Test
    @DisplayName("Reencolar un evento que no existe o con un id mal formado responde 404")
    void unknownEventIsNotFound() throws Exception {
        retry("00000000-0000-7000-8000-000000000099")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        retry("no-es-un-uuid")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        retry("1-1-1-1-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    private void failErpEvent() {
        confirm();
        erp.setAvailable(false);
        time.set(ERP_WINDOW_END);
        assertThat(relay.runOnce()).isEqualTo(new RelayResult(0, 0, 1, 1));
    }

    private void confirm() {
        var quote =
                quoteCart.handle(
                        new QuoteCartCommand(
                                "CO-MARCA-A",
                                "CLI-CONTADO",
                                "DIST-NORTE",
                                "CASH",
                                List.of(new QuoteCartCommand.Item("GASEOSA-350", 1))));
        String quoteId = quote.header().quoteId();
        confirmOrder.handle(
                new ConfirmOrderCommand("CO-MARCA-A", "CLI-CONTADO", quoteId, "key-1", quoteId));
    }

    private ResultActions retry(String eventId) throws Exception {
        return mvc.perform(post("/v1/admin/outbox/{eventId}/retry", eventId));
    }

    private Map<String, Object> event(String id) {
        return jdbc.queryForMap(
                "SELECT status, attempts, next_attempt_at, retry_window_start FROM outbox_event"
                        + " WHERE id = ?::uuid",
                id);
    }
}
