package com.marketplace.domain.config;

import com.marketplace.domain.catalog.Sku;
import java.util.Objects;

public record ComboComponent(Sku sku, int qty) {

    public ComboComponent {
        Objects.requireNonNull(sku, "sku");
        if (qty < 1) {
            throw new IllegalArgumentException("Cantidad de componente menor que 1: " + qty);
        }
    }
}
