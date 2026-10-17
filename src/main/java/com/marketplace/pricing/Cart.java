package com.marketplace.pricing;

import java.util.HashSet;
import java.util.List;

/** Carrito ya consolidado: una línea por SKU, en el orden en que apareció cada uno. */
public record Cart(List<CartLine> lines) {

    public Cart {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("El carrito no tiene líneas");
        }
        var skus = new HashSet<>();
        for (CartLine line : lines) {
            if (!skus.add(line.sku())) {
                throw new IllegalArgumentException(
                        "SKU repetido en el carrito: " + line.sku().value());
            }
        }
    }
}
