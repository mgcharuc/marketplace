package com.marketplace.application.port;

import com.marketplace.domain.outbox.OutboxEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository {

    /**
     * Bloquea el siguiente evento pendiente que ya toca entregar, saltando los que otro relay tiene
     * bloqueados. El bloqueo dura hasta que termina la transacción en curso.
     */
    Optional<OutboxEvent> lockNextPending(Instant now);

    void markSent(UUID id, Instant at);

    void markRetry(UUID id, int attempts, Instant next, String error);

    void markFailed(UUID id, int attempts, String error);

    void markDiscarded(UUID id, int attempts, String error);
}
