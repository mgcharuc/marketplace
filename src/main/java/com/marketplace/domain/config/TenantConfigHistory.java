package com.marketplace.domain.config;

import java.util.Objects;

/**
 * Historial de versiones comerciales de un tenant. Solo publica la versión siguiente a la vigente y
 * solo si cumple las reglas de configuración para el país del tenant.
 */
public class TenantConfigHistory {

    private static final ConfigValidator VALIDATOR = new ConfigValidator();

    private final Tenant tenant;
    private int currentNumber;

    public TenantConfigHistory(Tenant tenant, int currentNumber) {
        this.tenant = Objects.requireNonNull(tenant, "tenant");
        if (currentNumber < 0) {
            throw new IllegalArgumentException("Versión vigente negativa: " + currentNumber);
        }
        this.currentNumber = currentNumber;
    }

    public ConfigVersion publish(ConfigVersion version) {
        if (!version.tenantId().equals(tenant.id())) {
            throw new IllegalArgumentException(
                    "La versión es de " + version.tenantId() + " y no de " + tenant.id());
        }
        if (version.number() != currentNumber + 1) {
            throw new IllegalArgumentException(
                    "Se esperaba publicar la versión "
                            + (currentNumber + 1)
                            + " y llegó la "
                            + version.number());
        }
        var violations = VALIDATOR.validate(version, tenant.country());
        if (!violations.isEmpty()) {
            throw new ConfigInvalidException(violations);
        }
        currentNumber = version.number();
        return version;
    }

    public int currentNumber() {
        return currentNumber;
    }
}
