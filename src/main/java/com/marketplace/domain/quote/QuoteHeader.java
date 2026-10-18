package com.marketplace.domain.quote;

import com.marketplace.domain.Identifier;
import com.marketplace.domain.money.CurrencyConfig;
import java.time.Instant;
import java.util.Objects;

public record QuoteHeader(
        String quoteId,
        String tenantId,
        String customerId,
        String distributorId,
        int configVersion,
        CurrencyConfig currency,
        PaymentMethod paymentMethod,
        Instant createdAt,
        Instant expiresAt) {

    public QuoteHeader {
        Identifier.require(quoteId, "quoteId");
        Identifier.require(tenantId, "tenantId");
        Identifier.require(customerId, "customerId");
        Identifier.require(distributorId, "distributorId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(paymentMethod, "paymentMethod");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (configVersion < 1) {
            throw new IllegalArgumentException(
                    "Versión de configuración menor que 1: " + configVersion);
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "La cotización " + quoteId + " vence antes de crearse");
        }
    }

    public QuoteKey key() {
        return new QuoteKey(tenantId, customerId, quoteId);
    }
}
