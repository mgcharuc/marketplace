package com.marketplace.adapters.persistence.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.application.port.Outbox;
import com.marketplace.domain.outbox.Destination;
import com.marketplace.domain.outbox.OutboxEvent;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JpaOutboxIT {

    @Autowired Outbox outbox;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("El evento se guarda pendiente, sin intentos y con el payload como JSON")
    void appendsPendingEvent() {
        var id = UUID.fromString("00000000-0000-7000-8000-000000000001");
        outbox.append(
                OutboxEvent.orderConfirmed(
                        id,
                        "CO-MARCA-A",
                        "o_1",
                        Destination.ERP,
                        "{\"orderId\": \"o_1\"}",
                        FixedTimeSource.START));

        var row =
                jdbc.queryForMap(
                        "SELECT tenant_id, aggregate_id, event_type, destination, status, attempts,"
                                + " payload ->> 'orderId' AS order_id, next_attempt_at,"
                                + " retry_window_start, created_at, last_error, sent_at"
                                + " FROM outbox_event WHERE id = ?",
                        id);
        var start = Timestamp.from(FixedTimeSource.START);

        assertThat(row)
                .containsEntry("tenant_id", "CO-MARCA-A")
                .containsEntry("aggregate_id", "o_1")
                .containsEntry("event_type", "ORDER_CONFIRMED")
                .containsEntry("destination", "ERP")
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 0)
                .containsEntry("order_id", "o_1")
                .containsEntry("next_attempt_at", start)
                .containsEntry("retry_window_start", start)
                .containsEntry("created_at", start)
                .containsEntry("last_error", null)
                .containsEntry("sent_at", null);
    }
}
