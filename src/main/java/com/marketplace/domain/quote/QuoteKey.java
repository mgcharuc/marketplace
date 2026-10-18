package com.marketplace.domain.quote;

import com.marketplace.domain.Identifier;

/** Una cotización solo es visible para el tendero que la pidió, dentro de su tenant. */
public record QuoteKey(String tenantId, String customerId, String quoteId) {

    public QuoteKey {
        Identifier.require(tenantId, "tenantId");
        Identifier.require(customerId, "customerId");
        Identifier.require(quoteId, "quoteId");
    }
}
