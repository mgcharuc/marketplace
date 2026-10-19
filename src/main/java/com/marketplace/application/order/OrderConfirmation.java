package com.marketplace.application.order;

import java.util.Objects;

/** Respuesta de una confirmación. Es repetida cuando la clave ya había creado la orden. */
public record OrderConfirmation(String orderId, String body, boolean replayed) {

    public OrderConfirmation {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(body, "body");
    }
}
