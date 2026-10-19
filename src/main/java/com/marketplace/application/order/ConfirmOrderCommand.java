package com.marketplace.application.order;

import com.marketplace.domain.order.IdempotencyKey;
import com.marketplace.domain.quote.QuoteKey;
import java.util.Objects;

/** Pedido de confirmación con el hash del cuerpo normalizado. */
public record ConfirmOrderCommand(
        String tenantId,
        String customerId,
        String quoteId,
        String idempotencyKey,
        String requestHash) {

    public ConfirmOrderCommand {
        Objects.requireNonNull(requestHash, "requestHash");
    }

    QuoteKey quoteKey() {
        return new QuoteKey(tenantId, customerId, quoteId);
    }

    IdempotencyKey key() {
        return new IdempotencyKey(tenantId, customerId, idempotencyKey);
    }
}
