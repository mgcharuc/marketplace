package com.marketplace.pricing;

import static com.marketplace.domain.config.ConfigFixtures.CO_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.EC_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.PIPELINE;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaAProducts;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaARules;
import static com.marketplace.domain.config.ConfigFixtures.ecMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.product;
import static com.marketplace.domain.config.ConfigFixtures.scale;
import static com.marketplace.domain.config.ConfigFixtures.tier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.catalog.Quantity;
import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.Product;
import com.marketplace.domain.config.Rule;
import com.marketplace.domain.config.ScaleMode;
import com.marketplace.domain.config.ScaleRule;
import com.marketplace.domain.config.Scope;
import com.marketplace.domain.config.Stage;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.config.Tier;
import com.marketplace.domain.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PricingEngineTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final PricingEngine engine = new PricingEngine();

    private static Cart cart(Object... skuAndQuantity) {
        var lines = new ArrayList<CartLine>();
        for (int i = 0; i < skuAndQuantity.length; i += 2) {
            lines.add(
                    new CartLine(
                            new Sku((String) skuAndQuantity[i]),
                            new Quantity((Integer) skuAndQuantity[i + 1])));
        }
        return new Cart(lines);
    }

    private Breakdown price(ConfigVersion version, Tenant tenant, Cart cart) {
        var breakdown =
                engine.price(
                        new PricingInput(version, tenant, cart, CreditAvailability.none(), NOW));
        BreakdownInvariants.check(breakdown);
        return breakdown;
    }

    private static ConfigVersion ecVersion(List<Product> products, List<Rule> rules) {
        return new ConfigVersion("EC-MARCA-A", 1, PIPELINE, products, rules);
    }

    private static ScaleRule scaleOn(
            String id, int priority, ScaleMode mode, Scope scope, Tier... tiers) {
        return new ScaleRule(id, id, priority, null, null, scope, mode, List.of(tiers));
    }

    private Breakdown priceCo(Cart cart) {
        return price(coMarcaA(1), CO_MARCA_A, cart);
    }

    private static List<String> amounts(List<? extends Object> items) {
        return items.stream()
                .map(
                        item ->
                                switch (item) {
                                    case RuleAmount ruleAmount ->
                                            ruleAmount.ruleId()
                                                    + "="
                                                    + ruleAmount.amount().amount().toPlainString();
                                    case LineAmount lineAmount ->
                                            lineAmount.lineId()
                                                    + "="
                                                    + lineAmount.amount().amount().toPlainString();
                                    case TierAmount tier ->
                                            tier.fromQty()
                                                    + ":"
                                                    + tier.units()
                                                    + "="
                                                    + tier.amount().amount().toPlainString();
                                    default -> item.toString();
                                })
                .toList();
    }

    private static String plain(Money money) {
        return money.amount().toPlainString();
    }

    @Test
    @DisplayName(
            "Sin descuentos, el impuesto de cada línea se calcula sobre su bruto con la tasa de su clase")
    void taxesEachLineWithItsRate() {
        var breakdown = priceCo(cart("GASEOSA-350", 9, "JUGO-1L", 1));

        assertThat(breakdown.discounts()).isEmpty();
        assertThat(breakdown.lines())
                .extracting(line -> line.lineId() + " " + plain(line.taxAmount()))
                .containsExactly("L1 3420", "L2 200");
        assertThat(breakdown.lines().get(1).taxRate()).isEqualByComparingTo("0.05");
        assertThat(plain(breakdown.totals().total())).isEqualTo("25620");
    }

    @Test
    @DisplayName(
            "El combo aparece como un solo descuento y se reparte entre sus líneas solo como base gravable")
    void appliesComboAsSingleDiscount() {
        var breakdown = priceCo(cart("GASEOSA-1500", 3, "SNACK-PAPAS", 3));

        assertThat(breakdown.discounts()).singleElement().isInstanceOf(ComboDiscount.class);
        var combo = (ComboDiscount) breakdown.discounts().get(0);
        assertThat(combo.ruleId()).isEqualTo("CMB-FIESTA");
        assertThat(combo.applications()).isEqualTo(1);
        assertThat(plain(combo.listValue())).isEqualTo("15400");
        assertThat(plain(combo.amount())).isEqualTo("1400");
        assertThat(amounts(combo.lineAmounts())).containsExactly("L1=909", "L2=491");
        assertThat(breakdown.lines())
                .allSatisfy(line -> assertThat(line.scaleDiscounts()).isEmpty());
        assertThat(amounts(breakdown.lines().get(0).taxBaseAllocations()))
                .containsExactly("CMB-FIESTA=909");
        assertThat(plain(breakdown.totals().total())).isEqualTo("22610");
    }

    @Test
    @DisplayName(
            "El combo se aplica tantas veces como conjuntos completos y lo que sobra sigue elegible")
    void appliesComboSeveralTimes() {
        var breakdown = priceCo(cart("GASEOSA-1500", 5, "SNACK-PAPAS", 6));

        var combo = (ComboDiscount) breakdown.discounts().get(0);
        assertThat(combo.applications()).isEqualTo(2);
        assertThat(plain(combo.amount())).isEqualTo("2800");
        assertThat(amounts(combo.lineAmounts())).containsExactly("L1=1818", "L2=982");
        assertThat(breakdown.discounts()).hasSize(1);
        assertThat(plain(breakdown.totals().total())).isEqualTo("39270");
    }

    @Test
    @DisplayName(
            "Dos combos que comparten un SKU se aplican por prioridad y el segundo usa lo que queda")
    void appliesCombosByPriority() {
        var breakdown = priceCo(cart("GASEOSA-1500", 2, "JUGO-1L", 2, "SNACK-PAPAS", 3));

        assertThat(breakdown.discounts())
                .extracting(AppliedDiscount::ruleId)
                .containsExactly("CMB-FIESTA");
        assertThat(plain(breakdown.totals().total())).isEqualTo("25060");
    }

    @Test
    @DisplayName("La escala marginal descuenta cada tramo solo sobre las unidades que caen en él")
    void appliesMarginalTiers() {
        var breakdown = priceCo(cart("GASEOSA-350", 25));

        var scale = (ScaleDiscount) breakdown.discounts().get(0);
        assertThat(plain(scale.amount())).isEqualTo("2200");
        assertThat(amounts(scale.tiers())).containsExactly("10:10=1000", "20:6=1200");
        assertThat(amounts(scale.lineAmounts())).containsExactly("L1=2200");
        assertThat(amounts(breakdown.lines().get(0).scaleDiscounts()))
                .containsExactly("ESC-GAS350=2200");
        assertThat(plain(breakdown.lines().get(0).taxAmount())).isEqualTo("9082");
    }

    @Test
    @DisplayName("Una cantidad justo debajo del primer tramo no genera descuento y la del borde sí")
    void respectsTierBoundary() {
        assertThat(priceCo(cart("GASEOSA-350", 9)).discounts()).isEmpty();
        var atBoundary = (ScaleDiscount) priceCo(cart("GASEOSA-350", 10)).discounts().get(0);
        assertThat(amounts(atBoundary.tiers())).containsExactly("10:1=100");
    }

    @Test
    @DisplayName("La escala cuenta posiciones solo sobre las unidades que el combo no consumió")
    void countsScalePositionsOnlyOnEligibleUnits() {
        var breakdown = priceCo(cart("GASEOSA-1500", 5, "SNACK-PAPAS", 3));

        assertThat(breakdown.discounts())
                .extracting(AppliedDiscount::ruleId)
                .containsExactly("CMB-FIESTA", "ESC-GAS1500");
        var scale = (ScaleDiscount) breakdown.discounts().get(1);
        assertThat(amounts(scale.tiers())).containsExactly("3:1=250");
        assertThat(plain(breakdown.lines().get(0).taxAmount())).isEqualTo("4530");
        assertThat(plain(breakdown.totals().total())).isEqualTo("34213");
    }

    @Test
    @DisplayName(
            "Con todas las unidades, el tramo alcanzado descuenta todo y se redondea una vez por línea")
    void appliesAllUnitsModeInDollars() {
        var breakdown = price(ecMarcaA(), EC_MARCA_A, cart("AGUA-500", 7));

        var scale = (ScaleDiscount) breakdown.discounts().get(0);
        assertThat(amounts(scale.tiers())).containsExactly("5:7=0.07");
        assertThat(plain(breakdown.lines().get(0).taxableBase())).isEqualTo("2.38");
        assertThat(plain(breakdown.lines().get(0).taxAmount())).isEqualTo("0.36");
        assertThat(plain(breakdown.totals().total())).isEqualTo("2.74");
    }

    @Test
    @DisplayName("El residuo del reparto del combo en dólares va a la línea con mayor fracción")
    void allocatesComboRemainderInDollars() {
        var breakdown = price(ecMarcaA(), EC_MARCA_A, cart("AGUA-500", 3, "JUGO-EC-1L", 1));

        var combo = (ComboDiscount) breakdown.discounts().get(0);
        assertThat(amounts(combo.lineAmounts())).containsExactly("L1=0.14", "L2=0.16");
        assertThat(breakdown.discounts()).hasSize(1);
        assertThat(plain(breakdown.totals().total())).isEqualTo("2.14");
    }

    @Test
    @DisplayName(
            "La escala por familia ordena por precio descendente para que los tramos caigan en lo más barato")
    void ordersFamilyUnitsByPrice() {
        var rules = new ArrayList<Rule>(coMarcaARules());
        rules.removeIf(rule -> rule instanceof ScaleRule);
        rules.add(scale("ESC-FAM-GAS", 10, Scope.family("GASEOSAS"), tier(10, "5")));
        var version = new ConfigVersion("CO-MARCA-A", 1, PIPELINE, coMarcaAProducts(), rules);

        var breakdown = price(version, CO_MARCA_A, cart("GASEOSA-350", 6, "GASEOSA-1500", 6));

        var scale = (ScaleDiscount) breakdown.discounts().get(0);
        assertThat(amounts(scale.lineAmounts())).containsExactly("L1=300");
        assertThat(amounts(scale.tiers())).containsExactly("10:3=300");
        assertThat(breakdown.lines().get(1).scaleDiscounts()).isEmpty();
        assertThat(plain(breakdown.totals().total())).isEqualTo("49623");
    }

    @Test
    @DisplayName(
            "Las unidades al 0 % siguen elegibles para la siguiente escala y las descontadas no")
    void keepsUndiscountedUnitsEligible() {
        var rules =
                List.<Rule>of(
                        scale("ESC-A", 10, Scope.sku("GASEOSA-350"), tier(5, "5")),
                        scale("ESC-B", 20, Scope.sku("GASEOSA-350"), tier(1, "10")));
        var version = new ConfigVersion("CO-MARCA-A", 1, PIPELINE, coMarcaAProducts(), rules);

        var breakdown = price(version, CO_MARCA_A, cart("GASEOSA-350", 10));

        assertThat(amounts(breakdown.lines().get(0).scaleDiscounts()))
                .containsExactly("ESC-A=600", "ESC-B=800");
    }

    @Test
    @DisplayName("Una etapa que no está en el pipeline de la versión no se aplica")
    void ignoresStagesMissingFromPipeline() {
        var version =
                new ConfigVersion(
                        "CO-MARCA-A",
                        1,
                        List.of(Stage.SCALE, Stage.TAX, Stage.CREDIT),
                        coMarcaAProducts(),
                        coMarcaARules());

        var breakdown = price(version, CO_MARCA_A, cart("GASEOSA-1500", 3, "SNACK-PAPAS", 3));

        assertThat(breakdown.discounts()).noneMatch(discount -> discount instanceof ComboDiscount);
        assertThat(amounts(breakdown.lines().get(0).scaleDiscounts()))
                .containsExactly("ESC-GAS1500=250");
    }

    @Test
    @DisplayName("El mismo carrito da el mismo desglose aunque las reglas vengan en otro orden")
    void isDeterministic() {
        var cart = cart("GASEOSA-1500", 5, "SNACK-PAPAS", 3, "GASEOSA-350", 25);
        var reversed = new ArrayList<>(coMarcaARules());
        Collections.reverse(reversed);
        var sameRulesReversed =
                new ConfigVersion("CO-MARCA-A", 1, PIPELINE, coMarcaAProducts(), reversed);

        var first =
                new PricingEngine()
                        .price(
                                new PricingInput(
                                        coMarcaA(1),
                                        CO_MARCA_A,
                                        cart,
                                        CreditAvailability.none(),
                                        NOW));
        var second =
                new PricingEngine()
                        .price(
                                new PricingInput(
                                        sameRulesReversed,
                                        CO_MARCA_A,
                                        cart,
                                        CreditAvailability.none(),
                                        NOW));

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName(
            "Una escala que redondea a cero no aparece en el desglose, pero consume sus unidades")
    void zeroScaleConsumesUnitsWithoutEntry() {
        var version =
                ecVersion(
                        List.of(product("AGUA-500", "AGUAS", "IVA15", "0.35")),
                        List.of(
                                scaleOn(
                                        "ESC-MINIMA",
                                        10,
                                        ScaleMode.MARGINAL,
                                        Scope.sku("AGUA-500"),
                                        tier(1, "1")),
                                scaleOn(
                                        "ESC-POSTERIOR",
                                        20,
                                        ScaleMode.MARGINAL,
                                        Scope.sku("AGUA-500"),
                                        tier(1, "10"))));

        var breakdown = price(version, EC_MARCA_A, cart("AGUA-500", 1));

        assertThat(breakdown.discounts()).isEmpty();
        assertThat(breakdown.lines().get(0).scaleDiscounts()).isEmpty();
    }

    @Test
    @DisplayName(
            "Una línea cuyo descuento de escala redondea a cero no aparece en el reparto por línea")
    void omitsZeroLineAmounts() {
        var version =
                ecVersion(
                        List.of(
                                product("AGUA-500", "AGUAS", "IVA15", "0.35"),
                                product("AGUA-SORBO", "AGUAS", "IVA15", "0.01")),
                        List.of(
                                scaleOn(
                                        "ESC-AGUAS",
                                        10,
                                        ScaleMode.MARGINAL,
                                        Scope.family("AGUAS"),
                                        tier(1, "10"))));

        var breakdown = price(version, EC_MARCA_A, cart("AGUA-SORBO", 1, "AGUA-500", 1));

        var scale = (ScaleDiscount) breakdown.discounts().get(0);
        assertThat(amounts(scale.lineAmounts())).containsExactly("L2=0.04");
        assertThat(amounts(scale.tiers())).containsExactly("1:2=0.04");
        assertThat(breakdown.lines().get(0).scaleDiscounts()).isEmpty();
    }

    @Test
    @DisplayName(
            "Con todas las unidades, la escala alcanzada consume todo y no deja nada a la siguiente")
    void allUnitsConsumesEveryEligibleUnit() {
        var version =
                ecVersion(
                        List.of(product("AGUA-500", "AGUAS", "IVA15", "0.35")),
                        List.of(
                                scaleOn(
                                        "ESC-TODAS",
                                        10,
                                        ScaleMode.ALL_UNITS,
                                        Scope.sku("AGUA-500"),
                                        tier(5, "3")),
                                scaleOn(
                                        "ESC-POSTERIOR",
                                        20,
                                        ScaleMode.MARGINAL,
                                        Scope.sku("AGUA-500"),
                                        tier(1, "10"))));

        var breakdown = price(version, EC_MARCA_A, cart("AGUA-500", 7));

        assertThat(breakdown.discounts())
                .extracting(AppliedDiscount::ruleId)
                .containsExactly("ESC-TODAS");
    }

    @Test
    @DisplayName("El monto redondeado de la escala se reparte entre tramos por mayor residuo")
    void allocatesScaleAmountAmongTiersWithRemainder() {
        var version =
                ecVersion(
                        List.of(product("AGUA-500", "AGUAS", "IVA15", "0.35")),
                        List.of(
                                scaleOn(
                                        "ESC-DOS",
                                        10,
                                        ScaleMode.MARGINAL,
                                        Scope.sku("AGUA-500"),
                                        tier(1, "5"),
                                        tier(2, "7"))));

        var breakdown = price(version, EC_MARCA_A, cart("AGUA-500", 2));

        var scale = (ScaleDiscount) breakdown.discounts().get(0);
        assertThat(plain(scale.amount())).isEqualTo("0.04");
        assertThat(amounts(scale.tiers())).containsExactly("1:1=0.02", "2:1=0.02");
    }

    @Test
    @DisplayName(
            "Rechaza un carrito con un SKU que no existe en la versión o una versión de otro tenant")
    void rejectsInconsistentInput() {
        assertThatThrownBy(() -> priceCo(cart("AGUA-500", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> price(coMarcaA(1), EC_MARCA_A, cart("GASEOSA-350", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza un carrito vacío o con el mismo SKU repetido")
    void rejectsInvalidCart() {
        assertThatThrownBy(() -> new Cart(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart("GASEOSA-350", 1, "GASEOSA-350", 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
