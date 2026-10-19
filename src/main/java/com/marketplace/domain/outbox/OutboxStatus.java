package com.marketplace.domain.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED,
    DISCARDED
}
