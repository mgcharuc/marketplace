package com.marketplace.adapters.json;

import com.marketplace.domain.config.ConfigInvalidException;
import com.marketplace.domain.config.ConfigViolation;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class JsonSchema {

    static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";
    static final String REQUIRED = "REQUIRED";
    static final String INVALID_VALUE = "INVALID_VALUE";
    static final String INVALID_JSON = "INVALID_JSON";

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    sealed interface Type permits Scalar, Choice, Fields, Items, Variants {}

    enum Scalar implements Type {
        TEXT,
        WHOLE,
        DECIMAL,
        WHOLE_OR_DECIMAL,
        BOOLEAN,
        INSTANT
    }

    record Choice(Set<String> values) implements Type {}

    record Field(Type type, boolean required) {}

    record Fields(Map<String, Field> fields) implements Type {

        Fields with(Fields other) {
            var merged = new LinkedHashMap<>(fields);
            merged.putAll(other.fields);
            return new Fields(merged);
        }
    }

    record Items(Type item) implements Type {}

    record Variants(String discriminator, Map<String, Fields> byValue) implements Type {}

    private JsonSchema() {}

    static Field required(Type type) {
        return new Field(type, true);
    }

    static Field optional(Type type) {
        return new Field(type, false);
    }

    static Choice oneOf(Set<String> values) {
        return new Choice(values);
    }

    @SafeVarargs
    static Fields fields(Map.Entry<String, Field>... entries) {
        var fields = new LinkedHashMap<String, Field>();
        for (var entry : entries) {
            fields.put(entry.getKey(), entry.getValue());
        }
        return new Fields(fields);
    }

    static JsonNode parse(JsonMapper mapper, String text, Type type) {
        JsonNode root;
        try {
            root = mapper.readTree(text);
        } catch (JacksonException e) {
            throw new ConfigInvalidException(List.of(violation(INVALID_JSON, "")), e);
        }
        var violations = new ArrayList<ConfigViolation>();
        check(root, type, "", violations);
        if (!violations.isEmpty()) {
            throw new ConfigInvalidException(violations);
        }
        return root;
    }

    static <T> T build(String path, Supplier<T> builder) {
        try {
            return builder.get();
        } catch (IllegalArgumentException e) {
            throw new ConfigInvalidException(List.of(violation(INVALID_VALUE, path)), e);
        }
    }

    static ConfigViolation violation(String code, String path) {
        return new ConfigViolation(code, path);
    }

    static String child(String path, String name) {
        return path.isEmpty() ? name : path + "." + name;
    }

    static String item(String path, int index) {
        return path + "[" + index + "]";
    }

    private static void check(
            JsonNode node, Type type, String path, List<ConfigViolation> violations) {
        switch (type) {
            case Scalar scalar -> {
                if (!matches(node, scalar)) {
                    violations.add(violation(INVALID_VALUE, path));
                }
            }
            case Choice choice -> {
                if (!node.isString() || !choice.values().contains(node.stringValue())) {
                    violations.add(violation(INVALID_VALUE, path));
                }
            }
            case Fields fields -> checkObject(node, fields, path, violations);
            case Items items -> checkItems(node, items, path, violations);
            case Variants variants -> checkVariant(node, variants, path, violations);
        }
    }

    private static void checkObject(
            JsonNode node, Fields fields, String path, List<ConfigViolation> violations) {
        if (!node.isObject()) {
            violations.add(violation(INVALID_VALUE, path));
            return;
        }
        for (String name : node.propertyNames()) {
            if (!fields.fields().containsKey(name)) {
                violations.add(violation(UNKNOWN_FIELD, child(path, name)));
            }
        }
        fields.fields()
                .forEach(
                        (name, field) -> {
                            JsonNode value = node.get(name);
                            if (value == null || value.isNull()) {
                                if (field.required()) {
                                    violations.add(violation(REQUIRED, child(path, name)));
                                }
                            } else {
                                check(value, field.type(), child(path, name), violations);
                            }
                        });
    }

    private static void checkItems(
            JsonNode node, Items items, String path, List<ConfigViolation> violations) {
        if (!node.isArray()) {
            violations.add(violation(INVALID_VALUE, path));
            return;
        }
        for (int i = 0; i < node.size(); i++) {
            check(node.get(i), items.item(), item(path, i), violations);
        }
    }

    private static void checkVariant(
            JsonNode node, Variants variants, String path, List<ConfigViolation> violations) {
        if (!node.isObject()) {
            violations.add(violation(INVALID_VALUE, path));
            return;
        }
        String discriminatorPath = child(path, variants.discriminator());
        JsonNode discriminator = node.get(variants.discriminator());
        if (discriminator == null || discriminator.isNull()) {
            violations.add(violation(REQUIRED, discriminatorPath));
            return;
        }
        Fields fields =
                discriminator.isString()
                        ? variants.byValue().get(discriminator.stringValue())
                        : null;
        if (fields == null) {
            violations.add(violation(INVALID_VALUE, discriminatorPath));
            return;
        }
        checkObject(node, fields, path, violations);
    }

    private static boolean matches(JsonNode node, Scalar scalar) {
        return switch (scalar) {
            case TEXT -> node.isString();
            case WHOLE -> node.isIntegralNumber() && node.canConvertToInt();
            case DECIMAL -> isDecimalText(node);
            case WHOLE_OR_DECIMAL ->
                    (node.isIntegralNumber() && node.canConvertToInt()) || isDecimalText(node);
            case BOOLEAN -> node.isBoolean();
            case INSTANT -> isInstantText(node);
        };
    }

    private static boolean isDecimalText(JsonNode node) {
        return node.isString() && PLAIN_DECIMAL.matcher(node.stringValue()).matches();
    }

    private static boolean isInstantText(JsonNode node) {
        if (!node.isString()) {
            return false;
        }
        try {
            OffsetDateTime.parse(node.stringValue());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
