package com.marketplace.adapters.json;

import static com.marketplace.domain.config.ConfigFixtures.CO_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.EC_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.PIPELINE;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.ecMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.gift;
import static com.marketplace.pricing.Carts.cart;
import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.Scope;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.money.Money;
import com.marketplace.pricing.CreditAvailability;
import com.marketplace.pricing.PricingEngine;
import com.marketplace.pricing.PricingInput;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BreakdownJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final BreakdownJson json = new BreakdownJson(mapper);

    private JsonNode render(ConfigVersion version, Tenant tenant, Object... skuAndQuantity) {
        return render(version, tenant, CreditAvailability.none(), skuAndQuantity);
    }

    private JsonNode render(
            ConfigVersion version,
            Tenant tenant,
            CreditAvailability credit,
            Object... skuAndQuantity) {
        var input =
                new PricingInput(
                        version,
                        tenant,
                        cart(skuAndQuantity),
                        credit,
                        Instant.parse("2026-10-16T15:00:00Z"));
        return json.write(new PricingEngine().price(input));
    }

    @Test
    @DisplayName("Escribe los montos como texto con la escala exacta de la moneda")
    void writesAmountsWithCurrencyScale() {
        var node = render(ecMarcaA(), EC_MARCA_A, "AGUA-500", 7);
        var line = node.get("lines").get(0);

        assertThat(node.get("currency").toString())
                .isEqualTo("{\"code\":\"USD\",\"scale\":2,\"roundingMode\":\"HALF_UP\"}");
        assertThat(line.get("unitPrice").stringValue()).isEqualTo("0.35");
        assertThat(line.get("grossAmount").stringValue()).isEqualTo("2.45");
        assertThat(line.get("taxRate").stringValue()).isEqualTo("0.15");
        assertThat(node.get("totals").get("total").stringValue()).isEqualTo("2.74");
        assertThat(line.get("quantity").isInt()).isTrue();
    }

    @Test
    @DisplayName("Una escala lleva modo y tramos, y deja en null los campos propios del combo")
    void writesScaleWithExplicitNulls() {
        var discount = render(coMarcaA(1), CO_MARCA_A, "GASEOSA-350", 25).get("discounts").get(0);

        assertThat(discount.get("type").stringValue()).isEqualTo("ESCALA");
        assertThat(discount.get("origin").stringValue()).isEqualTo("ESCALA:ESC-GAS350");
        assertThat(discount.get("mode").stringValue()).isEqualTo("MARGINAL");
        assertThat(discount.get("tiers").toString())
                .isEqualTo(
                        "[{\"fromQty\":10,\"percent\":\"5\",\"units\":10,\"amount\":\"1000\"},"
                                + "{\"fromQty\":20,\"percent\":\"10\",\"units\":6,\"amount\":\"1200\"}]");
        for (String field : List.of("applications", "components", "listValue", "comboPrice")) {
            assertThat(discount.has(field)).as(field).isTrue();
            assertThat(discount.get(field).isNull()).as(field).isTrue();
        }
    }

    @Test
    @DisplayName(
            "Un combo lleva sus componentes y valores por conjunto, y deja en null modo y tramos")
    void writesComboWithExplicitNulls() {
        var combo =
                render(coMarcaA(1), CO_MARCA_A, "GASEOSA-1500", 2, "SNACK-PAPAS", 3)
                        .get("discounts")
                        .get(0);

        assertThat(combo.get("origin").stringValue()).isEqualTo("COMBO:CMB-FIESTA");
        assertThat(combo.get("applications").intValue()).isEqualTo(1);
        assertThat(combo.get("components").toString())
                .isEqualTo(
                        "[{\"sku\":\"GASEOSA-1500\",\"qty\":2},{\"sku\":\"SNACK-PAPAS\",\"qty\":3}]");
        assertThat(combo.get("listValue").stringValue()).isEqualTo("15400");
        assertThat(combo.get("mode").isNull()).isTrue();
        assertThat(combo.get("tiers").isNull()).isTrue();
    }

    @Test
    @DisplayName("Los arreglos siempre están presentes, aunque estén vacíos")
    void writesEmptyArrays() {
        var node = render(coMarcaA(1), CO_MARCA_A, "GASEOSA-350", 9);
        var line = node.get("lines").get(0);

        assertThat(node.get("discounts").isArray()).isTrue();
        assertThat(node.get("gifts").isArray()).isTrue();
        assertThat(line.get("scaleDiscounts").isEmpty()).isTrue();
        assertThat(line.get("taxBaseAllocations").isEmpty()).isTrue();
        assertThat(node.get("credit").isNull()).isTrue();
    }

    @Test
    @DisplayName(
            "Un obsequio lleva su precio de referencia, monto cobrado cero y quién asume su impuesto")
    void writesGift() {
        var gift = render(coMarcaA(1), CO_MARCA_A, "GASEOSA-350", 12).get("gifts").get(0);

        assertThat(gift.toString())
                .isEqualTo(
                        "{\"ruleId\":\"OBS-JUGO\",\"sku\":\"JUGO-1L\",\"name\":\"JUGO-1L\","
                                + "\"quantity\":1,\"referenceUnitPrice\":\"4000\","
                                + "\"chargedAmount\":\"0\",\"taxBase\":\"4000\",\"taxRate\":\"0.05\","
                                + "\"taxAmount\":\"200\",\"taxBorneBy\":\"SELLER\"}");
    }

    @Test
    @DisplayName(
            "Un obsequio sin impuesto deja en null la tasa y quién lo asume, con montos en la escala de la moneda")
    void writesUntaxedGiftWithNulls() {
        var version =
                new ConfigVersion(
                        "EC-MARCA-A",
                        1,
                        PIPELINE,
                        ecMarcaA().products(),
                        List.of(gift("OBS-AGUA", 10, Scope.sku("AGUA-500"), 5, "JUGO-EC-1L")));

        var gift = render(version, EC_MARCA_A, "AGUA-500", 5).get("gifts").get(0);

        assertThat(gift.get("chargedAmount").stringValue()).isEqualTo("0.00");
        assertThat(gift.get("taxBase").stringValue()).isEqualTo("1.25");
        assertThat(gift.get("taxAmount").stringValue()).isEqualTo("0.00");
        assertThat(gift.get("taxRate").isNull()).isTrue();
        assertThat(gift.get("taxBorneBy").isNull()).isTrue();
    }

    @Test
    @DisplayName("El crédito lleva los montos como texto y si cabe como booleano")
    void writesCredit() {
        var credit =
                render(
                                coMarcaA(1),
                                CO_MARCA_A,
                                CreditAvailability.available(
                                        new Money(new BigDecimal("50000"), CO_MARCA_A.currency())),
                                "GASEOSA-350",
                                25)
                        .get("credit");

        assertThat(credit.toString())
                .isEqualTo(
                        "{\"available\":\"50000\",\"required\":\"56882\",\"fits\":false,"
                                + "\"shortfall\":\"6882\"}");
    }
}
