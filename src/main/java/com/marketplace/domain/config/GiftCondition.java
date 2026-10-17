package com.marketplace.domain.config;

import java.math.BigDecimal;
import java.util.Objects;

/** Umbral que activa un obsequio: unidades pagadas o monto neto, según el tipo. */
public record GiftCondition(ConditionKind kind, Scope scope, BigDecimal threshold) {

    public GiftCondition {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(threshold, "threshold");
        if (threshold.signum() <= 0) {
            throw new IllegalArgumentException("El umbral debe ser mayor que cero: " + threshold);
        }
        if (kind == ConditionKind.QUANTITY && threshold.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Un umbral de cantidad debe ser entero: " + threshold);
        }
    }
}
