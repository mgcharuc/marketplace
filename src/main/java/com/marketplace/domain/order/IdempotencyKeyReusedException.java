package com.marketplace.domain.order;

import com.marketplace.domain.DomainException;

public class IdempotencyKeyReusedException extends DomainException {

    public IdempotencyKeyReusedException() {
        super("IDEMPOTENCY_KEY_REUSED", "La clave de idempotencia ya se usó con otro pedido.");
    }
}
