package com.marketplace.domain.config;

import com.marketplace.domain.Identifier;
import com.marketplace.domain.catalog.Sku;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Producto de una versión comercial. La tasa de impuesto sale de la clase tributaria del país del
 * tenant y el precio de lista no incluye impuesto.
 */
public record Product(Sku sku, String name, String family, String taxClass, BigDecimal listPrice) {

    public Product {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(listPrice, "listPrice");
        if (name.isBlank()) {
            throw new IllegalArgumentException("El producto " + sku.value() + " no tiene nombre");
        }
        Identifier.require(family, "family");
        Identifier.require(taxClass, "taxClass");
    }
}
