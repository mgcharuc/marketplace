package com.marketplace.domain.catalog;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Código de producto. Distingue mayúsculas y se ordena por código de carácter, así que las
 * mayúsculas van antes que las minúsculas. Ese orden decide los empates del reparto entre líneas.
 */
public record Sku(String value) implements Comparable<Sku> {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public Sku {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("SKU con formato inválido: " + value);
        }
    }

    @Override
    public int compareTo(Sku other) {
        return value.compareTo(other.value);
    }
}
