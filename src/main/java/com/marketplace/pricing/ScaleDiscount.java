package com.marketplace.pricing;

import com.marketplace.domain.config.ScaleMode;
import com.marketplace.domain.money.Money;
import java.util.List;

public record ScaleDiscount(
        String ruleId,
        String name,
        Money amount,
        List<LineAmount> lineAmounts,
        ScaleMode mode,
        List<TierAmount> tiers)
        implements AppliedDiscount {

    public ScaleDiscount {
        lineAmounts = List.copyOf(lineAmounts);
        tiers = List.copyOf(tiers);
    }
}
