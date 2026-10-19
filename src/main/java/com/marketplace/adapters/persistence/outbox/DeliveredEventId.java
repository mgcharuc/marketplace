package com.marketplace.adapters.persistence.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
record DeliveredEventId(
        @Column(name = "event_id") UUID eventId,
        @Column(name = "destination") String destination) {}
