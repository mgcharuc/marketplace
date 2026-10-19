package com.marketplace.adapters.persistence.idempotency;

import com.marketplace.domain.order.IdempotencyKey;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
record IdempotencyRecordId(
        @Column(name = "tenant_id") String tenantId,
        @Column(name = "customer_id") String customerId,
        @Column(name = "idem_key") String idemKey) {

    static IdempotencyRecordId of(IdempotencyKey key) {
        return new IdempotencyRecordId(key.tenantId(), key.customerId(), key.value());
    }
}
