package com.marketplace.application.port;

import com.marketplace.domain.config.ConfigVersion;
import java.util.List;

/** Datos iniciales de la aplicación: tenants con su país y la versión 1 de cada uno. */
public interface SeedSource {

    List<TenantSeed> tenants();

    List<ConfigVersion> versions();
}
