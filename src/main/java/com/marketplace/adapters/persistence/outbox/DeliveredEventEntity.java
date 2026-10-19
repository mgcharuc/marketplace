package com.marketplace.adapters.persistence.outbox;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "delivered_event")
class DeliveredEventEntity extends AssignedIdEntity<DeliveredEventId> {

    @EmbeddedId private DeliveredEventId id;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected DeliveredEventEntity() {}

    @Override
    public DeliveredEventId getId() {
        return id;
    }
}
