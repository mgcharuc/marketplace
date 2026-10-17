package com.marketplace.domain.config;

import java.math.BigDecimal;
import java.util.Objects;

public record TaxClass(String code, BigDecimal rate) {

    public TaxClass {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(rate, "rate");
    }
}
