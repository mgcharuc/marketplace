package com.marketplace.domain.outbox;

import com.marketplace.domain.DomainException;
import java.util.Objects;

public class EventNotFailedException extends DomainException {

    private final OutboxStatus status;

    public EventNotFailedException(OutboxStatus status) {
        super("EVENT_NOT_FAILED", "Solo se puede reencolar un evento fallido.");
        this.status = Objects.requireNonNull(status, "status");
    }

    public OutboxStatus status() {
        return status;
    }
}
