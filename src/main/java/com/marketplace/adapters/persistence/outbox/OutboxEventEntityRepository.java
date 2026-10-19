package com.marketplace.adapters.persistence.outbox;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface OutboxEventEntityRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(nativeQuery = true, value = RelayLimits.IN_TRANSACTION)
    String limitWaitsInTransaction();

    @Query(
            nativeQuery = true,
            value =
                    """
                    SELECT * FROM outbox_event
                    WHERE status = 'PENDING' AND next_attempt_at <= :now
                    ORDER BY next_attempt_at, id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """)
    Optional<OutboxEventEntity> lockNextPending(Instant now);

    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.status = 'SENT', e.sentAt = :at WHERE e.id = :id")
    int markSent(UUID id, Instant at);

    @Modifying
    @Query(
            """
            UPDATE OutboxEventEntity e
            SET e.attempts = :attempts, e.nextAttemptAt = :next, e.lastError = :error
            WHERE e.id = :id
            """)
    int markRetry(UUID id, int attempts, Instant next, String error);

    @Modifying
    @Query(
            """
            UPDATE OutboxEventEntity e
            SET e.status = :status, e.attempts = :attempts, e.lastError = :error
            WHERE e.id = :id
            """)
    int close(UUID id, String status, int attempts, String error);
}
