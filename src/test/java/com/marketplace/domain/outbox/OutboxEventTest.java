package com.marketplace.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final UUID ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-16T15:05:00Z");

    @Test
    @DisplayName(
            "El evento de una orden confirmada nace pendiente, sin intentos y listo para entregarse")
    void orderConfirmedIsPendingNow() {
        var event =
                OutboxEvent.orderConfirmed(ID, "CO-MARCA-A", "o_1", Destination.PUSH, "{}", NOW);

        assertThat(event.eventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(event.aggregateId()).isEqualTo("o_1");
        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.attempts()).isZero();
        assertThat(event.nextAttemptAt()).isEqualTo(NOW);
        assertThat(event.retryWindowStart()).isEqualTo(NOW);
        assertThat(event.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Un evento no puede tener intentos negativos")
    void rejectsNegativeAttempts() {
        assertThatThrownBy(
                        () ->
                                new OutboxEvent(
                                        ID,
                                        "CO-MARCA-A",
                                        "o_1",
                                        OutboxEvent.ORDER_CONFIRMED,
                                        Destination.ERP,
                                        "{}",
                                        OutboxStatus.PENDING,
                                        -1,
                                        NOW,
                                        NOW,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
