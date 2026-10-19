package com.marketplace.domain.config;

import com.marketplace.domain.Identifier;
import com.marketplace.domain.money.CurrencyConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * Marca comercial dentro de un país. Es la unidad de configuración comercial y de aislamiento de
 * datos, y toma la configuración fiscal y monetaria de su país.
 */
public record Tenant(
        String id,
        Country country,
        String brand,
        String orderNumberPrefix,
        Duration quoteTtl,
        Duration idempotencyTtl) {

    public Tenant {
        Identifier.require(id, "tenantId");
        Identifier.require(orderNumberPrefix, "orderNumberPrefix");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(brand, "brand");
        if (brand.isBlank()) {
            throw new IllegalArgumentException("La marca del tenant " + id + " está vacía");
        }
        requirePositive(quoteTtl, "quoteTtl");
        requirePositive(idempotencyTtl, "idempotencyTtl");
    }

    public CurrencyConfig currency() {
        return country.currency();
    }

    /** Tasa de impuesto del producto según la clase tributaria que tiene en el país del tenant. */
    public BigDecimal taxRate(Product product) {
        return country.taxClass(product.taxClass())
                .map(TaxClass::rate)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "La clase tributaria "
                                                + product.taxClass()
                                                + " no existe en "
                                                + country.code()));
    }

    /**
     * Número de orden con el prefijo del tenant y la secuencia rellena con ceros hasta ocho
     * dígitos. Una secuencia más larga se escribe completa.
     */
    public String orderNumber(long sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("Secuencia de orden menor que 1: " + sequence);
        }
        return "%s-%08d".formatted(orderNumberPrefix, sequence);
    }

    private static void requirePositive(Duration ttl, String field) {
        Objects.requireNonNull(ttl, field);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(field + " debe ser mayor que cero: " + ttl);
        }
    }
}
