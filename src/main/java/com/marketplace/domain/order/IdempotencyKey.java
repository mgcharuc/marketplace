package com.marketplace.domain.order;

import com.marketplace.domain.Identifier;
import java.util.Objects;
import java.util.regex.Pattern;

/** Clave de idempotencia de una confirmación. Cada tendero tiene su propio espacio de claves. */
public record IdempotencyKey(String tenantId, String customerId, String value) {

    private static final Pattern FORMAT = Pattern.compile("[\\x21-\\x7E]{1,128}");

    public IdempotencyKey {
        Identifier.require(tenantId, "tenantId");
        Identifier.require(customerId, "customerId");
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("Clave de idempotencia con formato inválido");
        }
    }

    /** Entre 1 y 128 caracteres ASCII visibles. */
    public static boolean isValid(String value) {
        return FORMAT.matcher(value).matches();
    }
}
