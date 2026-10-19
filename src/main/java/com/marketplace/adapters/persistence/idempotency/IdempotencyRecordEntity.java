package com.marketplace.adapters.persistence.idempotency;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
class IdempotencyRecordEntity extends AssignedIdEntity<IdempotencyRecordId> {

    @EmbeddedId private IdempotencyRecordId id;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected IdempotencyRecordEntity() {}

    @Override
    public IdempotencyRecordId getId() {
        return id;
    }

    String requestHash() {
        return requestHash;
    }

    String orderId() {
        return orderId;
    }

    String responseBody() {
        return responseBody;
    }
}
