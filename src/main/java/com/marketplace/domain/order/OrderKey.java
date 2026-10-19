package com.marketplace.domain.order;

import com.marketplace.domain.Identifier;

/** Una orden solo es visible para el tendero que la confirmó, dentro de su tenant. */
public record OrderKey(String tenantId, String customerId, String orderId) {

    public OrderKey {
        Identifier.require(tenantId, "tenantId");
        Identifier.require(customerId, "customerId");
        Identifier.require(orderId, "orderId");
    }
}
