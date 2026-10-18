package com.marketplace.domain.config;

import static com.marketplace.domain.config.ConfigFixtures.CO;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaAProducts;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaARules;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaAWith;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaAWithRule;
import static com.marketplace.domain.config.ConfigFixtures.combo;
import static com.marketplace.domain.config.ConfigFixtures.component;
import static com.marketplace.domain.config.ConfigFixtures.gift;
import static com.marketplace.domain.config.ConfigFixtures.product;
import static com.marketplace.domain.config.ConfigFixtures.scale;
import static com.marketplace.domain.config.ConfigFixtures.taxClass;
import static com.marketplace.domain.config.ConfigFixtures.tier;
import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.domain.catalog.Sku;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigValidatorTest {

    private final ConfigValidator validator = new ConfigValidator();

    private List<ConfigViolation> violationsOf(ConfigVersion version) {
        return validator.validate(version, CO);
    }

    private static ConfigViolation violation(String code, String ref) {
        return new ConfigViolation(code, ref);
    }

    @Test
    @DisplayName("La versión de la semilla no tiene violaciones")
    void seedVersionIsValid() {
        assertThat(violationsOf(coMarcaA(1))).isEmpty();
    }

    private static List<String> pipelineViolations(Stage... stages) {
        return new ConfigValidator()
                        .validate(
                                new ConfigVersion(
                                        "CO-MARCA-A",
                                        1,
                                        List.of(stages),
                                        coMarcaAProducts(),
                                        coMarcaARules()),
                                CO)
                        .stream()
                        .map(violation -> violation.code() + " " + violation.ref())
                        .toList();
    }

    @Test
    @DisplayName("Acepta un pipeline con las etapas comerciales reordenadas o sin alguna de ellas")
    void acceptsReorderedCommercialStages() {
        assertThat(
                        pipelineViolations(
                                Stage.SCALE, Stage.COMBO, Stage.GIFT, Stage.TAX, Stage.CREDIT))
                .isEmpty();
        assertThat(pipelineViolations(Stage.SCALE, Stage.TAX, Stage.CREDIT)).isEmpty();
    }

    @Test
    @DisplayName("Rechaza un pipeline con impuestos antes de una etapa comercial")
    void rejectsTaxBeforeCommercialStage() {
        assertThat(pipelineViolations(Stage.COMBO, Stage.TAX, Stage.SCALE, Stage.CREDIT))
                .containsExactly("PIPELINE_TAX_BEFORE_COMMERCIAL pipeline");
        assertThat(
                        pipelineViolations(
                                Stage.COMBO, Stage.SCALE, Stage.TAX, Stage.GIFT, Stage.CREDIT))
                .containsExactly("PIPELINE_TAX_BEFORE_COMMERCIAL pipeline");
    }

    @Test
    @DisplayName("Rechaza un pipeline en el que el crédito no es la última etapa")
    void rejectsCreditNotLast() {
        assertThat(
                        pipelineViolations(
                                Stage.COMBO, Stage.SCALE, Stage.GIFT, Stage.CREDIT, Stage.TAX))
                .containsExactly("PIPELINE_CREDIT_NOT_LAST pipeline");
    }

    @Test
    @DisplayName("Rechaza un pipeline con una etapa repetida")
    void rejectsDuplicateStage() {
        assertThat(
                        pipelineViolations(
                                Stage.COMBO,
                                Stage.GIFT,
                                Stage.SCALE,
                                Stage.GIFT,
                                Stage.TAX,
                                Stage.CREDIT))
                .containsExactly("PIPELINE_DUPLICATE_STAGE pipeline");
    }

    @Test
    @DisplayName("Rechaza un pipeline sin impuestos o sin crédito")
    void rejectsMissingStage() {
        assertThat(pipelineViolations(Stage.COMBO, Stage.SCALE, Stage.CREDIT))
                .containsExactly("PIPELINE_MISSING_STAGE pipeline");
        assertThat(pipelineViolations(Stage.COMBO, Stage.SCALE, Stage.TAX))
                .containsExactly("PIPELINE_MISSING_STAGE pipeline");
    }

    @Test
    @DisplayName(
            "Con un obsequio por monto, rechaza un pipeline que evalúa el obsequio antes de un descuento")
    void rejectsNetAmountGiftBeforeDiscounts() {
        var byAmount =
                new GiftRule(
                        "OBS-JUGO",
                        "Jugo por monto",
                        10,
                        null,
                        null,
                        new GiftCondition(
                                ConditionKind.NET_AMOUNT,
                                Scope.sku("GASEOSA-350"),
                                new BigDecimal("20000")),
                        new GiftReward(new Sku("JUGO-1L"), 1),
                        true);
        var giftFirst = List.of(Stage.COMBO, Stage.GIFT, Stage.SCALE, Stage.TAX, Stage.CREDIT);
        var rules = new ArrayList<>(coMarcaARules());
        rules.removeIf(rule -> rule.id().equals("OBS-JUGO"));
        rules.add(byAmount);

        assertThat(
                        validator.validate(
                                new ConfigVersion(
                                        "CO-MARCA-A", 1, giftFirst, coMarcaAProducts(), rules),
                                CO))
                .containsExactly(violation("PIPELINE_GIFT_NET_AMOUNT_ORDER", "OBS-JUGO"));
        assertThat(
                        violationsOf(
                                new ConfigVersion(
                                        "CO-MARCA-A",
                                        1,
                                        giftFirst,
                                        coMarcaAProducts(),
                                        coMarcaARules())))
                .isEmpty();
    }

    @Test
    @DisplayName("Un pipeline vacío informa las etapas que faltan")
    void reportsEmptyPipeline() {
        assertThat(pipelineViolations()).containsExactly("PIPELINE_MISSING_STAGE pipeline");
    }

    @Test
    @DisplayName(
            "Rechaza tramos de escala vacíos, desde cero, no crecientes o con porcentaje fuera de rango")
    void rejectsInvalidTiers() {
        var gaseosa = Scope.sku("GASEOSA-350");
        var rules =
                List.<Rule>of(
                        scale("ESC-VACIA", 30, gaseosa),
                        scale("ESC-CERO", 31, gaseosa, tier(0, "5")),
                        scale("ESC-REPETIDO", 32, gaseosa, tier(10, "5"), tier(10, "10")),
                        scale("ESC-DECRECE", 33, gaseosa, tier(20, "5"), tier(10, "10")),
                        scale("ESC-SIN-PORC", 34, gaseosa, tier(10, "0")),
                        scale("ESC-CIEN", 35, gaseosa, tier(10, "100")));

        assertThat(violationsOf(coMarcaAWith(coMarcaAProducts(), rules)))
                .containsExactly(
                        violation("SCALE_INVALID_TIERS", "ESC-VACIA"),
                        violation("SCALE_INVALID_TIERS", "ESC-CERO"),
                        violation("SCALE_INVALID_TIERS", "ESC-REPETIDO"),
                        violation("SCALE_INVALID_TIERS", "ESC-DECRECE"),
                        violation("SCALE_INVALID_TIERS", "ESC-SIN-PORC"),
                        violation("SCALE_INVALID_TIERS", "ESC-CIEN"));
    }

    @Test
    @DisplayName("Acepta porcentajes con decimales dentro del rango abierto de 0 a 100")
    void acceptsFractionalPercentInsideRange() {
        var rule =
                scale("ESC-GAS350", 10, Scope.sku("GASEOSA-350"), tier(1, "0.5"), tier(5, "99.9"));

        assertThat(violationsOf(coMarcaAWithRule(rule))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "15400", "16000"})
    @DisplayName("Rechaza un precio de combo en cero o que no quede por debajo del valor de lista")
    void rejectsComboPriceNotBelowList(String price) {
        var rule =
                combo(
                        "CMB-FIESTA",
                        10,
                        price,
                        component("GASEOSA-1500", 2),
                        component("SNACK-PAPAS", 3));

        assertThat(violationsOf(coMarcaAWithRule(rule)))
                .containsExactly(violation("COMBO_PRICE_NOT_BELOW_LIST", "CMB-FIESTA"));
    }

    @Test
    @DisplayName("Acepta un combo una unidad mínima por debajo del valor de lista")
    void acceptsComboPriceJustBelowList() {
        var rule =
                combo(
                        "CMB-FIESTA",
                        10,
                        "15399",
                        component("GASEOSA-1500", 2),
                        component("SNACK-PAPAS", 3));

        assertThat(violationsOf(coMarcaAWithRule(rule))).isEmpty();
    }

    @Test
    @DisplayName("Rechaza un producto cuya clase tributaria no existe en el país del tenant")
    void rejectsUnknownTaxClass() {
        var products = new ArrayList<>(coMarcaAProducts());
        products.add(product("AGUA-500", "AGUAS", "IVA15", "1000"));

        assertThat(violationsOf(coMarcaAWith(products, coMarcaARules())))
                .containsExactly(violation("RULE_UNKNOWN_REFERENCE", "AGUA-500"));
    }

    @Test
    @DisplayName("Rechaza reglas que referencian SKU o familias que no están en la versión")
    void rejectsRulesWithUnknownReferences() {
        var rules =
                List.<Rule>of(
                        scale("ESC-AGUAS", 30, Scope.family("AGUAS"), tier(5, "3")),
                        combo(
                                "CMB-AGUA",
                                30,
                                "1000",
                                component("AGUA-500", 2),
                                component("JUGO-1L", 1)),
                        gift("OBS-AGUA", 30, Scope.sku("GASEOSA-350"), 12, "AGUA-500"));

        assertThat(violationsOf(coMarcaAWith(coMarcaAProducts(), rules)))
                .containsExactly(
                        violation("RULE_UNKNOWN_REFERENCE", "ESC-AGUAS"),
                        violation("RULE_UNKNOWN_REFERENCE", "CMB-AGUA"),
                        violation("RULE_UNKNOWN_REFERENCE", "OBS-AGUA"));
    }

    @Test
    @DisplayName("Rechaza tasas del país fuera de 0 a 1 o con más de cuatro decimales")
    void rejectsInvalidTaxRates() {
        var country =
                new Country(
                        "CO",
                        CO.currency(),
                        GiftTaxPolicy.NONE,
                        List.of(
                                taxClass("IVA19", "0.19"),
                                taxClass("NEGATIVA", "-0.01"),
                                taxClass("MAYOR", "1.01"),
                                taxClass("DECIMALES", "0.12345"),
                                taxClass("CEROS", "0.19000"),
                                taxClass("CUATRO", "0.1900"),
                                taxClass("EXENTO", "0"),
                                taxClass("TOTAL", "1")));

        assertThat(validator.validate(country))
                .containsExactly(
                        violation("TAX_RATE_INVALID", "NEGATIVA"),
                        violation("TAX_RATE_INVALID", "MAYOR"),
                        violation("TAX_RATE_INVALID", "DECIMALES"),
                        violation("TAX_RATE_INVALID", "CEROS"));
    }

    @Test
    @DisplayName("Rechaza precios de lista negativos o con más decimales que la moneda del país")
    void rejectsInvalidListPrices() {
        var products = new ArrayList<>(coMarcaAProducts());
        products.add(product("AGUA-NEG", "AGUAS", "IVA19", "-1"));
        products.add(product("AGUA-DEC", "AGUAS", "IVA19", "1000.5"));
        products.add(product("AGUA-CERO", "AGUAS", "IVA19", "0"));

        assertThat(violationsOf(coMarcaAWith(products, coMarcaARules())))
                .containsExactly(
                        violation("PRICE_INVALID_SCALE", "AGUA-NEG"),
                        violation("PRICE_INVALID_SCALE", "AGUA-DEC"));
    }

    @Test
    @DisplayName("Rechaza un precio de combo con más decimales que la moneda del país")
    void rejectsComboPriceWithInvalidScale() {
        var rule =
                combo(
                        "CMB-FIESTA",
                        10,
                        "14000.5",
                        component("GASEOSA-1500", 2),
                        component("SNACK-PAPAS", 3));

        assertThat(violationsOf(coMarcaAWithRule(rule)))
                .containsExactly(violation("PRICE_INVALID_SCALE", "CMB-FIESTA"));
    }

    @Test
    @DisplayName(
            "Rechaza dos reglas del mismo tipo y prioridad que comparten un SKU directo o por familia")
    void rejectsAmbiguousPriorities() {
        var rules = new ArrayList<>(coMarcaARules());
        rules.add(scale("ESC-GASEOSAS", 10, Scope.family("GASEOSAS"), tier(10, "3")));
        rules.add(combo("CMB-PAPAS", 20, "5000", component("SNACK-PAPAS", 4)));

        assertThat(violationsOf(coMarcaAWith(coMarcaAProducts(), rules)))
                .containsExactly(
                        violation("RULE_PRIORITY_AMBIGUOUS", "CMB-DESAYUNO,CMB-PAPAS"),
                        violation("RULE_PRIORITY_AMBIGUOUS", "ESC-GAS350,ESC-GASEOSAS"));
    }

    @Test
    @DisplayName("Dos reglas del mismo tipo y prioridad sobre SKU distintos no compiten")
    void rulesWithoutSharedSkusDoNotCompete() {
        var rules = new ArrayList<>(coMarcaARules());
        rules.add(scale("ESC-JUGO", 10, Scope.sku("JUGO-1L"), tier(5, "3")));
        rules.add(scale("ESC-SNACKS", 10, Scope.family("SNACKS"), tier(5, "3")));

        assertThat(violationsOf(coMarcaAWith(coMarcaAProducts(), rules))).isEmpty();
    }

    @Test
    @DisplayName("Reglas de distinto tipo con la misma prioridad sobre el mismo SKU no compiten")
    void rulesOfDifferentTypeDoNotCompete() {
        assertThat(coMarcaARules())
                .filteredOn(rule -> rule.priority() == 10)
                .extracting(Rule::id)
                .contains("ESC-GAS350", "OBS-JUGO");
        assertThat(violationsOf(coMarcaA(1))).isEmpty();
    }

    @Test
    @DisplayName("Rechaza una vigencia cuyo inicio no es anterior al fin")
    void rejectsInvalidValidity() {
        var instant = Instant.parse("2026-10-17T05:00:00Z");
        var rule =
                new ScaleRule(
                        "ESC-GAS350",
                        "Escala",
                        10,
                        instant,
                        instant,
                        Scope.sku("GASEOSA-350"),
                        ScaleMode.MARGINAL,
                        List.of(tier(10, "5")));
        var open =
                new ScaleRule(
                        "ESC-GAS1500",
                        "Escala",
                        20,
                        instant,
                        null,
                        Scope.sku("GASEOSA-1500"),
                        ScaleMode.MARGINAL,
                        List.of(tier(3, "5")));
        var rules = new ArrayList<>(coMarcaARules());
        rules.removeIf(existing -> existing instanceof ScaleRule);
        rules.add(rule);
        rules.add(open);

        assertThat(violationsOf(coMarcaAWith(coMarcaAProducts(), rules)))
                .containsExactly(violation("RULE_INVALID_VALIDITY", "ESC-GAS350"));
    }

    @Test
    @DisplayName("Informa todas las violaciones juntas, ordenadas por regla")
    void reportsAllViolationsSortedByRule() {
        var products = new ArrayList<>(coMarcaAProducts());
        products.add(product("AGUA-500", "AGUAS", "IVA15", "-1"));
        var rules = new ArrayList<>(coMarcaARules());
        rules.add(scale("ESC-AGUA", 30, Scope.sku("AGUA-500")));
        rules.add(
                new ComboRule(
                        "CMB-CARO",
                        "Combo caro",
                        30,
                        null,
                        null,
                        List.of(component("JUGO-1L", 1)),
                        new BigDecimal("4000")));

        assertThat(violationsOf(coMarcaAWith(products, rules)))
                .extracting(ConfigViolation::code)
                .containsExactly(
                        "SCALE_INVALID_TIERS",
                        "COMBO_PRICE_NOT_BELOW_LIST",
                        "RULE_UNKNOWN_REFERENCE",
                        "PRICE_INVALID_SCALE");
    }

    @Test
    @DisplayName("Las violaciones del pipeline van antes que las de las reglas")
    void reportsPipelineViolationsFirst() {
        var rules = new ArrayList<>(coMarcaARules());
        rules.add(scale("ESC-VACIA", 30, Scope.sku("GASEOSA-350")));
        var version =
                new ConfigVersion(
                        "CO-MARCA-A",
                        1,
                        List.of(Stage.CREDIT, Stage.COMBO),
                        coMarcaAProducts(),
                        rules);

        assertThat(violationsOf(version))
                .extracting(ConfigViolation::code)
                .containsExactly(
                        "PIPELINE_CREDIT_NOT_LAST",
                        "PIPELINE_MISSING_STAGE",
                        "SCALE_INVALID_TIERS");
    }
}
