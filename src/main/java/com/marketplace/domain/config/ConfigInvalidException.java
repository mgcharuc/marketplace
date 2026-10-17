package com.marketplace.domain.config;

import java.util.List;

/** Una configuración que incumple una o más reglas. Lleva todas las violaciones encontradas. */
public class ConfigInvalidException extends RuntimeException {

    private final List<ConfigViolation> violations;

    public ConfigInvalidException(List<ConfigViolation> violations) {
        this(violations, null);
    }

    public ConfigInvalidException(List<ConfigViolation> violations, Throwable cause) {
        super("Configuración inválida: " + violations, cause);
        this.violations = List.copyOf(violations);
    }

    public List<ConfigViolation> violations() {
        return violations;
    }
}
