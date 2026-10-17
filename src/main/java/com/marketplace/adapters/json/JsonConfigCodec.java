package com.marketplace.adapters.json;

import static com.marketplace.adapters.json.JsonSchema.build;
import static com.marketplace.adapters.json.JsonSchema.item;

import com.marketplace.application.port.ConfigCodec;
import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.ComboComponent;
import com.marketplace.domain.config.ComboRule;
import com.marketplace.domain.config.ConditionKind;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.GiftCondition;
import com.marketplace.domain.config.GiftReward;
import com.marketplace.domain.config.GiftRule;
import com.marketplace.domain.config.Product;
import com.marketplace.domain.config.Rule;
import com.marketplace.domain.config.ScaleMode;
import com.marketplace.domain.config.ScaleRule;
import com.marketplace.domain.config.Scope;
import com.marketplace.domain.config.ScopeKind;
import com.marketplace.domain.config.Stage;
import com.marketplace.domain.config.Tier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class JsonConfigCodec implements ConfigCodec {

    private static final Map<String, Stage> STAGES =
            Map.of(
                    ConfigSchemas.COMBO, Stage.COMBO,
                    ConfigSchemas.SCALE, Stage.SCALE,
                    ConfigSchemas.GIFT, Stage.GIFT,
                    ConfigSchemas.TAX, Stage.TAX,
                    ConfigSchemas.CREDIT, Stage.CREDIT);

    private static final Map<Stage, String> STAGE_CODES =
            Map.of(
                    Stage.COMBO, ConfigSchemas.COMBO,
                    Stage.SCALE, ConfigSchemas.SCALE,
                    Stage.GIFT, ConfigSchemas.GIFT,
                    Stage.TAX, ConfigSchemas.TAX,
                    Stage.CREDIT, ConfigSchemas.CREDIT);

    private final JsonMapper mapper;

    public JsonConfigCodec() {
        this(JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    }

    public JsonConfigCodec(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ConfigVersion decode(String text) {
        JsonNode root = JsonSchema.parse(mapper, text, ConfigSchemas.VERSION);
        List<Stage> pipeline =
                list(root.get("pipeline"), "pipeline", node -> STAGES.get(node.stringValue()));
        List<Product> products = list(root.get("products"), "products", JsonConfigCodec::product);
        List<Rule> rules = list(root.get("rules"), "rules", JsonConfigCodec::rule);
        return build(
                "",
                () ->
                        new ConfigVersion(
                                root.get("tenantId").stringValue(),
                                root.get("configVersion").intValue(),
                                pipeline,
                                products,
                                rules));
    }

    @Override
    public String encode(ConfigVersion version) {
        ObjectNode root = mapper.createObjectNode();
        root.put("tenantId", version.tenantId());
        root.put("configVersion", version.number());
        ArrayNode pipeline = root.putArray("pipeline");
        version.pipeline().forEach(stage -> pipeline.add(STAGE_CODES.get(stage)));
        ArrayNode products = root.putArray("products");
        version.products().forEach(product -> writeProduct(products.addObject(), product));
        ArrayNode rules = root.putArray("rules");
        version.rules().forEach(rule -> writeRule(rules.addObject(), rule));
        return mapper.writeValueAsString(root);
    }

    static <T> List<T> list(JsonNode array, String path, Function<JsonNode, T> reader) {
        var items = new ArrayList<T>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonNode node = array.get(i);
            items.add(build(item(path, i), () -> reader.apply(node)));
        }
        return items;
    }

    static BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.get(field).stringValue());
    }

    private static Product product(JsonNode node) {
        return new Product(
                new Sku(node.get("sku").stringValue()),
                node.get("name").stringValue(),
                node.get("family").stringValue(),
                node.get("taxClass").stringValue(),
                decimal(node, "listPrice"));
    }

    private static Rule rule(JsonNode node) {
        String id = node.get("id").stringValue();
        String name = node.get("name").stringValue();
        int priority = node.get("priority").intValue();
        Instant validFrom = instant(node.get("validFrom"));
        Instant validTo = instant(node.get("validTo"));
        return switch (STAGES.get(node.get("type").stringValue())) {
            case SCALE ->
                    new ScaleRule(
                            id,
                            name,
                            priority,
                            validFrom,
                            validTo,
                            scope(node.get("scope")),
                            ScaleMode.valueOf(node.get("mode").stringValue()),
                            mapItems(node.get("tiers"), JsonConfigCodec::tier));
            case COMBO ->
                    new ComboRule(
                            id,
                            name,
                            priority,
                            validFrom,
                            validTo,
                            mapItems(node.get("components"), JsonConfigCodec::component),
                            decimal(node, "comboPrice"));
            case GIFT ->
                    new GiftRule(
                            id,
                            name,
                            priority,
                            validFrom,
                            validTo,
                            condition(node.get("condition")),
                            reward(node.get("reward")),
                            node.get("repeatable").booleanValue());
            case TAX, CREDIT ->
                    throw new IllegalArgumentException(
                            "Tipo de regla no admitido: " + node.get("type"));
        };
    }

    private static <T> List<T> mapItems(JsonNode array, Function<JsonNode, T> mapper) {
        var items = new ArrayList<T>(array.size());
        array.values().forEach(node -> items.add(mapper.apply(node)));
        return items;
    }

    private static Instant instant(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return OffsetDateTime.parse(node.stringValue()).toInstant();
    }

    private static Scope scope(JsonNode node) {
        return new Scope(
                ScopeKind.valueOf(node.get("kind").stringValue()), node.get("ref").stringValue());
    }

    private static Tier tier(JsonNode node) {
        return new Tier(node.get("fromQty").intValue(), decimal(node, "percent"));
    }

    private static ComboComponent component(JsonNode node) {
        return new ComboComponent(
                new Sku(node.get("sku").stringValue()), node.get("qty").intValue());
    }

    private static GiftCondition condition(JsonNode node) {
        var kind = ConditionKind.valueOf(node.get("kind").stringValue());
        JsonNode threshold = node.get("threshold");
        if (kind == ConditionKind.QUANTITY && !threshold.isIntegralNumber()) {
            throw new IllegalArgumentException("Un umbral por cantidad debe ser un entero");
        }
        if (kind == ConditionKind.NET_AMOUNT && !threshold.isString()) {
            throw new IllegalArgumentException("Un umbral por monto debe ser un texto decimal");
        }
        BigDecimal value =
                threshold.isString()
                        ? new BigDecimal(threshold.stringValue())
                        : threshold.decimalValue();
        return new GiftCondition(kind, scope(node.get("scope")), value);
    }

    private static GiftReward reward(JsonNode node) {
        return new GiftReward(new Sku(node.get("sku").stringValue()), node.get("qty").intValue());
    }

    private static void writeProduct(ObjectNode node, Product product) {
        node.put("sku", product.sku().value());
        node.put("name", product.name());
        node.put("family", product.family());
        node.put("taxClass", product.taxClass());
        node.put("listPrice", product.listPrice().toPlainString());
    }

    private static void writeRule(ObjectNode node, Rule rule) {
        node.put("id", rule.id());
        node.put("name", rule.name());
        node.put("type", STAGE_CODES.get(rule.stage()));
        node.put("priority", rule.priority());
        writeInstant(node, "validFrom", rule.validFrom());
        writeInstant(node, "validTo", rule.validTo());
        switch (rule) {
            case ScaleRule scale -> {
                writeScope(node.putObject("scope"), scale.scope());
                node.put("mode", scale.mode().name());
                ArrayNode tiers = node.putArray("tiers");
                scale.tiers()
                        .forEach(
                                tier ->
                                        tiers.addObject()
                                                .put("fromQty", tier.fromQty())
                                                .put("percent", tier.percent().toPlainString()));
            }
            case ComboRule combo -> {
                ArrayNode components = node.putArray("components");
                combo.components()
                        .forEach(
                                component ->
                                        components
                                                .addObject()
                                                .put("sku", component.sku().value())
                                                .put("qty", component.qty()));
                node.put("comboPrice", combo.comboPrice().toPlainString());
            }
            case GiftRule gift -> {
                ObjectNode condition = node.putObject("condition");
                condition.put("kind", gift.condition().kind().name());
                writeScope(condition.putObject("scope"), gift.condition().scope());
                writeThreshold(condition, gift.condition());
                node.putObject("reward")
                        .put("sku", gift.reward().sku().value())
                        .put("qty", gift.reward().qty());
                node.put("repeatable", gift.repeatable());
            }
        }
    }

    private static void writeInstant(ObjectNode node, String field, Instant instant) {
        if (instant == null) {
            node.putNull(field);
        } else {
            node.put(field, instant.toString());
        }
    }

    private static void writeScope(ObjectNode node, Scope scope) {
        node.put("kind", scope.kind().name());
        node.put("ref", scope.ref());
    }

    private static void writeThreshold(ObjectNode node, GiftCondition condition) {
        if (condition.kind() == ConditionKind.QUANTITY) {
            node.put("threshold", condition.threshold().intValueExact());
        } else {
            node.put("threshold", condition.threshold().toPlainString());
        }
    }
}
