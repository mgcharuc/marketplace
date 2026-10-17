package com.marketplace.pricing;

import com.marketplace.domain.money.Money;
import java.math.BigDecimal;

public record TierAmount(int fromQty, BigDecimal percent, int units, Money amount) {}
