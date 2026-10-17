package com.marketplace.domain.config;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ScaleRule(
        String id,
        String name,
        int priority,
        Instant validFrom,
        Instant validTo,
        Scope scope,
        ScaleMode mode,
        List<Tier> tiers)
        implements Rule {

    public ScaleRule {
        Rule.requireHeader(id, name);
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(mode, "mode");
        tiers = List.copyOf(tiers);
    }

    @Override
    public Stage stage() {
        return Stage.SCALE;
    }
}
