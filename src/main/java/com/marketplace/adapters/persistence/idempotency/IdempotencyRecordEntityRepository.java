package com.marketplace.adapters.persistence.idempotency;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface IdempotencyRecordEntityRepository
        extends JpaRepository<IdempotencyRecordEntity, IdempotencyRecordId> {

    // Devuelve la clave si quedó reservada, sea nueva o una vencida que se reutiliza.
    @Transactional
    @Query(
            nativeQuery = true,
            value =
                    """
                    INSERT INTO idempotency_record
                      (tenant_id, customer_id, idem_key, request_hash, created_at, expires_at)
                    VALUES (:#{#id.tenantId}, :#{#id.customerId}, :#{#id.idemKey},
                            :requestHash, :now, :expiresAt)
                    ON CONFLICT (tenant_id, customer_id, idem_key) DO UPDATE
                      SET request_hash = EXCLUDED.request_hash, order_id = NULL, response_body = NULL,
                          created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at
                      WHERE idempotency_record.expires_at <= :now
                    RETURNING idem_key
                    """)
    Optional<String> reserve(
            IdempotencyRecordId id, String requestHash, Instant now, Instant expiresAt);

    @Transactional
    @Modifying
    @Query(
            """
            UPDATE IdempotencyRecordEntity r SET r.orderId = :orderId, r.responseBody = :body
            WHERE r.id = :id
            """)
    int complete(IdempotencyRecordId id, String orderId, String body);
}
