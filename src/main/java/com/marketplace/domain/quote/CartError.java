package com.marketplace.domain.quote;

import java.util.Objects;

/**
 * Error en el contenido del carrito. Un error de un ítem lleva su posición original en la petición
 * y el SKU recibido. Uno del carrito completo los deja en {@code null}.
 */
public record CartError(Integer index, String field, String sku, String code) {

    public CartError {
        Objects.requireNonNull(code, "code");
    }
}
