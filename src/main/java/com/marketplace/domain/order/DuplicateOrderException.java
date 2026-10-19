package com.marketplace.domain.order;

/** Otra transacción guardó primero una orden para la misma cotización. */
public class DuplicateOrderException extends RuntimeException {

    public DuplicateOrderException(Throwable cause) {
        super("Ya existe una orden para la cotización", cause);
    }
}
