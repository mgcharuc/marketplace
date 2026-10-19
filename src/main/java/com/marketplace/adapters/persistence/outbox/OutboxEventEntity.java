package com.marketplace.adapters.persistence.outbox;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import com.marketplace.domain.outbox.OutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
class OutboxEventEntity extends AssignedIdEntity<UUID> {

    @Id private UUID id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "aggregate_id")
    private String aggregateId;

    @Column(name = "event_type")
    private String eventType;

    private String destination;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private String status;

    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "retry_window_start")
    private Instant retryWindowStart;

    @Column(name = "created_at")
    private Instant createdAt;

    protected OutboxEventEntity() {}

    OutboxEventEntity(OutboxEvent event) {
        this.id = event.id();
        this.tenantId = event.tenantId();
        this.aggregateId = event.aggregateId();
        this.eventType = event.eventType();
        this.destination = event.destination().name();
        this.payload = event.payload();
        this.status = event.status().name();
        this.attempts = event.attempts();
        this.nextAttemptAt = event.nextAttemptAt();
        this.retryWindowStart = event.retryWindowStart();
        this.createdAt = event.createdAt();
    }

    @Override
    public UUID getId() {
        return id;
    }
}
