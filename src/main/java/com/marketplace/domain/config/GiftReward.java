package com.marketplace.domain.config;

import com.marketplace.domain.catalog.Sku;
import java.util.Objects;

public record GiftReward(Sku sku, int qty) {

    public GiftReward {
        Objects.requireNonNull(sku, "sku");
        if (qty < 1) {
            throw new IllegalArgumentException("Cantidad obsequiada menor que 1: " + qty);
        }
    }
}
