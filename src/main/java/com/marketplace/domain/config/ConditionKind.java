package com.marketplace.domain.config;

/**
 * Qué mide la condición de un obsequio. {@code QUANTITY} cuenta unidades pagadas y {@code
 * NET_AMOUNT} suma la base gravable después de descuentos.
 */
public enum ConditionKind {
    QUANTITY,
    NET_AMOUNT
}
