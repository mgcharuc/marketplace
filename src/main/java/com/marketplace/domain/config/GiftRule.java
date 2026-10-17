package com.marketplace.domain.config;

import java.time.Instant;
import java.util.Objects;

/**
 * Unidades gratis de un SKU al cumplir una condición. Si es repetible, se entrega una recompensa
 * por cada vez que se alcanza el umbral.
 */
public record GiftRule(
        String id,
        String name,
        int priority,
        Instant validFrom,
        Instant validTo,
        GiftCondition condition,
        GiftReward reward,
        boolean repeatable)
        implements Rule {

    public GiftRule {
        Rule.requireHeader(id, name);
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(reward, "reward");
    }

    @Override
    public Stage stage() {
        return Stage.GIFT;
    }
}
