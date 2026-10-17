package com.marketplace.domain.config;

import com.marketplace.domain.Identifier;
import java.util.Objects;

/** Alcance de una regla: un SKU o todos los productos de una familia. */
public record Scope(ScopeKind kind, String ref) {

    public Scope {
        Objects.requireNonNull(kind, "kind");
        Identifier.require(ref, "ref");
    }

    public static Scope sku(String sku) {
        return new Scope(ScopeKind.SKU, sku);
    }

    public static Scope family(String family) {
        return new Scope(ScopeKind.FAMILY, family);
    }

    public boolean covers(Product product) {
        return switch (kind) {
            case SKU -> product.sku().value().equals(ref);
            case FAMILY -> product.family().equals(ref);
        };
    }
}
