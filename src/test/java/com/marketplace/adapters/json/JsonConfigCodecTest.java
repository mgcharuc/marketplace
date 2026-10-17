package com.marketplace.adapters.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.ComboRule;
import com.marketplace.domain.config.ConditionKind;
import com.marketplace.domain.config.ConfigInvalidException;
import com.marketplace.domain.config.ConfigViolation;
import com.marketplace.domain.config.GiftRule;
import com.marketplace.domain.config.ScaleMode;
import com.marketplace.domain.config.ScaleRule;
import com.marketplace.domain.config.Scope;
import com.marketplace.domain.config.Stage;
import java.math.BigDecimal;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonConfigCodecTest {

    private static final String SEED = Resources.read("seed/co-marca-a.v1.json");

    private final JsonConfigCodec codec = new JsonConfigCodec();

    private static void assertViolations(ThrowingCallable call, ConfigViolation... expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ConfigInvalidException.class,
                        error -> assertThat(error.violations()).containsExactly(expected));
    }

    private static ConfigViolation schema(String code, String ref) {
        return new ConfigViolation(code, ref);
    }

    @Test
    @DisplayName("Lee la versión de la semilla con su pipeline, productos y reglas")
    void decodesSeedVersion() {
        var version = codec.decode(SEED);

        assertThat(version.tenantId()).isEqualTo("CO-MARCA-A");
        assertThat(version.number()).isEqualTo(1);
        assertThat(version.pipeline())
                .containsExactly(Stage.COMBO, Stage.SCALE, Stage.GIFT, Stage.TAX, Stage.CREDIT);
        assertThat(version.product(new Sku("SNACK-PAPAS")).orElseThrow().listPrice())
                .isEqualByComparingTo("1800");
        assertThat(version.rules())
                .extracting(rule -> rule.id())
                .containsExactly(
                        "CMB-FIESTA", "CMB-DESAYUNO", "ESC-GAS350", "ESC-GAS1500", "OBS-JUGO");

        var fiesta = (ComboRule) version.rules().get(0);
        assertThat(fiesta.components()).hasSize(2);
        assertThat(fiesta.comboPrice()).isEqualByComparingTo("14000");

        var scale = (ScaleRule) version.rules().get(2);
        assertThat(scale.scope()).isEqualTo(Scope.sku("GASEOSA-350"));
        assertThat(scale.mode()).isEqualTo(ScaleMode.MARGINAL);
        assertThat(scale.tiers()).extracting(tier -> tier.fromQty()).containsExactly(10, 20);
        assertThat(scale.validFrom()).isNull();

        var gift = (GiftRule) version.rules().get(4);
        assertThat(gift.condition().kind()).isEqualTo(ConditionKind.QUANTITY);
        assertThat(gift.condition().threshold()).isEqualByComparingTo("12");
        assertThat(gift.reward().sku()).isEqualTo(new Sku("JUGO-1L"));
        assertThat(gift.repeatable()).isTrue();
    }

    @Test
    @DisplayName("Escribir y volver a leer una versión da la misma versión")
    void roundTripsVersion() {
        var version = codec.decode(SEED);

        assertThat(codec.decode(codec.encode(version))).isEqualTo(version);
    }

    @Test
    @DisplayName("Normaliza la vigencia a UTC y lee un umbral por monto como decimal")
    void readsValidityAndNetAmountThreshold() {
        var json =
                SEED.replace(
                                "\"type\": \"OBSEQUIO\", \"priority\": 10,",
                                "\"type\": \"OBSEQUIO\", \"priority\": 10,"
                                        + " \"validFrom\": \"2026-10-17T00:00:00-05:00\", \"validTo\": null,")
                        .replace(
                                "\"kind\": \"QUANTITY\", \"scope\": { \"kind\": \"SKU\", \"ref\":"
                                        + " \"GASEOSA-350\" }, \"threshold\": 12",
                                "\"kind\": \"NET_AMOUNT\", \"scope\": { \"kind\": \"SKU\", \"ref\":"
                                        + " \"GASEOSA-350\" }, \"threshold\": \"150000\"");

        var gift = (GiftRule) codec.decode(json).rules().get(4);

        assertThat(gift.validFrom()).isEqualTo(Instant.parse("2026-10-17T05:00:00Z"));
        assertThat(gift.validTo()).isNull();
        assertThat(gift.condition().kind()).isEqualTo(ConditionKind.NET_AMOUNT);
        assertThat(gift.condition().threshold()).isEqualTo(new BigDecimal("150000"));
        assertThat(codec.encode(codec.decode(json))).contains("\"2026-10-17T05:00:00Z\"");
    }

    @Test
    @DisplayName("Informa todos los campos desconocidos con su ruta")
    void reportsUnknownFields() {
        var json =
                SEED.replace("\"configVersion\": 1,", "\"configVersion\": 1, \"draft\": true,")
                        .replace(
                                "{ \"fromQty\": 3, \"percent\": \"5\" }",
                                "{ \"fromQty\": 3, \"percent\": \"5\", \"cap\": \"10\" }");

        assertViolations(
                () -> codec.decode(json),
                schema("UNKNOWN_FIELD", "draft"),
                schema("UNKNOWN_FIELD", "rules[3].tiers[0].cap"));
    }

    @Test
    @DisplayName("Informa los campos obligatorios que faltan o vienen en null")
    void reportsMissingRequiredFields() {
        var json =
                SEED.replace(
                                "\"pipeline\": [\"COMBO\", \"ESCALA\", \"OBSEQUIO\", \"IMPUESTOS\","
                                        + " \"CREDITO\"],",
                                "")
                        .replace(
                                "\"taxClass\": \"IVA19\", \"listPrice\": \"2000\"",
                                "\"taxClass\": \"IVA19\", \"listPrice\": null")
                        .replace(
                                "\"mode\": \"MARGINAL\",\n      \"tiers\": [ { \"fromQty\": 3",
                                "\n      \"tiers\": [ { \"fromQty\": 3");

        assertViolations(
                () -> codec.decode(json),
                schema("REQUIRED", "pipeline"),
                schema("REQUIRED", "products[0].listPrice"),
                schema("REQUIRED", "rules[3].mode"));
    }

    @Test
    @DisplayName("Rechaza tipos incorrectos, montos numéricos y valores fuera de catálogo")
    void reportsInvalidValues() {
        var json =
                SEED.replace("\"listPrice\": \"5000\"", "\"listPrice\": 5000")
                        .replace(
                                "\"type\": \"COMBO\", \"priority\": 20",
                                "\"type\": \"COMBO\", \"priority\": \"20\"")
                        .replace("\"OBSEQUIO\", \"IMPUESTOS\"", "\"REGALO\", \"IMPUESTOS\"")
                        .replace("\"type\": \"OBSEQUIO\"", "\"type\": \"DESCUENTO\"");

        assertViolations(
                () -> codec.decode(json),
                schema("INVALID_VALUE", "pipeline[2]"),
                schema("INVALID_VALUE", "products[1].listPrice"),
                schema("INVALID_VALUE", "rules[1].priority"),
                schema("INVALID_VALUE", "rules[4].type"));
    }

    @Test
    @DisplayName("Rechaza montos en notación científica")
    void rejectsScientificNotation() {
        var json = SEED.replace("\"listPrice\": \"2000\"", "\"listPrice\": \"2e3\"");

        assertViolations(
                () -> codec.decode(json), schema("INVALID_VALUE", "products[0].listPrice"));
    }

    @Test
    @DisplayName("El umbral por cantidad es un entero y el umbral por monto es un texto decimal")
    void requiresThresholdFormatByKind() {
        var quantityAsText = SEED.replace("\"threshold\": 12", "\"threshold\": \"12\"");
        var amountAsNumber = SEED.replace("\"kind\": \"QUANTITY\"", "\"kind\": \"NET_AMOUNT\"");

        assertViolations(() -> codec.decode(quantityAsText), schema("INVALID_VALUE", "rules[4]"));
        assertViolations(() -> codec.decode(amountAsNumber), schema("INVALID_VALUE", "rules[4]"));
    }

    @Test
    @DisplayName("Rechaza un valor que el dominio no acepta, indicando el elemento")
    void reportsDomainInvariantViolations() {
        var json = SEED.replace("\"sku\": \"JUGO-1L\", \"name\"", "\"sku\": \"JUGO 1L\", \"name\"");

        assertViolations(() -> codec.decode(json), schema("INVALID_VALUE", "products[2]"));
    }

    @Test
    @DisplayName("Rechaza un JSON mal formado")
    void rejectsMalformedJson() {
        assertViolations(() -> codec.decode("{ \"tenantId\": "), schema("INVALID_JSON", ""));
    }
}
