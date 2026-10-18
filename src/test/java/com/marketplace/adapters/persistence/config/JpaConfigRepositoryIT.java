package com.marketplace.adapters.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.application.port.ConfigCodec;
import com.marketplace.application.port.ConfigRepository;
import com.marketplace.application.port.SeedSource;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.TenantConfigHistory;
import com.marketplace.support.IntegrationTest;
import com.marketplace.support.Resources;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JpaConfigRepositoryIT {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-10-16T15:00:00Z");

    @Autowired ConfigRepository configs;
    @Autowired TenantRepository tenants;
    @Autowired ConfigCodec codec;
    @Autowired SeedSource seeds;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("La versión vigente se lee igual a la de la semilla")
    void readsSeededVersion() {
        assertThat(seeds.versions())
                .allSatisfy(
                        version ->
                                assertThat(configs.current(version.tenantId())).isEqualTo(version));
    }

    @Test
    @DisplayName("Publicar la versión siguiente la convierte en vigente y conserva la anterior")
    void publishingNextVersionMakesItCurrent() {
        var second = publishSecondVersion();

        assertThat(configs.current("CO-MARCA-A")).isEqualTo(second);
        assertThat(
                        jdbc.queryForList(
                                "SELECT version FROM config_version"
                                        + " WHERE tenant_id = 'CO-MARCA-A' AND NOT is_current",
                                Integer.class))
                .containsExactly(1);
    }

    @Test
    @DisplayName("Publicar en un tenant no cambia la versión vigente de otro")
    void publishingIsScopedToTheTenant() {
        var before = configs.current("CO-MARCA-B");

        publishSecondVersion();

        assertThat(configs.current("CO-MARCA-B")).isEqualTo(before);
    }

    @Test
    @DisplayName("Si guardar la versión nueva falla, la vigente sigue siendo la anterior")
    void failedPublishKeepsCurrentVersion() {
        var repeated = configs.current("CO-MARCA-A");

        assertThatThrownBy(() -> configs.publish(repeated, PUBLISHED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(configs.current("CO-MARCA-A")).isEqualTo(repeated);
    }

    @Test
    @DisplayName("La definición se guarda como jsonb")
    void storesDefinitionAsJsonb() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT definition ->> 'tenantId' FROM config_version"
                                        + " WHERE tenant_id = 'EC-MARCA-A'",
                                String.class))
                .isEqualTo("EC-MARCA-A");
    }

    private ConfigVersion publishSecondVersion() {
        var second = codec.decode(Resources.read("seed/co-marca-a.v2.json"));
        configs.publish(history("CO-MARCA-A").publish(second), PUBLISHED_AT.plusSeconds(60));
        return second;
    }

    private TenantConfigHistory history(String tenantId) {
        return new TenantConfigHistory(
                tenants.find(tenantId).orElseThrow(), configs.current(tenantId).number());
    }
}
