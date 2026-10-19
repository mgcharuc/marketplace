package com.marketplace.domain.order;

import java.util.Objects;

/**
 * Orden confirmada con el cuerpo de su respuesta, que la consulta devuelve tal cual. La secuencia
 * es el número de la orden sin el prefijo del tenant.
 */
public record Order(OrderHeader header, long sequence, String body) {

    public Order {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(body, "body");
        if (sequence < 1) {
            throw new IllegalArgumentException("Secuencia de orden menor que 1: " + sequence);
        }
    }
}
