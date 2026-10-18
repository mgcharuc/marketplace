package com.marketplace.adapters.persistence;

import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class StoredAmounts {

    private StoredAmounts() {}

    public static BigDecimal inScale(BigDecimal stored, CurrencyConfig currency) {
        return stored.setScale(currency.scale(), RoundingMode.UNNECESSARY);
    }

    public static Money money(BigDecimal stored, CurrencyConfig currency) {
        return new Money(inScale(stored, currency), currency);
    }
}
