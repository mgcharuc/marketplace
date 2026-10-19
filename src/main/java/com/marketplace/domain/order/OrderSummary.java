package com.marketplace.domain.order;

import com.marketplace.domain.Identifier;
import com.marketplace.domain.money.Money;
import java.time.Instant;
import java.util.Objects;

/** Resumen de una orden para el historial del tendero. */
public record OrderSummary(
        String orderId,
        String orderNumber,
        String distributorId,
        Instant confirmedAt,
        Money total,
        OrderStatus status) {

    public OrderSummary {
        Identifier.require(orderId, "orderId");
        Identifier.require(orderNumber, "orderNumber");
        Identifier.require(distributorId, "distributorId");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(status, "status");
    }
}
