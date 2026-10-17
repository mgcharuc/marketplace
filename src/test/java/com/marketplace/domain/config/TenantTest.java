package com.marketplace.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.money.CurrencyConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantTest {

    private static final Country CO =
            new Country(
                    "CO",
                    new CurrencyConfig("COP", 0, RoundingMode.HALF_UP),
                    GiftTaxPolicy.ABSORBED_BY_SELLER,
                    List.of(new TaxClass("IVA19", new BigDecimal("0.19"))));

    private static Product product(String price) {
        return new Product(
                new Sku("GASEOSA-350"), "Gaseosa", "GASEOSAS", "IVA19", new BigDecimal(price));
    }

    private static Tenant tenant(String id, String prefix) {
        return new Tenant(id, CO, "Marca", prefix, Duration.ofMinutes(15), Duration.ofHours(24));
    }

    @Test
    @DisplayName(
            "Dos marcas del mismo país resuelven la misma moneda, política y tasa con precios propios")
    void brandsOfSameCountryShareFiscalConfig() {
        var brandA = tenant("CO-MARCA-A", "CO-A");
        var brandB = tenant("CO-MARCA-B", "CO-B");
        var priceA = product("2000");
        var priceB = product("2100");

        assertThat(brandA.currency()).isEqualTo(brandB.currency());
        assertThat(brandA.country().giftTaxPolicy()).isEqualTo(GiftTaxPolicy.ABSORBED_BY_SELLER);
        assertThat(brandA.taxRate(priceA)).isEqualTo(brandB.taxRate(priceB)).isEqualTo("0.19");
        assertThat(priceA.listPrice()).isNotEqualByComparingTo(priceB.listPrice());
    }

    @Test
    @DisplayName("La tasa de un producto sale de la clase tributaria del país del tenant")
    void resolvesTaxRateFromCountry() {
        var ecuador =
                new Country(
                        "EC",
                        new CurrencyConfig("USD", 2, RoundingMode.HALF_UP),
                        GiftTaxPolicy.NONE,
                        List.of(new TaxClass("IVA19", new BigDecimal("0.15"))));
        var tenant =
                new Tenant(
                        "EC-MARCA-A",
                        ecuador,
                        "Marca A",
                        "EC-A",
                        Duration.ofMinutes(15),
                        Duration.ofHours(24));

        assertThat(tenant.taxRate(product("2000"))).isEqualTo("0.15");
    }

    @Test
    @DisplayName("Falla si la clase tributaria del producto no existe en el país")
    void rejectsUnknownTaxClass() {
        var product = new Product(new Sku("AGUA-500"), "Agua", "AGUAS", "IVA15", BigDecimal.ONE);

        assertThatThrownBy(() -> tenant("CO-MARCA-A", "CO-A").taxRate(product))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza un identificador o un prefijo con formato inválido")
    void rejectsInvalidIdentifiers() {
        assertThatThrownBy(() -> tenant("CO MARCA", "CO-A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tenant("CO-MARCA-A", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza tiempos de vigencia en cero o negativos")
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(
                        () ->
                                new Tenant(
                                        "CO-MARCA-A",
                                        CO,
                                        "Marca A",
                                        "CO-A",
                                        Duration.ZERO,
                                        Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new Tenant(
                                        "CO-MARCA-A",
                                        CO,
                                        "Marca A",
                                        "CO-A",
                                        Duration.ofMinutes(15),
                                        Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza una marca vacía o un país nulo")
    void rejectsMissingBrandOrCountry() {
        assertThatThrownBy(
                        () ->
                                new Tenant(
                                        "CO-MARCA-A",
                                        CO,
                                        " ",
                                        "CO-A",
                                        Duration.ofMinutes(15),
                                        Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new Tenant(
                                        "CO-MARCA-A",
                                        null,
                                        "Marca A",
                                        "CO-A",
                                        Duration.ofMinutes(15),
                                        Duration.ofHours(24)))
                .isInstanceOf(NullPointerException.class);
    }
}
