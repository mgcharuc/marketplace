package com.marketplace.domain.config;

import com.marketplace.domain.Identifier;
import java.time.Instant;
import java.util.Objects;

/**
 * Regla comercial de una versión. Dentro de una etapa se aplican por prioridad ascendente y luego
 * por id. Las fechas de vigencia son opcionales y un extremo nulo queda abierto.
 */
public sealed interface Rule permits ComboRule, ScaleRule, GiftRule {

    String id();

    String name();

    int priority();

    Instant validFrom();

    Instant validTo();

    Stage stage();

    /** Indica si la regla vale en {@code now}. Incluye el inicio y excluye el fin. */
    default boolean activeAt(Instant now) {
        return (validFrom() == null || !now.isBefore(validFrom()))
                && (validTo() == null || now.isBefore(validTo()));
    }

    static void requireHeader(String id, String name) {
        Identifier.require(id, "ruleId");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("La regla " + id + " no tiene nombre");
        }
    }
}
