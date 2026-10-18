package com.marketplace.application.port;

import com.marketplace.domain.config.Tenant;
import java.util.Optional;

public interface TenantRepository {

    /** Tenant con la configuración de su país ya resuelta, leída de nuevo en cada llamada. */
    Optional<Tenant> find(String tenantId);

    /** Registra el tenant y, si todavía no existe, su país con las clases tributarias. */
    void add(Tenant tenant);
}
