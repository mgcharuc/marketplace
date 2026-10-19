package com.marketplace.application.port;

import com.marketplace.domain.outbox.Destination;
import com.marketplace.domain.outbox.OutboxEvent;

public interface EventDeliverer {

    Destination destination();

    /** Entrega el evento y, si el destino lo recibe, registra la entrega. */
    void deliver(OutboxEvent event) throws DeliveryException;
}
