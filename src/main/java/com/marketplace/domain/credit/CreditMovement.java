package com.marketplace.domain.credit;

import com.marketplace.domain.Identifier;
import com.marketplace.domain.money.Money;
import java.time.Instant;
import java.util.Objects;

/** Consumo de cupo que hizo una orden a crédito. Cada orden consume como máximo una vez. */
public record CreditMovement(String orderId, CreditKey account, Money amount, Instant createdAt) {

    public CreditMovement {
        Identifier.require(orderId, "orderId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(createdAt, "createdAt");
        if (amount.amount().signum() == 0) {
            throw new IllegalArgumentException("La orden " + orderId + " consume un cupo de cero");
        }
    }
}
