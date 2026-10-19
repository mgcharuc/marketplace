package com.marketplace.domain.outbox;

import com.marketplace.domain.Identifier;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Aviso a un sistema externo, guardado en la misma transacción que lo origina. */
public record OutboxEvent(
        UUID id,
        String tenantId,
        String aggregateId,
        String eventType,
        Destination destination,
        String payload,
        OutboxStatus status,
        int attempts,
        Instant nextAttemptAt,
        Instant retryWindowStart,
        Instant createdAt) {

    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";

    public OutboxEvent {
        Objects.requireNonNull(id, "id");
        Identifier.require(tenantId, "tenantId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(retryWindowStart, "retryWindowStart");
        Objects.requireNonNull(createdAt, "createdAt");
        if (attempts < 0) {
            throw new IllegalArgumentException("Intentos negativos en el evento " + id);
        }
    }

    /** Evento de una orden recién confirmada, listo para entregarse. */
    public static OutboxEvent orderConfirmed(
            UUID id,
            String tenantId,
            String orderId,
            Destination destination,
            String payload,
            Instant now) {
        return new OutboxEvent(
                id,
                tenantId,
                orderId,
                ORDER_CONFIRMED,
                destination,
                payload,
                OutboxStatus.PENDING,
                0,
                now,
                now,
                now);
    }
}
