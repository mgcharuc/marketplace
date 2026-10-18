package com.marketplace.application.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.application.port.ConfigRepository;
import com.marketplace.application.port.CreditRepository;
import com.marketplace.domain.credit.CreditKey;
import com.marketplace.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class LoadSeedsIT {

    private static final String COUNTS =
            """
            SELECT (SELECT count(*) FROM country) AS countries,
                   (SELECT count(*) FROM country_tax_class) AS tax_classes,
                   (SELECT count(*) FROM tenant) AS tenants,
                   (SELECT count(*) FROM distributor) AS distributors,
                   (SELECT count(*) FROM customer) AS customers,
                   (SELECT count(*) FROM credit_account) AS credit_accounts,
                   (SELECT count(*) FROM config_version) AS versions
            """;

    @Autowired LoadSeeds loadSeeds;
    @Autowired ConfigRepository configs;
    @Autowired CreditRepository credits;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Las semillas cargan países, tenants, distribuidores, tenderos, cupos y versiones")
    void loadsEverySeed() {
        assertThat(jdbc.queryForMap(COUNTS))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "countries", 2L,
                                "tax_classes", 4L,
                                "tenants", 3L,
                                "distributors", 4L,
                                "customers", 5L,
                                "credit_accounts", 4L,
                                "versions", 3L));
        assertThat(
                        jdbc.queryForList(
                                "SELECT tenant_id FROM config_version"
                                        + " WHERE version = 1 AND is_current ORDER BY tenant_id",
                                String.class))
                .isEqualTo(List.of("CO-MARCA-A", "CO-MARCA-B", "EC-MARCA-A"));
    }

    @Test
    @DisplayName(
            "Los cupos cargados son los de las semillas y los tenderos de contado no tienen cuenta")
    void loadsSeededCreditAccounts() {
        assertThat(
                        jdbc.queryForList(
                                "SELECT tenant_id || '/' || customer_id || '/' || distributor_id"
                                        + " || '=' || available FROM credit_account ORDER BY 1",
                                String.class))
                .containsExactly(
                        "CO-MARCA-A/CLI-CREDITO-ALTO/DIST-NORTE=1000000.0000",
                        "CO-MARCA-A/CLI-CREDITO-BAJO/DIST-NORTE=50000.0000",
                        "CO-MARCA-A/CLI-CREDITO-BAJO/DIST-SUR=100000.0000",
                        "EC-MARCA-A/CLI-EC-01/DIST-EC-01=500.0000");
    }

    @Test
    @DisplayName("El cupo inicial es a la vez el límite y el disponible")
    void creditLimitStartsAsAvailable() {
        var account =
                credits.find(new CreditKey("CO-MARCA-A", "CLI-CREDITO-BAJO", "DIST-SUR"))
                        .orElseThrow();

        assertThat(account.creditLimit().amount()).isEqualTo("100000");
        assertThat(account.available().amount()).isEqualTo("100000");
    }

    @Test
    @DisplayName("Cargar las semillas otra vez no duplica ni pisa los datos existentes")
    void reloadingKeepsExistingData() {
        var key = new CreditKey("CO-MARCA-A", "CLI-CREDITO-BAJO", "DIST-NORTE");
        credits.consume(key, new BigDecimal("1000"));
        var before = jdbc.queryForMap(COUNTS);

        loadSeeds.handle();

        assertThat(jdbc.queryForMap(COUNTS)).isEqualTo(before);
        assertThat(credits.find(key).orElseThrow().available().amount()).isEqualTo("49000");
    }

    @Test
    @DisplayName("Un tenant nuevo en las semillas se carga aunque los demás ya existan")
    void loadsOnlyMissingTenants() {
        jdbc.update("DELETE FROM credit_account WHERE tenant_id = 'EC-MARCA-A'");
        jdbc.update("DELETE FROM customer WHERE tenant_id = 'EC-MARCA-A'");
        jdbc.update("DELETE FROM distributor WHERE tenant_id = 'EC-MARCA-A'");
        jdbc.update("DELETE FROM config_version WHERE tenant_id = 'EC-MARCA-A'");
        jdbc.update("DELETE FROM tenant WHERE id = 'EC-MARCA-A'");

        loadSeeds.handle();

        assertThat(configs.current("EC-MARCA-A").number()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM country", Long.class)).isEqualTo(2);
    }
}
