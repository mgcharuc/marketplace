package com.marketplace.application.port;

import com.marketplace.domain.outbox.Destination;
import java.time.Instant;
import java.util.UUID;

public interface DeliveryLog {

    /**
     * Registra la entrega en una transacción propia, que queda confirmada aunque la transacción del
     * evento se revierta después. Registrar dos veces la misma entrega no cambia nada.
     */
    void record(UUID eventId, Destination destination, Instant at);
}
