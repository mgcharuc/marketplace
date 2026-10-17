package com.marketplace.adapters.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.application.port.CreditAccountSeed;
import com.marketplace.application.port.DistributorSeed;
import com.marketplace.application.port.TenantSeed;
import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.ConfigInvalidException;
import com.marketplace.domain.config.ConfigValidator;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.ConfigViolation;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.config.ScaleRule;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.config.TenantConfigHistory;
import com.marketplace.domain.money.CurrencyConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonSeedSourceTest {

    private final JsonSeedSource seeds = new JsonSeedSource();

    private Map<String, Tenant> tenantsById() {
        return seeds.tenants().stream()
                .map(TenantSeed::tenant)
                .collect(Collectors.toMap(Tenant::id, Function.identity()));
    }

    private Map<String, ConfigVersion> versionsByTenant() {
        return seeds.versions().stream()
                .collect(Collectors.toMap(ConfigVersion::tenantId, Function.identity()));
    }

    private static void assertViolations(ThrowingCallable call, ConfigViolation... expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ConfigInvalidException.class,
                        error -> assertThat(error.violations()).containsExactly(expected));
    }

    @Test
    @DisplayName(
            "Carga los tres tenants con su país, distribuidores, tenderos y cuentas de crédito")
    void loadsTenants() {
        var seed =
                seeds.tenants().stream()
                        .filter(tenant -> tenant.tenant().id().equals("CO-MARCA-A"))
                        .findFirst()
                        .orElseThrow();

        assertThat(tenantsById()).containsOnlyKeys("CO-MARCA-A", "CO-MARCA-B", "EC-MARCA-A");
        assertThat(seed.tenant().orderNumberPrefix()).isEqualTo("CO-A");
        assertThat(seed.tenant().quoteTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(seed.tenant().idempotencyTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(seed.distributors())
                .containsExactly(
                        new DistributorSeed("DIST-NORTE", "Distribuidora Norte"),
                        new DistributorSeed("DIST-SUR", "Distribuidora Sur"));
        assertThat(seed.customers())
                .filteredOn(customer -> customer.id().equals("CLI-CREDITO-BAJO"))
                .singleElement()
                .satisfies(
                        customer ->
                                assertThat(customer.creditAccounts())
                                        .containsExactly(
                                                new CreditAccountSeed(
                                                        "DIST-NORTE", new BigDecimal("50000")),
                                                new CreditAccountSeed(
                                                        "DIST-SUR", new BigDecimal("100000"))));
        assertThat(seed.customers())
                .filteredOn(customer -> customer.id().equals("CLI-CONTADO"))
                .singleElement()
                .satisfies(customer -> assertThat(customer.creditAccounts()).isEmpty());
    }

    @Test
    @DisplayName(
            "Las dos marcas de Colombia comparten moneda, redondeo, política y tasa, con precios propios")
    void colombianBrandsShareCountryButNotPrices() {
        var tenants = tenantsById();
        var versions = versionsByTenant();
        var brandA = tenants.get("CO-MARCA-A");
        var brandB = tenants.get("CO-MARCA-B");
        var gaseosa = new Sku("GASEOSA-350");
        var productA = versions.get("CO-MARCA-A").product(gaseosa).orElseThrow();
        var productB = versions.get("CO-MARCA-B").product(gaseosa).orElseThrow();

        assertThat(brandA.country()).isEqualTo(brandB.country());
        assertThat(brandA.currency()).isEqualTo(new CurrencyConfig("COP", 0, RoundingMode.HALF_UP));
        assertThat(brandA.country().giftTaxPolicy()).isEqualTo(GiftTaxPolicy.ABSORBED_BY_SELLER);
        assertThat(brandA.taxRate(productA))
                .isEqualTo(brandB.taxRate(productB))
                .isEqualByComparingTo("0.19");
        assertThat(productA.listPrice()).isEqualByComparingTo("2000");
        assertThat(productB.listPrice()).isEqualByComparingTo("2100");
        assertThat(versions.get("CO-MARCA-B").rules()).isEmpty();
    }

    @Test
    @DisplayName("Ecuador usa dólares con dos decimales y sin impuesto en el obsequio")
    void ecuadorUsesDollars() {
        var ecuador = tenantsById().get("EC-MARCA-A");

        assertThat(ecuador.currency())
                .isEqualTo(new CurrencyConfig("USD", 2, RoundingMode.HALF_UP));
        assertThat(ecuador.country().giftTaxPolicy()).isEqualTo(GiftTaxPolicy.NONE);
        assertThat(
                        versionsByTenant()
                                .get("EC-MARCA-A")
                                .product(new Sku("AGUA-500"))
                                .orElseThrow()
                                .listPrice())
                .isEqualByComparingTo("0.35");
    }

    @Test
    @DisplayName(
            "Cada tenant tiene su versión 1 y todas cumplen las reglas de configuración de su país")
    void seedVersionsAreValid() {
        var tenants = tenantsById();
        var validator = new ConfigValidator();

        assertThat(seeds.versions())
                .hasSize(3)
                .allSatisfy(version -> assertThat(version.number()).isEqualTo(1));
        for (ConfigVersion version : seeds.versions()) {
            var country = tenants.get(version.tenantId()).country();
            assertThat(validator.validate(version, country)).isEmpty();
            assertThat(validator.validate(country)).isEmpty();
        }
    }

    @Test
    @DisplayName(
            "La versión 2 de prueba sube el precio, quita la escala y se puede publicar sobre la 1")
    void testVersionTwoIsPublishable() {
        var version = new JsonConfigCodec().decode(Resources.read("seed/co-marca-a.v2.json"));
        var history = new TenantConfigHistory(tenantsById().get("CO-MARCA-A"), 1);

        history.publish(version);

        assertThat(version.number()).isEqualTo(2);
        assertThat(version.product(new Sku("GASEOSA-350")).orElseThrow().listPrice())
                .isEqualByComparingTo("2500");
        assertThat(version.rules())
                .noneMatch(rule -> rule instanceof ScaleRule && rule.id().equals("ESC-GAS350"));
        assertThat(version.rules()).anyMatch(rule -> rule.id().equals("OBS-JUGO"));
        assertThat(history.currentNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un tenant que referencia un país inexistente hace fallar la carga")
    void rejectsUnknownCountry() {
        var source = new JsonSeedSource("seed/unknown-country");

        assertViolations(
                source::tenants, new ConfigViolation("UNKNOWN_COUNTRY", "tenants[0].country"));
    }

    @Test
    @DisplayName(
            "Informa campos desconocidos, obligatorios faltantes y tipos incorrectos de los tenants")
    void reportsTenantSchemaViolations() {
        var source = new JsonSeedSource("seed/invalid-schema");

        assertViolations(
                source::tenants,
                new ConfigViolation("UNKNOWN_FIELD", "countries[0].currency.symbol"),
                new ConfigViolation("REQUIRED", "countries[0].taxClasses[0].rate"),
                new ConfigViolation("INVALID_VALUE", "tenants[0].quoteTtlSeconds"));
    }

    @Test
    @DisplayName("Rechaza una tasa de impuesto del país fuera de 0 a 1")
    void rejectsInvalidCountryRate() {
        var source = new JsonSeedSource("seed/invalid-rate");

        assertViolations(source::tenants, new ConfigViolation("TAX_RATE_INVALID", "IVA19"));
    }

    @Test
    @DisplayName(
            "Una versión inicial que incumple las reglas de configuración hace fallar la carga")
    void rejectsInvalidInitialVersion() {
        var source = new JsonSeedSource("seed/invalid-version");

        assertViolations(
                source::versions, new ConfigViolation("COMBO_PRICE_NOT_BELOW_LIST", "CMB-CARO"));
    }

    @Test
    @DisplayName("Rechaza una versión inicial de otro tenant o con un número distinto de 1")
    void rejectsVersionFileOfAnotherTenant() {
        var source = new JsonSeedSource("seed/wrong-tenant");

        assertViolations(
                source::versions,
                new ConfigViolation("INVALID_VALUE", "seed/wrong-tenant/co-marca-z.v1.json"));
    }

    @Test
    @DisplayName("Rechaza países o tenants repetidos")
    void rejectsDuplicatedCountriesAndTenants() {
        assertViolations(
                new JsonSeedSource("seed/duplicate-country")::tenants,
                new ConfigViolation("INVALID_VALUE", "countries[1].code"));
        assertViolations(
                new JsonSeedSource("seed/duplicate-tenant")::tenants,
                new ConfigViolation("INVALID_VALUE", "tenants[1].id"));
    }

    @Test
    @DisplayName("Rechaza una cuenta de crédito con un distribuidor que no es del tenant")
    void rejectsCreditAccountWithForeignDistributor() {
        assertViolations(
                new JsonSeedSource("seed/foreign-distributor")::tenants,
                new ConfigViolation("INVALID_VALUE", "tenants[0]"));
    }

    @Test
    @DisplayName("Falla si un tenant no tiene su versión inicial")
    void rejectsMissingInitialVersion() {
        var source = new JsonSeedSource("seed/missing-version");

        assertViolations(
                source::versions,
                new ConfigViolation("REQUIRED", "seed/missing-version/co-marca-z.v1.json"));
    }
}
