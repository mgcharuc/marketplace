package com.marketplace.adapters.json;

import static com.marketplace.adapters.json.JsonSchema.fields;
import static com.marketplace.adapters.json.JsonSchema.oneOf;
import static com.marketplace.adapters.json.JsonSchema.optional;
import static com.marketplace.adapters.json.JsonSchema.required;
import static java.util.Map.entry;

import com.marketplace.adapters.json.JsonSchema.Fields;
import com.marketplace.adapters.json.JsonSchema.Items;
import com.marketplace.adapters.json.JsonSchema.Scalar;
import com.marketplace.adapters.json.JsonSchema.Variants;
import java.util.Map;
import java.util.Set;

final class ConfigSchemas {

    static final String COMBO = "COMBO";
    static final String SCALE = "ESCALA";
    static final String GIFT = "OBSEQUIO";
    static final String TAX = "IMPUESTOS";
    static final String CREDIT = "CREDITO";

    private static final Fields SCOPE =
            fields(
                    entry("kind", required(oneOf(Set.of("SKU", "FAMILY")))),
                    entry("ref", required(Scalar.TEXT)));

    private static final Fields SKU_QUANTITY =
            fields(entry("sku", required(Scalar.TEXT)), entry("qty", required(Scalar.WHOLE)));

    private static final Fields RULE =
            fields(
                    entry("id", required(Scalar.TEXT)),
                    entry("name", required(Scalar.TEXT)),
                    entry("type", required(oneOf(Set.of(COMBO, SCALE, GIFT)))),
                    entry("priority", required(Scalar.WHOLE)),
                    entry("validFrom", optional(Scalar.INSTANT)),
                    entry("validTo", optional(Scalar.INSTANT)));

    private static final Fields SCALE_RULE =
            RULE.with(
                    fields(
                            entry("scope", required(SCOPE)),
                            entry("mode", required(oneOf(Set.of("MARGINAL", "ALL_UNITS")))),
                            entry(
                                    "tiers",
                                    required(
                                            new Items(
                                                    fields(
                                                            entry(
                                                                    "fromQty",
                                                                    required(Scalar.WHOLE)),
                                                            entry(
                                                                    "percent",
                                                                    required(Scalar.DECIMAL))))))));

    private static final Fields COMBO_RULE =
            RULE.with(
                    fields(
                            entry("components", required(new Items(SKU_QUANTITY))),
                            entry("comboPrice", required(Scalar.DECIMAL))));

    private static final Fields GIFT_RULE =
            RULE.with(
                    fields(
                            entry(
                                    "condition",
                                    required(
                                            fields(
                                                    entry(
                                                            "kind",
                                                            required(
                                                                    oneOf(
                                                                            Set.of(
                                                                                    "QUANTITY",
                                                                                    "NET_AMOUNT")))),
                                                    entry("scope", required(SCOPE)),
                                                    entry(
                                                            "threshold",
                                                            required(Scalar.WHOLE_OR_DECIMAL))))),
                            entry("reward", required(SKU_QUANTITY)),
                            entry("repeatable", required(Scalar.BOOLEAN))));

    static final Fields VERSION =
            fields(
                    entry("tenantId", required(Scalar.TEXT)),
                    entry("configVersion", required(Scalar.WHOLE)),
                    entry(
                            "pipeline",
                            required(new Items(oneOf(Set.of(COMBO, SCALE, GIFT, TAX, CREDIT))))),
                    entry(
                            "products",
                            required(
                                    new Items(
                                            fields(
                                                    entry("sku", required(Scalar.TEXT)),
                                                    entry("name", required(Scalar.TEXT)),
                                                    entry("family", required(Scalar.TEXT)),
                                                    entry("taxClass", required(Scalar.TEXT)),
                                                    entry(
                                                            "listPrice",
                                                            required(Scalar.DECIMAL)))))),
                    entry(
                            "rules",
                            required(
                                    new Items(
                                            new Variants(
                                                    "type",
                                                    Map.of(
                                                            COMBO, COMBO_RULE,
                                                            SCALE, SCALE_RULE,
                                                            GIFT, GIFT_RULE))))));

    private static final Fields COUNTRY =
            fields(
                    entry("code", required(Scalar.TEXT)),
                    entry(
                            "currency",
                            required(
                                    fields(
                                            entry("code", required(Scalar.TEXT)),
                                            entry("scale", required(Scalar.WHOLE)),
                                            entry(
                                                    "roundingMode",
                                                    required(
                                                            oneOf(
                                                                    Set.of(
                                                                            "HALF_UP",
                                                                            "HALF_EVEN"))))))),
                    entry(
                            "giftTaxPolicy",
                            required(
                                    oneOf(
                                            Set.of(
                                                    "NONE",
                                                    "ABSORBED_BY_SELLER",
                                                    "CHARGED_TO_CUSTOMER")))),
                    entry(
                            "taxClasses",
                            required(
                                    new Items(
                                            fields(
                                                    entry("code", required(Scalar.TEXT)),
                                                    entry("rate", required(Scalar.DECIMAL)))))));

    private static final Fields NAMED =
            fields(entry("id", required(Scalar.TEXT)), entry("name", required(Scalar.TEXT)));

    private static final Fields CUSTOMER =
            NAMED.with(
                    fields(
                            entry(
                                    "creditAccounts",
                                    required(
                                            new Items(
                                                    fields(
                                                            entry(
                                                                    "distributorId",
                                                                    required(Scalar.TEXT)),
                                                            entry(
                                                                    "available",
                                                                    required(Scalar.DECIMAL))))))));

    private static final Fields TENANT =
            fields(
                    entry("id", required(Scalar.TEXT)),
                    entry("country", required(Scalar.TEXT)),
                    entry("brand", required(Scalar.TEXT)),
                    entry("orderNumberPrefix", required(Scalar.TEXT)),
                    entry("quoteTtlSeconds", required(Scalar.WHOLE)),
                    entry("idempotencyTtlSeconds", required(Scalar.WHOLE)),
                    entry("distributors", required(new Items(NAMED))),
                    entry("customers", required(new Items(CUSTOMER))));

    static final Fields TENANTS =
            fields(
                    entry("countries", required(new Items(COUNTRY))),
                    entry("tenants", required(new Items(TENANT))));

    private ConfigSchemas() {}
}
