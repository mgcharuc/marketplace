package com.marketplace.application.port;

import com.marketplace.domain.config.ConfigVersion;
import java.time.Instant;

public interface ConfigRepository {

    /** Versión vigente del tenant. Todo tenant registrado tiene una. */
    ConfigVersion current(String tenantId);

    /**
     * Guarda la versión y la deja como vigente en lugar de la anterior. No valida reglas de
     * negocio: la versión ya pasó por {@code TenantConfigHistory}.
     */
    void publish(ConfigVersion version, Instant publishedAt);
}
