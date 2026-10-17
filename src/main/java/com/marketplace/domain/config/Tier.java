package com.marketplace.domain.config;

import java.math.BigDecimal;
import java.util.Objects;

public record Tier(int fromQty, BigDecimal percent) {

    public Tier {
        Objects.requireNonNull(percent, "percent");
    }
}
