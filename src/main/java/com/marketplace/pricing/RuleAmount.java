package com.marketplace.pricing;

import com.marketplace.domain.money.Money;

public record RuleAmount(String ruleId, Money amount) {}
