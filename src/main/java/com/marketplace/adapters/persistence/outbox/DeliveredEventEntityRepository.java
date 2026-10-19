package com.marketplace.adapters.persistence.outbox;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface DeliveredEventEntityRepository
        extends JpaRepository<DeliveredEventEntity, DeliveredEventId> {

    @Query(nativeQuery = true, value = RelayLimits.IN_TRANSACTION)
    String limitWaitsInTransaction();

    @Modifying
    @Query(
            nativeQuery = true,
            value =
                    """
                    INSERT INTO delivered_event (event_id, destination, delivered_at)
                    VALUES (:eventId, :destination, :at)
                    ON CONFLICT DO NOTHING
                    """)
    void record(UUID eventId, String destination, Instant at);
}
