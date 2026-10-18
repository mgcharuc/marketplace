package com.marketplace.pricing;

import static com.marketplace.domain.config.ConfigFixtures.CO;
import static com.marketplace.domain.config.ConfigFixtures.CO_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaAProducts;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaARules;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaAWith;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaAWithRule;
import static com.marketplace.domain.config.ConfigFixtures.scale;
import static com.marketplace.domain.config.ConfigFixtures.tier;
import static com.marketplace.domain.config.ConfigFixtures.withCountry;
import static com.marketplace.pricing.Carts.cart;
import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.ConditionKind;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.Country;
import com.marketplace.domain.config.GiftCondition;
import com.marketplace.domain.config.GiftReward;
import com.marketplace.domain.config.GiftRule;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.config.Scope;
import com.marketplace.domain.config.Stage;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PricingEngineGiftTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final PricingEngine engine = new PricingEngine();

    private Breakdown price(ConfigVersion version, Tenant tenant, Cart cart) {
        var breakdown =
                engine.price(
                        new PricingInput(version, tenant, cart, CreditAvailability.none(), NOW));
        BreakdownInvariants.check(breakdown);
        return breakdown;
    }

    private Breakdown priceCo(Cart cart) {
        return price(coMarcaA(1), CO_MARCA_A, cart);
    }

    private static Tenant coMarcaAWithPolicy(GiftTaxPolicy policy) {
        return withCountry(
                CO_MARCA_A, new Country(CO.code(), CO.currency(), policy, CO.taxClasses()));
    }

    private static GiftRule gift(
            String id,
            int priority,
            ConditionKind kind,
            Scope scope,
            String threshold,
            String rewardSku,
            int rewardQty,
            boolean repeatable) {
        return new GiftRule(
                id,
                id,
                priority,
                null,
                null,
                new GiftCondition(kind, scope, new BigDecimal(threshold)),
                new GiftReward(new Sku(rewardSku), rewardQty),
                repeatable);
    }

    private static GiftRule juiceEvery(String threshold, int rewardQty, boolean repeatable) {
        return gift(
                "OBS-JUGO",
                10,
                ConditionKind.QUANTITY,
                Scope.sku("GASEOSA-350"),
                threshold,
                "JUGO-1L",
                rewardQty,
                repeatable);
    }

    private static String describe(Gift gift) {
        return String.join(
                " ",
                gift.ruleId(),
                gift.sku().value(),
                "x" + gift.quantity(),
                plain(gift.referenceUnitPrice()),
                plain(gift.taxBase()),
                gift.taxRate() == null ? "null" : gift.taxRate().toPlainString(),
                plain(gift.taxAmount()),
                String.valueOf(gift.taxBorneBy()));
    }

    private static List<Integer> quantities(Breakdown breakdown) {
        return breakdown.gifts().stream().map(Gift::quantity).toList();
    }

    private static String plain(Money money) {
        return money.amount().toPlainString();
    }

    @Test
    @DisplayName("El obsequio cuenta todas las unidades pagadas, también las que tienen descuento")
    void countsPaidUnitsIncludingDiscountedOnes() {
        var breakdown = priceCo(cart("GASEOSA-350", 12));

        assertThat(breakdown.discounts()).hasSize(1);
        assertThat(quantities(breakdown)).containsExactly(1);
        assertThat(breakdown.gifts().get(0).name()).isEqualTo("JUGO-1L");
        assertThat(breakdown.lines()).hasSize(1);
    }

    @Test
    @DisplayName(
            "Si el vendedor asume el impuesto del obsequio, se informa pero no entra en los totales")
    void reportsSellerBorneGiftTax() {
        var breakdown = priceCo(cart("GASEOSA-350", 12));

        assertThat(breakdown.gifts())
                .extracting(PricingEngineGiftTest::describe)
                .containsExactly("OBS-JUGO JUGO-1L x1 4000 4000 0.05 200 SELLER");
        var totals = breakdown.totals();
        assertThat(plain(totals.taxableBase())).isEqualTo("23700");
        assertThat(plain(totals.taxTotal())).isEqualTo("4503");
        assertThat(plain(totals.total())).isEqualTo("28203");
    }

    @Test
    @DisplayName("Sin llegar al umbral no hay obsequio")
    void givesNothingBelowThreshold() {
        assertThat(priceCo(cart("GASEOSA-350", 11)).gifts()).isEmpty();
    }

    @Test
    @DisplayName(
            "Un obsequio repetible entrega la recompensa por cada vez que se alcanza el umbral")
    void repeatsRewardForEachThreshold() {
        assertThat(quantities(priceCo(cart("GASEOSA-350", 24)))).containsExactly(2);
        assertThat(quantities(priceCo(cart("GASEOSA-350", 35)))).containsExactly(2);
        assertThat(quantities(priceCo(cart("GASEOSA-350", 36)))).containsExactly(3);

        var twoPerThreshold = coMarcaAWithRule(juiceEvery("12", 2, true));
        assertThat(quantities(price(twoPerThreshold, CO_MARCA_A, cart("GASEOSA-350", 25))))
                .containsExactly(4);
    }

    @Test
    @DisplayName("Un obsequio no repetible entrega su recompensa una sola vez")
    void givesNonRepeatableRewardOnce() {
        var once = coMarcaAWithRule(juiceEvery("12", 2, false));

        assertThat(quantities(price(once, CO_MARCA_A, cart("GASEOSA-350", 36)))).containsExactly(2);
    }

    @Test
    @DisplayName("Sin impuesto para el obsequio, no se informa tasa ni quién lo asume")
    void omitsTaxWhenPolicyIsNone() {
        var breakdown =
                price(coMarcaA(1), coMarcaAWithPolicy(GiftTaxPolicy.NONE), cart("GASEOSA-350", 12));

        assertThat(breakdown.gifts())
                .extracting(PricingEngineGiftTest::describe)
                .containsExactly("OBS-JUGO JUGO-1L x1 4000 4000 null 0 null");
        assertThat(plain(breakdown.totals().total())).isEqualTo("28203");
    }

    @Test
    @DisplayName(
            "Si el impuesto del obsequio lo paga el cliente, su base y su impuesto entran en los totales")
    void chargesGiftTaxToCustomer() {
        var breakdown =
                price(
                        coMarcaA(1),
                        coMarcaAWithPolicy(GiftTaxPolicy.CHARGED_TO_CUSTOMER),
                        cart("GASEOSA-350", 12));

        assertThat(breakdown.gifts())
                .extracting(PricingEngineGiftTest::describe)
                .containsExactly("OBS-JUGO JUGO-1L x1 4000 4000 0.05 200 CUSTOMER");
        var totals = breakdown.totals();
        assertThat(plain(totals.netSubtotal())).isEqualTo("23700");
        assertThat(plain(totals.taxableBase())).isEqualTo("27700");
        assertThat(plain(totals.taxTotal())).isEqualTo("4703");
        assertThat(plain(totals.total())).isEqualTo("28403");
    }

    @Test
    @DisplayName("Las unidades obsequiadas no activan escalas ni otros obsequios")
    void giftUnitsDoNotTriggerLevers() {
        var version =
                coMarcaAWith(
                        coMarcaAProducts(),
                        List.of(
                                scale("ESC-GAS350", 10, Scope.sku("GASEOSA-350"), tier(13, "5")),
                                gift(
                                        "OBS-GASEOSA",
                                        10,
                                        ConditionKind.QUANTITY,
                                        Scope.sku("GASEOSA-350"),
                                        "12",
                                        "GASEOSA-350",
                                        1,
                                        true),
                                gift(
                                        "OBS-TRECE",
                                        20,
                                        ConditionKind.QUANTITY,
                                        Scope.sku("GASEOSA-350"),
                                        "13",
                                        "JUGO-1L",
                                        1,
                                        true)));

        var breakdown = price(version, CO_MARCA_A, cart("GASEOSA-350", 12));

        assertThat(breakdown.discounts()).isEmpty();
        assertThat(breakdown.gifts())
                .extracting(PricingEngineGiftTest::describe)
                .containsExactly("OBS-GASEOSA GASEOSA-350 x1 2000 2000 0.19 380 SELLER");
        assertThat(plain(breakdown.totals().total())).isEqualTo("28560");
    }

    @Test
    @DisplayName(
            "Cada regla aplicada tiene su propia entrada, aunque regalen el mismo SKU, ordenadas por regla")
    void listsEachRuleSeparatelyByRuleId() {
        var version =
                coMarcaAWith(
                        coMarcaAProducts(),
                        List.of(
                                gift(
                                        "OBS-B",
                                        10,
                                        ConditionKind.QUANTITY,
                                        Scope.sku("GASEOSA-350"),
                                        "6",
                                        "JUGO-1L",
                                        1,
                                        true),
                                gift(
                                        "OBS-A",
                                        20,
                                        ConditionKind.QUANTITY,
                                        Scope.sku("GASEOSA-350"),
                                        "12",
                                        "JUGO-1L",
                                        1,
                                        true)));

        var breakdown = price(version, CO_MARCA_A, cart("GASEOSA-350", 12));

        assertThat(breakdown.gifts())
                .extracting(gift -> gift.ruleId() + " x" + gift.quantity())
                .containsExactly("OBS-A x1", "OBS-B x2");
    }

    @Test
    @DisplayName("Con alcance de familia, el umbral suma las unidades pagadas de todos sus SKU")
    void countsFamilyUnits() {
        var version =
                coMarcaAWithRule(
                        gift(
                                "OBS-JUGO",
                                10,
                                ConditionKind.QUANTITY,
                                Scope.family("GASEOSAS"),
                                "12",
                                "JUGO-1L",
                                1,
                                true));

        var breakdown = price(version, CO_MARCA_A, cart("GASEOSA-350", 6, "GASEOSA-1500", 6));

        assertThat(quantities(breakdown)).containsExactly(1);
    }

    @Test
    @DisplayName(
            "Con condición por monto, el umbral se compara con la base gravable después de descuentos")
    void comparesNetAmountAfterDiscounts() {
        var version =
                coMarcaAWithRule(
                        gift(
                                "OBS-JUGO",
                                10,
                                ConditionKind.NET_AMOUNT,
                                Scope.sku("GASEOSA-350"),
                                "11900",
                                "JUGO-1L",
                                1,
                                true));

        assertThat(quantities(price(version, CO_MARCA_A, cart("GASEOSA-350", 12))))
                .containsExactly(1);
        assertThat(quantities(price(version, CO_MARCA_A, cart("GASEOSA-350", 5)))).isEmpty();
    }

    @Test
    @DisplayName("Sin la etapa de obsequio en el pipeline no hay obsequios")
    void ignoresGiftsMissingFromPipeline() {
        var version =
                new ConfigVersion(
                        "CO-MARCA-A",
                        1,
                        List.of(Stage.COMBO, Stage.SCALE, Stage.TAX, Stage.CREDIT),
                        coMarcaAProducts(),
                        coMarcaARules());

        assertThat(price(version, CO_MARCA_A, cart("GASEOSA-350", 12)).gifts()).isEmpty();
    }
}
