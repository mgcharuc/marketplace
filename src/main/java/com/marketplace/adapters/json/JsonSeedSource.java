package com.marketplace.adapters.json;

import static com.marketplace.adapters.json.JsonConfigCodec.decimal;
import static com.marketplace.adapters.json.JsonConfigCodec.list;
import static com.marketplace.adapters.json.JsonSchema.child;
import static com.marketplace.adapters.json.JsonSchema.item;
import static com.marketplace.adapters.json.JsonSchema.violation;

import com.marketplace.application.port.CreditAccountSeed;
import com.marketplace.application.port.CustomerSeed;
import com.marketplace.application.port.DistributorSeed;
import com.marketplace.application.port.SeedSource;
import com.marketplace.application.port.TenantSeed;
import com.marketplace.domain.config.ConfigInvalidException;
import com.marketplace.domain.config.ConfigValidator;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.ConfigViolation;
import com.marketplace.domain.config.Country;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.config.TaxClass;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class JsonSeedSource implements SeedSource {

    private static final String DEFAULT_DIRECTORY = "seed";
    private static final String TENANTS_FILE = "tenants.json";
    private static final String UNKNOWN_COUNTRY = "UNKNOWN_COUNTRY";
    private static final int INITIAL_VERSION = 1;

    private final String directory;
    private final JsonMapper mapper;
    private final JsonConfigCodec codec;
    private final ConfigValidator validator = new ConfigValidator();

    public JsonSeedSource() {
        this(DEFAULT_DIRECTORY);
    }

    public JsonSeedSource(String directory) {
        this.directory = directory;
        this.mapper =
                JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        this.codec = new JsonConfigCodec(mapper);
    }

    @Override
    public List<TenantSeed> tenants() {
        JsonNode root = JsonSchema.parse(mapper, require(TENANTS_FILE), ConfigSchemas.TENANTS);
        Map<String, Country> countries = countries(root.get("countries"));
        requireKnownCountries(root.get("tenants"), countries);
        List<TenantSeed> tenants =
                list(
                        root.get("tenants"),
                        "tenants",
                        node -> tenant(node, countries.get(node.get("country").stringValue())));
        requireUniqueTenants(tenants);
        return tenants;
    }

    @Override
    public List<ConfigVersion> versions() {
        return tenants().stream().map(seed -> initialVersion(seed.tenant())).toList();
    }

    private ConfigVersion initialVersion(Tenant tenant) {
        String file = versionFile(tenant.id());
        ConfigVersion version = codec.decode(require(file));
        if (!version.tenantId().equals(tenant.id()) || version.number() != INITIAL_VERSION) {
            throw new ConfigInvalidException(
                    List.of(violation(JsonSchema.INVALID_VALUE, directory + "/" + file)));
        }
        List<ConfigViolation> violations = validator.validate(version, tenant.country());
        if (!violations.isEmpty()) {
            throw new ConfigInvalidException(violations);
        }
        return version;
    }

    private static String versionFile(String tenantId) {
        return tenantId.toLowerCase(Locale.ROOT) + ".v" + INITIAL_VERSION + ".json";
    }

    private Map<String, Country> countries(JsonNode array) {
        var countries = new HashMap<String, Country>();
        List<Country> loaded = list(array, "countries", JsonSeedSource::country);
        for (int i = 0; i < loaded.size(); i++) {
            Country country = loaded.get(i);
            List<ConfigViolation> violations = validator.validate(country);
            if (!violations.isEmpty()) {
                throw new ConfigInvalidException(violations);
            }
            if (countries.putIfAbsent(country.code(), country) != null) {
                throw invalid(child(item("countries", i), "code"));
            }
        }
        return countries;
    }

    private static void requireUniqueTenants(List<TenantSeed> tenants) {
        var ids = new HashSet<String>();
        for (int i = 0; i < tenants.size(); i++) {
            if (!ids.add(tenants.get(i).tenant().id())) {
                throw invalid(child(item("tenants", i), "id"));
            }
        }
    }

    private static ConfigInvalidException invalid(String path) {
        return new ConfigInvalidException(List.of(violation(JsonSchema.INVALID_VALUE, path)));
    }

    private static Country country(JsonNode node) {
        JsonNode currency = node.get("currency");
        return new Country(
                node.get("code").stringValue(),
                new CurrencyConfig(
                        currency.get("code").stringValue(),
                        currency.get("scale").intValue(),
                        RoundingMode.valueOf(currency.get("roundingMode").stringValue())),
                GiftTaxPolicy.valueOf(node.get("giftTaxPolicy").stringValue()),
                node.get("taxClasses").values().stream()
                        .map(
                                taxClass ->
                                        new TaxClass(
                                                taxClass.get("code").stringValue(),
                                                decimal(taxClass, "rate")))
                        .toList());
    }

    private static void requireKnownCountries(JsonNode tenants, Map<String, Country> countries) {
        var violations = new ArrayList<ConfigViolation>();
        for (int i = 0; i < tenants.size(); i++) {
            if (!countries.containsKey(tenants.get(i).get("country").stringValue())) {
                violations.add(violation(UNKNOWN_COUNTRY, child(item("tenants", i), "country")));
            }
        }
        if (!violations.isEmpty()) {
            throw new ConfigInvalidException(violations);
        }
    }

    private static TenantSeed tenant(JsonNode node, Country country) {
        var tenant =
                new Tenant(
                        node.get("id").stringValue(),
                        country,
                        node.get("brand").stringValue(),
                        node.get("orderNumberPrefix").stringValue(),
                        Duration.ofSeconds(node.get("quoteTtlSeconds").intValue()),
                        Duration.ofSeconds(node.get("idempotencyTtlSeconds").intValue()));
        List<DistributorSeed> distributors =
                node.get("distributors").values().stream()
                        .map(
                                distributor ->
                                        new DistributorSeed(
                                                distributor.get("id").stringValue(),
                                                distributor.get("name").stringValue()))
                        .toList();
        List<CustomerSeed> customers =
                node.get("customers").values().stream()
                        .map(customer -> customer(customer, country))
                        .toList();
        return new TenantSeed(tenant, distributors, customers);
    }

    private static CustomerSeed customer(JsonNode node, Country country) {
        return new CustomerSeed(
                node.get("id").stringValue(),
                node.get("name").stringValue(),
                node.get("creditAccounts").values().stream()
                        .map(
                                account ->
                                        new CreditAccountSeed(
                                                account.get("distributorId").stringValue(),
                                                new Money(
                                                                decimal(account, "available"),
                                                                country.currency())
                                                        .amount()))
                        .toList());
    }

    private String require(String file) {
        String path = directory + "/" + file;
        return read(path)
                .orElseThrow(
                        () ->
                                new ConfigInvalidException(
                                        List.of(violation(JsonSchema.REQUIRED, path))));
    }

    private static Optional<String> read(String path) {
        try (var stream = JsonSeedSource.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
