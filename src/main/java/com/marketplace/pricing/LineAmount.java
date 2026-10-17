package com.marketplace.pricing;

import com.marketplace.domain.money.Money;

public record LineAmount(String lineId, Money amount) {}
