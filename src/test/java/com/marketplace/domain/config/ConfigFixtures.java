package com.marketplace.domain.config;

import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.money.CurrencyConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class ConfigFixtures {

    static final List<Stage> PIPELINE =
            List.of(Stage.COMBO, Stage.SCALE, Stage.GIFT, Stage.TAX, Stage.CREDIT);

    static final Country CO =
            new Country(
                    "CO",
                    new CurrencyConfig("COP", 0, RoundingMode.HALF_UP),
                    GiftTaxPolicy.ABSORBED_BY_SELLER,
                    List.of(taxClass("IVA19", "0.19"), taxClass("IVA5", "0.05")));

    static final Tenant CO_MARCA_A =
            new Tenant(
                    "CO-MARCA-A",
                    CO,
                    "Marca A",
                    "CO-A",
                    Duration.ofMinutes(15),
                    Duration.ofHours(24));

    private ConfigFixtures() {}

    static TaxClass taxClass(String code, String rate) {
        return new TaxClass(code, new BigDecimal(rate));
    }

    static Product product(String sku, String family, String taxClass, String price) {
        return new Product(new Sku(sku), sku, family, taxClass, new BigDecimal(price));
    }

    static ScaleRule scale(String id, int priority, Scope scope, Tier... tiers) {
        return new ScaleRule(
                id, id, priority, null, null, scope, ScaleMode.MARGINAL, List.of(tiers));
    }

    static Tier tier(int fromQty, String percent) {
        return new Tier(fromQty, new BigDecimal(percent));
    }

    static ComboRule combo(String id, int priority, String price, ComboComponent... components) {
        return new ComboRule(
                id, id, priority, null, null, List.of(components), new BigDecimal(price));
    }

    static ComboComponent component(String sku, int qty) {
        return new ComboComponent(new Sku(sku), qty);
    }

    static GiftRule gift(String id, int priority, Scope scope, int threshold, String rewardSku) {
        return new GiftRule(
                id,
                id,
                priority,
                null,
                null,
                new GiftCondition(ConditionKind.QUANTITY, scope, BigDecimal.valueOf(threshold)),
                new GiftReward(new Sku(rewardSku), 1),
                true);
    }

    static List<Product> coMarcaAProducts() {
        return List.of(
                product("GASEOSA-350", "GASEOSAS", "IVA19", "2000"),
                product("GASEOSA-1500", "GASEOSAS", "IVA19", "5000"),
                product("JUGO-1L", "JUGOS", "IVA5", "4000"),
                product("SNACK-PAPAS", "SNACKS", "IVA19", "1800"));
    }

    static List<Rule> coMarcaARules() {
        return List.of(
                combo(
                        "CMB-FIESTA",
                        10,
                        "14000",
                        component("GASEOSA-1500", 2),
                        component("SNACK-PAPAS", 3)),
                combo(
                        "CMB-DESAYUNO",
                        20,
                        "9000",
                        component("JUGO-1L", 2),
                        component("SNACK-PAPAS", 2)),
                scale("ESC-GAS350", 10, Scope.sku("GASEOSA-350"), tier(10, "5"), tier(20, "10")),
                scale("ESC-GAS1500", 20, Scope.sku("GASEOSA-1500"), tier(3, "5")),
                gift("OBS-JUGO", 10, Scope.sku("GASEOSA-350"), 12, "JUGO-1L"));
    }

    static ConfigVersion coMarcaA(int number) {
        return new ConfigVersion(
                "CO-MARCA-A", number, PIPELINE, coMarcaAProducts(), coMarcaARules());
    }

    static ConfigVersion coMarcaAWith(List<Product> products, List<Rule> rules) {
        return new ConfigVersion("CO-MARCA-A", 1, PIPELINE, products, rules);
    }

    static ConfigVersion coMarcaAWithRule(Rule rule) {
        var rules = new ArrayList<>(coMarcaARules());
        rules.removeIf(existing -> existing.id().equals(rule.id()));
        rules.add(rule);
        return coMarcaAWith(coMarcaAProducts(), rules);
    }
}
