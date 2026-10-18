package com.marketplace.adapters.json.golden;

import static com.marketplace.domain.config.ConfigFixtures.withCountry;
import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.adapters.json.BreakdownJson;
import com.marketplace.adapters.json.JsonConfigCodec;
import com.marketplace.adapters.json.JsonSeedSource;
import com.marketplace.application.port.TenantSeed;
import com.marketplace.domain.catalog.Quantity;
import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.ConfigValidator;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.Country;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.config.TaxClass;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import com.marketplace.pricing.BreakdownInvariants;
import com.marketplace.pricing.Cart;
import com.marketplace.pricing.CartLine;
import com.marketplace.pricing.CreditAvailability;
import com.marketplace.pricing.PricingEngine;
import com.marketplace.pricing.PricingInput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class GoldenTest {

    private static final Set<String> SUPPORTED_OVERRIDES =
            Set.of("pipeline", "rules", "products", "country");

    private static final Set<String> CASE_FIELDS =
            Set.of("description", "seed", "overrides", "now", "customerId", "credit", "request");

    private static final Set<String> COUNTRY_OVERRIDES =
            Set.of("giftTaxPolicy", "roundingMode", "taxClasses");

    private static final List<String> COMPARED =
            List.of("configVersion", "currency", "lines", "discounts", "gifts", "totals", "credit");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsonConfigCodec codec = new JsonConfigCodec(mapper);
    private final Map<String, Tenant> tenants = tenants();
    private final PricingEngine engine = new PricingEngine();

    @TestFactory
    Stream<DynamicTest> goldenCases() throws IOException, URISyntaxException {
        Path root =
                Path.of(
                        Objects.requireNonNull(getClass().getClassLoader().getResource("golden"))
                                .toURI());
        List<Path> cases;
        try (Stream<Path> entries = Files.list(root)) {
            cases = entries.filter(Files::isDirectory).sorted().toList();
        }
        assertThat(cases).as("casos golden").isNotEmpty();
        return cases.stream().map(this::goldenCase);
    }

    private DynamicTest goldenCase(Path directory) {
        JsonNode testCase = read(directory.resolve("case.json"));
        assertThat(testCase.propertyNames())
                .as("campos de %s/case.json", directory.getFileName())
                .containsExactlyInAnyOrderElementsOf(CASE_FIELDS);
        return DynamicTest.dynamicTest(
                testCase.get("description").stringValue(), () -> verify(directory, testCase));
    }

    private void verify(Path directory, JsonNode testCase) {
        ConfigVersion version = version(testCase);
        Tenant tenant = tenant(version.tenantId(), testCase.get("overrides"));
        assertThat(new ConfigValidator().validate(version, tenant.country()))
                .as("violaciones de la configuración del caso")
                .isEmpty();
        var breakdown =
                engine.price(
                        new PricingInput(
                                version,
                                tenant,
                                cart(testCase.get("request").get("items")),
                                credit(testCase, tenant),
                                OffsetDateTime.parse(testCase.get("now").stringValue())
                                        .toInstant()));

        BreakdownInvariants.check(breakdown);

        JsonNode actual = mapper.readTree(new BreakdownJson(mapper).write(breakdown).toString());
        JsonNode expected = read(directory.resolve("expected.json"));
        for (String field : COMPARED) {
            assertThat(actual.get(field)).as(field).isEqualTo(expected.get(field));
        }
    }

    private ConfigVersion version(JsonNode testCase) {
        String seed = testCase.get("seed").stringValue();
        ObjectNode version = (ObjectNode) readResource("seed/" + seed + ".json");
        JsonNode overrides = testCase.get("overrides");
        if (isPresent(overrides)) {
            assertThat(overrides.propertyNames())
                    .as("cambios sobre la semilla que este runner sabe aplicar")
                    .isSubsetOf(SUPPORTED_OVERRIDES);
            if (overrides.has("pipeline")) {
                version.set("pipeline", overrides.get("pipeline"));
            }
            replaceById((ArrayNode) version.get("rules"), "id", overrides.get("rules"));
            replaceById((ArrayNode) version.get("products"), "sku", overrides.get("products"));
        }
        return codec.decode(mapper.writeValueAsString(version));
    }

    private Tenant tenant(String tenantId, JsonNode overrides) {
        Tenant tenant = tenants.get(tenantId);
        JsonNode changes = isPresent(overrides) ? overrides.get("country") : null;
        if (!isPresent(changes)) {
            return tenant;
        }
        assertThat(changes.propertyNames())
                .as("cambios sobre el país que este runner sabe aplicar")
                .isSubsetOf(COUNTRY_OVERRIDES);
        Country changed = country(tenant.country(), changes);
        assertThat(new ConfigValidator().validate(changed))
                .as("violaciones del país del caso")
                .isEmpty();
        return withCountry(tenant, changed);
    }

    private static Country country(Country country, JsonNode changes) {
        CurrencyConfig currency = country.currency();
        if (changes.has("roundingMode")) {
            currency =
                    new CurrencyConfig(
                            currency.code(),
                            currency.scale(),
                            RoundingMode.valueOf(changes.get("roundingMode").stringValue()));
        }
        GiftTaxPolicy policy =
                changes.has("giftTaxPolicy")
                        ? GiftTaxPolicy.valueOf(changes.get("giftTaxPolicy").stringValue())
                        : country.giftTaxPolicy();
        List<TaxClass> taxClasses =
                changes.has("taxClasses")
                        ? changes.get("taxClasses").values().stream()
                                .map(GoldenTest::taxClass)
                                .toList()
                        : country.taxClasses();
        return new Country(country.code(), currency, policy, taxClasses);
    }

    private static TaxClass taxClass(JsonNode node) {
        return new TaxClass(
                node.get("code").stringValue(), new BigDecimal(node.get("rate").stringValue()));
    }

    private static boolean isPresent(JsonNode node) {
        return node != null && !node.isNull();
    }

    private static void replaceById(ArrayNode items, String key, JsonNode replacements) {
        if (replacements == null || replacements.isNull()) {
            return;
        }
        for (String id : replacements.propertyNames()) {
            for (int i = items.size() - 1; i >= 0; i--) {
                if (items.get(i).get(key).stringValue().equals(id)) {
                    items.remove(i);
                }
            }
            JsonNode replacement = replacements.get(id);
            if (!replacement.isNull()) {
                assertThat(replacement.get(key).stringValue())
                        .as("el reemplazo repite su clave")
                        .isEqualTo(id);
                items.add(replacement);
            }
        }
    }

    private static Cart cart(JsonNode items) {
        return new Cart(
                items.values().stream()
                        .map(
                                item ->
                                        new CartLine(
                                                new Sku(item.get("sku").stringValue()),
                                                new Quantity(item.get("quantity").intValue())))
                        .toList());
    }

    private static CreditAvailability credit(JsonNode testCase, Tenant tenant) {
        String paymentMethod = testCase.get("request").get("paymentMethod").stringValue();
        JsonNode credit = testCase.get("credit");
        return switch (paymentMethod) {
            case "CASH" -> {
                assertThat(isPresent(credit)).as("cupo en un caso de contado").isFalse();
                yield CreditAvailability.none();
            }
            case "CREDIT" -> {
                assertThat(isPresent(credit)).as("cupo en un caso a crédito").isTrue();
                yield CreditAvailability.available(
                        new Money(
                                new BigDecimal(credit.get("available").stringValue()),
                                tenant.currency()));
            }
            default ->
                    throw new IllegalArgumentException(
                            "Medio de pago desconocido en el caso: " + paymentMethod);
        };
    }

    private static Map<String, Tenant> tenants() {
        return new JsonSeedSource()
                .tenants().stream()
                        .map(TenantSeed::tenant)
                        .collect(Collectors.toMap(Tenant::id, Function.identity()));
    }

    private JsonNode read(Path file) {
        try {
            return mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonNode readResource(String resource) {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            Objects.requireNonNull(stream, resource);
            return mapper.readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
