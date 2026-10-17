package com.marketplace.pricing;

import com.marketplace.domain.catalog.Quantity;
import com.marketplace.domain.catalog.Sku;
import java.util.Objects;

public record CartLine(Sku sku, Quantity quantity) {

    public CartLine {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(quantity, "quantity");
    }
}
