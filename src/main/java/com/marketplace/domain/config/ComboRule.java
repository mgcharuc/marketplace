package com.marketplace.domain.config;

import com.marketplace.domain.catalog.Sku;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Precio cerrado por cada conjunto completo de componentes. */
public record ComboRule(
        String id,
        String name,
        int priority,
        Instant validFrom,
        Instant validTo,
        List<ComboComponent> components,
        BigDecimal comboPrice)
        implements Rule {

    public ComboRule {
        Rule.requireHeader(id, name);
        Objects.requireNonNull(comboPrice, "comboPrice");
        components = List.copyOf(components);
        if (components.isEmpty()) {
            throw new IllegalArgumentException("El combo " + id + " no tiene componentes");
        }
        var skus = new HashSet<Sku>();
        for (ComboComponent component : components) {
            if (!skus.add(component.sku())) {
                throw new IllegalArgumentException(
                        "El combo " + id + " repite el SKU " + component.sku().value());
            }
        }
    }

    @Override
    public Stage stage() {
        return Stage.COMBO;
    }
}
