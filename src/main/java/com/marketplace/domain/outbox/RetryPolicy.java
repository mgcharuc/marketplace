package com.marketplace.domain.outbox;

import java.time.Duration;
import java.time.Instant;

/** Define la espera entre intentos de entrega y hasta cuándo se reintenta cada destino. */
public final class RetryPolicy {

    private static final Duration BASE = Duration.ofSeconds(1);
    private static final Duration CAP = Duration.ofMinutes(15);
    private static final int MAX_EXPONENT = 20;
    private static final Duration ERP_WINDOW = Duration.ofHours(72);
    private static final Duration PUSH_WINDOW = Duration.ofHours(1);

    private RetryPolicy() {}

    public static Duration backoff(int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("Intentos menores que 1: " + attempts);
        }
        Duration wait = BASE.multipliedBy(1L << Math.min(attempts - 1, MAX_EXPONENT));
        return wait.compareTo(CAP) > 0 ? CAP : wait;
    }

    public static boolean windowClosed(OutboxEvent event, Instant now) {
        Duration window = event.destination() == Destination.ERP ? ERP_WINDOW : PUSH_WINDOW;
        return !now.isBefore(event.retryWindowStart().plus(window));
    }
}
