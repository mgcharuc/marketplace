package com.marketplace.application.port;

import com.marketplace.domain.outbox.OutboxEvent;

public interface Outbox {

    /** Encola el evento en la transacción en curso. */
    void append(OutboxEvent event);
}
