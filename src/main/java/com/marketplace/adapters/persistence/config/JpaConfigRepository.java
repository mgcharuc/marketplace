package com.marketplace.adapters.persistence.config;

import com.marketplace.application.port.ConfigCodec;
import com.marketplace.application.port.ConfigRepository;
import com.marketplace.domain.config.ConfigVersion;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaConfigRepository implements ConfigRepository {

    private final ConfigVersionEntityRepository versions;
    private final ConfigCodec codec;

    JpaConfigRepository(ConfigVersionEntityRepository versions, ConfigCodec codec) {
        this.versions = versions;
        this.codec = codec;
    }

    @Override
    public ConfigVersion current(String tenantId) {
        return versions.findByIdTenantIdAndCurrentTrue(tenantId)
                .map(version -> codec.decode(version.definition()))
                .orElseThrow(
                        () -> new IllegalStateException("Tenant sin versión vigente: " + tenantId));
    }

    // Se retira la versión vigente antes de insertar la nueva porque el índice único no es
    // diferible.
    @Override
    @Transactional
    public void publish(ConfigVersion version, Instant publishedAt) {
        versions.retireCurrent(version.tenantId());
        var id = new ConfigVersionId(version.tenantId(), version.number());
        versions.saveAndFlush(new ConfigVersionEntity(id, publishedAt, codec.encode(version)));
    }
}
