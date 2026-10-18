package com.marketplace.pricing;

import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Unidades que entrega una regla de obsequio sin cobrarlas. {@code taxRate} y {@code taxBorneBy}
 * son {@code null} cuando el país no grava los obsequios.
 */
public record Gift(
        String ruleId,
        Sku sku,
        String name,
        int quantity,
        Money referenceUnitPrice,
        Money taxBase,
        BigDecimal taxRate,
        Money taxAmount,
        TaxBearer taxBorneBy) {

    public Gift {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(referenceUnitPrice, "referenceUnitPrice");
        Objects.requireNonNull(taxBase, "taxBase");
        Objects.requireNonNull(taxAmount, "taxAmount");
        if (quantity < 1) {
            throw new IllegalArgumentException("Cantidad obsequiada menor que 1: " + quantity);
        }
        if ((taxRate == null) != (taxBorneBy == null)) {
            throw new IllegalArgumentException(
                    "La tasa y quién asume el impuesto del obsequio van juntos");
        }
    }
}
