package com.marketplace.domain.config;

import java.util.Objects;

/** Regla de configuración incumplida, con su código y el elemento que la incumple. */
public record ConfigViolation(String code, String ref) {

    public ConfigViolation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(ref, "ref");
    }
}
