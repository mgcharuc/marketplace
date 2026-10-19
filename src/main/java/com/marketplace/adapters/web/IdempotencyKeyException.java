package com.marketplace.adapters.web;

class IdempotencyKeyException extends RuntimeException {

    IdempotencyKeyException() {
        super("Idempotency-Key ausente o inválida");
    }
}
