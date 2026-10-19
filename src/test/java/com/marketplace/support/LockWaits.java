package com.marketplace.support;

import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

public final class LockWaits {

    private static final String WAITING =
            """
            SELECT count(*) FROM pg_stat_activity
            WHERE datname = current_database() AND wait_event_type = 'Lock'
            """;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private LockWaits() {}

    public static void await(JdbcTemplate jdbc, int sessions) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (jdbc.queryForObject(WAITING, Integer.class) < sessions) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException(
                        "Ninguna sesión quedó esperando un bloqueo en " + TIMEOUT);
            }
            Thread.sleep(20);
        }
    }
}
