package com.marketplace.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.catalog.Sku;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigVersionTest {

    private static final List<Stage> PIPELINE =
            List.of(Stage.COMBO, Stage.SCALE, Stage.GIFT, Stage.TAX, Stage.CREDIT);

    private static Product product(String sku, String family, String price) {
        return new Product(new Sku(sku), sku, family, "IVA19", new BigDecimal(price));
    }

    private static ScaleRule scale(String id, Scope scope) {
        return new ScaleRule(
                id,
                id,
                10,
                null,
                null,
                scope,
                ScaleMode.MARGINAL,
                List.of(new Tier(10, new BigDecimal("5"))));
    }

    private static ConfigVersion version(List<Product> products, List<Rule> rules) {
        return new ConfigVersion("CO-MARCA-A", 1, PIPELINE, products, rules);
    }

    @Test
    @DisplayName("Las colecciones de una versión no se pueden modificar")
    void collectionsAreImmutable() {
        var products = new ArrayList<>(List.of(product("GASEOSA-350", "GASEOSAS", "2000")));
        var version = version(products, List.of());

        products.add(product("JUGO-1L", "JUGOS", "4000"));

        assertThat(version.products()).hasSize(1);
        assertThatThrownBy(() -> version.products().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> version.rules().add(scale("ESC-1", Scope.sku("GASEOSA-350"))))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> version.pipeline().remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Busca productos por SKU y por alcance de SKU o de familia")
    void findsProductsByScope() {
        var gaseosa350 = product("GASEOSA-350", "GASEOSAS", "2000");
        var gaseosa1500 = product("GASEOSA-1500", "GASEOSAS", "5000");
        var jugo = product("JUGO-1L", "JUGOS", "4000");
        var version = version(List.of(gaseosa350, gaseosa1500, jugo), List.of());

        assertThat(version.product(new Sku("JUGO-1L"))).hasValue(jugo);
        assertThat(version.product(new Sku("AGUA-500"))).isEmpty();
        assertThat(version.productsIn(Scope.family("GASEOSAS")))
                .containsExactly(gaseosa350, gaseosa1500);
        assertThat(version.productsIn(Scope.sku("JUGO-1L"))).containsExactly(jugo);
        assertThat(version.productsIn(Scope.family("SNACKS"))).isEmpty();
    }

    @Test
    @DisplayName("Rechaza productos con el mismo SKU o reglas con el mismo id")
    void rejectsDuplicates() {
        var product = product("GASEOSA-350", "GASEOSAS", "2000");
        var rule = scale("ESC-1", Scope.sku("GASEOSA-350"));

        assertThatThrownBy(() -> version(List.of(product, product), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> version(List.of(product), List.of(rule, rule)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza un número de versión menor que 1")
    void rejectsNonPositiveNumber() {
        assertThatThrownBy(() -> new ConfigVersion("CO-MARCA-A", 0, PIPELINE, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza un combo que repite un SKU en sus componentes")
    void rejectsComboWithRepeatedSku() {
        var components =
                List.of(
                        new ComboComponent(new Sku("GASEOSA-350"), 1),
                        new ComboComponent(new Sku("GASEOSA-350"), 1));

        assertThatThrownBy(
                        () ->
                                new ComboRule(
                                        "CMB-DOBLE",
                                        "Doble",
                                        10,
                                        null,
                                        null,
                                        components,
                                        BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza componentes de combo, obsequios y umbrales con cantidades menores que 1")
    void rejectsNonPositiveRuleQuantities() {
        var sku = new Sku("JUGO-1L");

        assertThatThrownBy(() -> new ComboComponent(sku, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GiftReward(sku, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new GiftCondition(
                                        ConditionKind.QUANTITY,
                                        Scope.sku("GASEOSA-350"),
                                        BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Una condición por cantidad exige un umbral entero")
    void quantityConditionRequiresWholeThreshold() {
        assertThatThrownBy(
                        () ->
                                new GiftCondition(
                                        ConditionKind.QUANTITY,
                                        Scope.sku("GASEOSA-350"),
                                        new BigDecimal("12.5")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
