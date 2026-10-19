package com.marketplace.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final Instant START = Instant.parse("2026-10-16T15:00:00Z");

    @Test
    @DisplayName("La espera empieza en un segundo y se duplica con cada fallo")
    void backoffDoubles() {
        assertThat(RetryPolicy.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(RetryPolicy.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(RetryPolicy.backoff(5)).isEqualTo(Duration.ofSeconds(16));
        assertThat(RetryPolicy.backoff(10)).isEqualTo(Duration.ofSeconds(512));
    }

    @Test
    @DisplayName("La espera no pasa de 15 minutos aunque los fallos sigan")
    void backoffIsCapped() {
        assertThat(RetryPolicy.backoff(11)).isEqualTo(Duration.ofMinutes(15));
        assertThat(RetryPolicy.backoff(Integer.MAX_VALUE)).isEqualTo(Duration.ofMinutes(15));
        assertThatThrownBy(() -> RetryPolicy.backoff(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El ERP se reintenta 72 horas y la notificación una hora")
    void windowsDependOnDestination() {
        var erp = event(Destination.ERP);
        var push = event(Destination.PUSH);

        assertThat(RetryPolicy.windowClosed(erp, START.plus(Duration.ofHours(72)).minusSeconds(1)))
                .isFalse();
        assertThat(RetryPolicy.windowClosed(erp, START.plus(Duration.ofHours(72)))).isTrue();
        assertThat(RetryPolicy.windowClosed(push, START.plus(Duration.ofHours(1)).minusSeconds(1)))
                .isFalse();
        assertThat(RetryPolicy.windowClosed(push, START.plus(Duration.ofHours(1)))).isTrue();
    }

    private static OutboxEvent event(Destination destination) {
        return OutboxEvent.orderConfirmed(
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                "CO-MARCA-A",
                "o_1",
                destination,
                "{}",
                START);
    }
}
