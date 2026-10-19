package com.marketplace.adapters.persistence.outbox;

import com.marketplace.application.port.Outbox;
import com.marketplace.domain.outbox.OutboxEvent;
import org.springframework.stereotype.Repository;

@Repository
class JpaOutbox implements Outbox {

    private final OutboxEventEntityRepository events;

    JpaOutbox(OutboxEventEntityRepository events) {
        this.events = events;
    }

    @Override
    public void append(OutboxEvent event) {
        events.save(new OutboxEventEntity(event));
    }
}
