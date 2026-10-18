package com.marketplace.adapters.persistence.credit;

import com.marketplace.domain.credit.CreditKey;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
record CreditAccountId(
        @Column(name = "tenant_id") String tenantId,
        @Column(name = "customer_id") String customerId,
        @Column(name = "distributor_id") String distributorId) {

    static CreditAccountId of(CreditKey key) {
        return new CreditAccountId(key.tenantId(), key.customerId(), key.distributorId());
    }

    CreditKey toKey() {
        return new CreditKey(tenantId, customerId, distributorId);
    }
}
