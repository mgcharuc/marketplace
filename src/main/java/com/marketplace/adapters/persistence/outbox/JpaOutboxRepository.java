package com.marketplace.adapters.persistence.outbox;

import com.marketplace.application.port.OutboxRepository;
import com.marketplace.domain.outbox.OutboxEvent;
import com.marketplace.domain.outbox.OutboxStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaOutboxRepository implements OutboxRepository {

    private final OutboxEventEntityRepository events;

    JpaOutboxRepository(OutboxEventEntityRepository events) {
        this.events = events;
    }

    // El bloqueo y los límites de espera duran lo que la transacción del evento.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<OutboxEvent> lockNextPending(Instant now) {
        events.limitWaitsInTransaction();
        return events.lockNextPending(now).map(OutboxEventEntity::toDomain);
    }

    @Override
    public void markSent(UUID id, Instant at) {
        requireOne(events.markSent(id, at), id);
    }

    @Override
    public void markRetry(UUID id, int attempts, Instant next, String error) {
        requireOne(events.markRetry(id, attempts, next, error), id);
    }

    @Override
    public void markFailed(UUID id, int attempts, String error) {
        requireOne(events.close(id, OutboxStatus.FAILED.name(), attempts, error), id);
    }

    @Override
    public void markDiscarded(UUID id, int attempts, String error) {
        requireOne(events.close(id, OutboxStatus.DISCARDED.name(), attempts, error), id);
    }

    private static void requireOne(int updated, UUID id) {
        if (updated != 1) {
            throw new IllegalStateException("El evento " + id + " no existe");
        }
    }
}
