package com.marketplace.adapters.persistence.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.application.port.CustomerRepository;
import com.marketplace.application.port.DistributorRepository;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.config.TaxClass;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.support.IntegrationTest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JpaTenantRepositoryIT {

    @Autowired TenantRepository tenants;
    @Autowired DistributorRepository distributors;
    @Autowired CustomerRepository customers;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("El tenant se lee con la moneda, el redondeo, la política y las tasas de su país")
    void readsTenantWithItsCountry() {
        var tenant = tenants.find("CO-MARCA-A").orElseThrow();

        assertThat(tenant.brand()).isEqualTo("Marca A");
        assertThat(tenant.orderNumberPrefix()).isEqualTo("CO-A");
        assertThat(tenant.quoteTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(tenant.idempotencyTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(tenant.currency()).isEqualTo(new CurrencyConfig("COP", 0, RoundingMode.HALF_UP));
        assertThat(tenant.country().giftTaxPolicy()).isEqualTo(GiftTaxPolicy.ABSORBED_BY_SELLER);
        assertThat(tenant.country().taxClasses())
                .containsExactly(
                        new TaxClass("IVA19", new BigDecimal("0.19")),
                        new TaxClass("IVA5", new BigDecimal("0.05")));
    }

    @Test
    @DisplayName("Las tasas se leen como se escriben en la configuración, también la tasa cero")
    void readsRatesAsConfigured() {
        var tenant = tenants.find("EC-MARCA-A").orElseThrow();

        assertThat(tenant.country().taxClass("IVA0").orElseThrow().rate()).isEqualTo("0.00");
        assertThat(tenant.country().taxClass("IVA15").orElseThrow().rate()).isEqualTo("0.15");
    }

    @Test
    @DisplayName(
            "Un cambio de tasa en el país se ve en la siguiente lectura de cualquiera de sus tenants")
    void readsCountryChangesWithoutCache() {
        tenants.find("CO-MARCA-A").orElseThrow();

        jdbc.update(
                "UPDATE country_tax_class SET rate = 0.21"
                        + " WHERE country_code = 'CO' AND code = 'IVA19'");

        assertThat(List.of("CO-MARCA-A", "CO-MARCA-B"))
                .allSatisfy(
                        tenantId ->
                                assertThat(
                                                tenants.find(tenantId)
                                                        .orElseThrow()
                                                        .country()
                                                        .taxClass("IVA19"))
                                        .hasValueSatisfying(
                                                taxClass ->
                                                        assertThat(taxClass.rate())
                                                                .isEqualTo("0.21")));
    }

    @Test
    @DisplayName("Buscar un tenant inexistente devuelve vacío")
    void unknownTenantIsEmpty() {
        assertThat(tenants.find("CO-MARCA-Z")).isEmpty();
    }

    @Test
    @DisplayName("Distribuidores y tenderos existen solo dentro de su tenant")
    void distributorsAndCustomersAreScopedToTheirTenant() {
        assertThat(distributors.exists("CO-MARCA-A", "DIST-NORTE")).isTrue();
        assertThat(distributors.exists("CO-MARCA-B", "DIST-NORTE")).isFalse();
        assertThat(distributors.exists("CO-MARCA-A", "DIST-B-01")).isFalse();
        assertThat(customers.exists("CO-MARCA-A", "CLI-CONTADO")).isTrue();
        assertThat(customers.exists("CO-MARCA-B", "CLI-CONTADO")).isFalse();
        assertThat(customers.exists("CO-MARCA-A", "CLI-B-01")).isFalse();
    }
}
