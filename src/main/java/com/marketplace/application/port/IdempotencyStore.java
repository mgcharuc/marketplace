package com.marketplace.application.port;

import com.marketplace.domain.order.IdempotencyKey;
import com.marketplace.domain.order.Reservation;
import java.time.Instant;

public interface IdempotencyStore {

    /**
     * Reserva la clave para la petición. Si con ella ya se creó una orden, devuelve su respuesta
     * cuando el hash coincide o un conflicto cuando no. Una clave vencida se reutiliza como nueva.
     * Si otra transacción tiene la clave reservada, espera a que termine.
     */
    Reservation reserve(IdempotencyKey key, String requestHash, Instant now, Instant expiresAt);

    /** Guarda la respuesta de la orden creada con la clave reservada. */
    void complete(IdempotencyKey key, String orderId, String body);
}
