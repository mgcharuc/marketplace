package com.marketplace.pricing;

import com.marketplace.domain.money.Money;

public record Totals(
        Money grossSubtotal,
        Money discountTotal,
        Money netSubtotal,
        Money taxableBase,
        Money taxTotal,
        Money total) {}
