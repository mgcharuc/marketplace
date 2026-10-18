package com.marketplace.adapters.json;

import static com.marketplace.domain.config.ConfigFixtures.CO_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.EC_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.ecMarcaA;
import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.domain.catalog.Quantity;
import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.Tenant;
import com.marketplace.pricing.Cart;
import com.marketplace.pricing.CartLine;
import com.marketplace.pricing.CreditAvailability;
import com.marketplace.pricing.PricingEngine;
import com.marketplace.pricing.PricingInput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BreakdownJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final BreakdownJson json = new BreakdownJson(mapper);

    private JsonNode render(ConfigVersion version, Tenant tenant, Object... skuAndQuantity) {
        var lines = new ArrayList<CartLine>();
        for (int i = 0; i < skuAndQuantity.length; i += 2) {
            lines.add(
                    new CartLine(
                            new Sku((String) skuAndQuantity[i]),
                            new Quantity((Integer) skuAndQuantity[i + 1])));
        }
        var input =
                new PricingInput(
                        version,
                        tenant,
                        new Cart(lines),
                        CreditAvailability.none(),
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
}
