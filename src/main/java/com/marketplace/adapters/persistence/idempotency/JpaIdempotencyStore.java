package com.marketplace.adapters.persistence.idempotency;

import com.marketplace.application.port.IdempotencyStore;
import com.marketplace.domain.order.IdempotencyKey;
import com.marketplace.domain.order.Reservation;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
class JpaIdempotencyStore implements IdempotencyStore {

    private final IdempotencyRecordEntityRepository records;

    JpaIdempotencyStore(IdempotencyRecordEntityRepository records) {
        this.records = records;
    }

    @Override
    public Reservation reserve(
            IdempotencyKey key, String requestHash, Instant now, Instant expiresAt) {
        var id = IdempotencyRecordId.of(key);
        if (records.reserve(id, requestHash, now, expiresAt).isPresent()) {
            return new Reservation.Reserved();
        }
        IdempotencyRecordEntity stored =
                records.findById(id)
                        .orElseThrow(() -> new IllegalStateException("La clave vigente no existe"));
        if (!stored.requestHash().equals(requestHash)) {
            return new Reservation.Conflict();
        }
        if (stored.responseBody() == null) {
            throw new IllegalStateException("Clave vigente sin respuesta guardada");
        }
        return new Reservation.Replay(stored.orderId(), stored.responseBody());
    }

    @Override
    public void complete(IdempotencyKey key, String orderId, String body) {
        if (records.complete(IdempotencyRecordId.of(key), orderId, body) != 1) {
            throw new IllegalStateException("La clave no estaba reservada");
        }
    }
}
