package com.marketplace.domain.order;

import com.marketplace.domain.Identifier;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.QuoteHeader;
import java.time.Instant;
import java.util.Objects;

/**
 * Datos de la orden al confirmar. A crédito lleva el cupo que le queda al tendero con el
 * distribuidor, y de contado no lleva cupo.
 */
public record OrderHeader(
        String orderId,
        String orderNumber,
        QuoteHeader quote,
        Money total,
        Instant confirmedAt,
        Money availableAfter) {

    public OrderHeader {
        Identifier.require(orderId, "orderId");
        Identifier.require(orderNumber, "orderNumber");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        requireCurrency(total, quote);
        boolean credit = quote.paymentMethod() == PaymentMethod.CREDIT;
        if (credit && availableAfter == null) {
            throw new IllegalArgumentException("La orden a crédito " + orderId + " no trae cupo");
        }
        if (!credit && availableAfter != null) {
            throw new IllegalArgumentException("La orden de contado " + orderId + " trae cupo");
        }
        if (availableAfter != null) {
            requireCurrency(availableAfter, quote);
        }
    }

    public OrderKey key() {
        return new OrderKey(quote.tenantId(), quote.customerId(), orderId);
    }

    private static void requireCurrency(Money amount, QuoteHeader quote) {
        if (!amount.currency().equals(quote.currency())) {
            throw new IllegalArgumentException(
                    "El monto está en "
                            + amount.currency().code()
                            + " y la cotización en "
                            + quote.currency().code());
        }
    }
}
