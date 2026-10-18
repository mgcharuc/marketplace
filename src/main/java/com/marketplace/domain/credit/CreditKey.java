package com.marketplace.domain.credit;

import com.marketplace.domain.Identifier;

/** Identifica la cuenta de crédito de un tendero con un distribuidor dentro de un tenant. */
public record CreditKey(String tenantId, String customerId, String distributorId) {

    public CreditKey {
        Identifier.require(tenantId, "tenantId");
        Identifier.require(customerId, "customerId");
        Identifier.require(distributorId, "distributorId");
    }
}
