package com.marketplace.domain.config;

import com.marketplace.domain.Identifier;
import com.marketplace.domain.catalog.Sku;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Versión de la configuración comercial de un tenant con sus precios, reglas y orden de etapas. Una
 * versión publicada no cambia. Cualquier ajuste genera una versión nueva.
 */
public record ConfigVersion(
        String tenantId,
        int number,
        List<Stage> pipeline,
        List<Product> products,
        List<Rule> rules) {

    public ConfigVersion {
        Identifier.require(tenantId, "tenantId");
        if (number < 1) {
            throw new IllegalArgumentException("Número de versión menor que 1: " + number);
        }
        pipeline = List.copyOf(pipeline);
        products = List.copyOf(products);
        rules = List.copyOf(rules);
        requireUnique(products, product -> product.sku().value(), "SKU repetido");
        requireUnique(rules, Rule::id, "Regla repetida");
    }

    public Optional<Product> product(Sku sku) {
        return products.stream().filter(product -> product.sku().equals(sku)).findFirst();
    }

    public List<Product> productsIn(Scope scope) {
        return products.stream().filter(scope::covers).toList();
    }

    private static <T> void requireUnique(List<T> items, Function<T, String> key, String error) {
        var seen = new HashSet<String>();
        for (T item : items) {
            Objects.requireNonNull(item, "item");
            if (!seen.add(key.apply(item))) {
                throw new IllegalArgumentException(error + ": " + key.apply(item));
            }
        }
    }
}
