package com.marketplace.domain.catalog;

import com.marketplace.domain.Identifier;

/**
 * Código de producto. Distingue mayúsculas y se ordena por código de carácter, así que las
 * mayúsculas van antes que las minúsculas. Ese orden decide los empates del reparto entre líneas.
 */
public record Sku(String value) implements Comparable<Sku> {

    public Sku {
        Identifier.require(value, "SKU");
    }

    @Override
    public int compareTo(Sku other) {
        return value.compareTo(other.value);
    }
}
