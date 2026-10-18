package com.marketplace.domain.credit;

import com.marketplace.domain.money.Money;
import java.util.Objects;

public record CreditAccount(CreditKey key, Money creditLimit, Money available) {

    public CreditAccount {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(creditLimit, "creditLimit");
        Objects.requireNonNull(available, "available");
    }
}
