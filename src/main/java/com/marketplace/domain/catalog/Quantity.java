package com.marketplace.domain.catalog;

/** Cantidad de un producto en el carrito. Las cantidades calculadas por el motor son enteros. */
public record Quantity(int value) {

    public static final int MIN = 1;
    public static final int MAX = 9999;

    public Quantity {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException(
                    "La cantidad debe estar entre " + MIN + " y " + MAX + ": " + value);
        }
    }
}
