package com.marketplace.domain.config;

import com.marketplace.domain.catalog.Sku;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Revisa que una versión comercial se pueda publicar para el país de su tenant. Informa todas las
 * violaciones a la vez, en el orden de las reglas, para que se corrijan en una sola pasada.
 */
public class ConfigValidator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_RATE_SCALE = 4;

    public List<ConfigViolation> validate(Country country) {
        var violations = new ArrayList<ConfigViolation>();
        for (TaxClass taxClass : country.taxClasses()) {
            if (!isValidRate(taxClass.rate())) {
                violations.add(Check.TAX_RATE.violation(taxClass.code()));
            }
        }
        return violations;
    }

    public List<ConfigViolation> validate(ConfigVersion version, Country country) {
        var violations = new ArrayList<ConfigViolation>();
        checkTiers(version, violations);
        checkComboPrices(version, violations);
        checkTaxClasses(version, country, violations);
        checkRuleReferences(version, violations);
        checkListPrices(version, country, violations);
        checkPriorities(version, violations);
        checkValidity(version, violations);
        return violations;
    }

    private static boolean isValidRate(BigDecimal rate) {
        return rate.signum() >= 0
                && rate.compareTo(BigDecimal.ONE) <= 0
                && rate.scale() <= MAX_RATE_SCALE;
    }

    private static void checkTiers(ConfigVersion version, List<ConfigViolation> violations) {
        rulesOf(version, ScaleRule.class)
                .filter(rule -> !hasValidTiers(rule.tiers()))
                .forEach(rule -> violations.add(Check.SCALE_TIERS.violation(rule.id())));
    }

    private static boolean hasValidTiers(List<Tier> tiers) {
        if (tiers.isEmpty()) {
            return false;
        }
        int previous = 0;
        for (Tier tier : tiers) {
            boolean percentInRange =
                    tier.percent().signum() > 0 && tier.percent().compareTo(HUNDRED) < 0;
            if (tier.fromQty() <= previous || !percentInRange) {
                return false;
            }
            previous = tier.fromQty();
        }
        return true;
    }

    private static void checkComboPrices(ConfigVersion version, List<ConfigViolation> violations) {
        rulesOf(version, ComboRule.class)
                .filter(rule -> isKnown(version, rule))
                .filter(rule -> !isBelowList(rule, listValue(version, rule)))
                .forEach(rule -> violations.add(Check.COMBO_PRICE.violation(rule.id())));
    }

    private static boolean isKnown(ConfigVersion version, ComboRule rule) {
        return rule.components().stream()
                .allMatch(component -> version.product(component.sku()).isPresent());
    }

    private static BigDecimal listValue(ConfigVersion version, ComboRule rule) {
        return rule.components().stream()
                .map(
                        component ->
                                version.product(component.sku())
                                        .orElseThrow()
                                        .listPrice()
                                        .multiply(BigDecimal.valueOf(component.qty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isBelowList(ComboRule rule, BigDecimal listValue) {
        return rule.comboPrice().signum() > 0 && rule.comboPrice().compareTo(listValue) < 0;
    }

    private static void checkTaxClasses(
            ConfigVersion version, Country country, List<ConfigViolation> violations) {
        version.products().stream()
                .filter(product -> country.taxClass(product.taxClass()).isEmpty())
                .forEach(
                        product ->
                                violations.add(
                                        Check.UNKNOWN_REFERENCE.violation(product.sku().value())));
    }

    private static void checkRuleReferences(
            ConfigVersion version, List<ConfigViolation> violations) {
        version.rules().stream()
                .filter(rule -> !referencesExist(version, rule))
                .forEach(rule -> violations.add(Check.UNKNOWN_REFERENCE.violation(rule.id())));
    }

    private static boolean referencesExist(ConfigVersion version, Rule rule) {
        return switch (rule) {
            case ScaleRule scale -> !version.productsIn(scale.scope()).isEmpty();
            case ComboRule combo -> isKnown(version, combo);
            case GiftRule gift ->
                    !version.productsIn(gift.condition().scope()).isEmpty()
                            && version.product(gift.reward().sku()).isPresent();
        };
    }

    private static void checkListPrices(
            ConfigVersion version, Country country, List<ConfigViolation> violations) {
        int scale = country.currency().scale();
        version.products().stream()
                .filter(
                        product ->
                                product.listPrice().signum() < 0
                                        || product.listPrice().scale() > scale)
                .forEach(
                        product ->
                                violations.add(Check.LIST_PRICE.violation(product.sku().value())));
    }

    private static void checkPriorities(ConfigVersion version, List<ConfigViolation> violations) {
        List<Rule> rules = version.rules();
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                Rule first = rules.get(i);
                Rule second = rules.get(j);
                if (compete(version, first, second)) {
                    String pair =
                            Stream.of(first.id(), second.id())
                                    .sorted()
                                    .collect(Collectors.joining(","));
                    violations.add(Check.PRIORITY.violation(pair));
                }
            }
        }
    }

    private static boolean compete(ConfigVersion version, Rule first, Rule second) {
        if (first.stage() != second.stage() || first.priority() != second.priority()) {
            return false;
        }
        Set<Sku> shared = new HashSet<>(skusOf(version, first));
        shared.retainAll(skusOf(version, second));
        return !shared.isEmpty();
    }

    private static Set<Sku> skusOf(ConfigVersion version, Rule rule) {
        return switch (rule) {
            case ScaleRule scale -> skusIn(version, scale.scope());
            case ComboRule combo ->
                    combo.components().stream()
                            .map(ComboComponent::sku)
                            .collect(Collectors.toSet());
            case GiftRule gift -> skusIn(version, gift.condition().scope());
        };
    }

    private static Set<Sku> skusIn(ConfigVersion version, Scope scope) {
        return version.productsIn(scope).stream().map(Product::sku).collect(Collectors.toSet());
    }

    private static void checkValidity(ConfigVersion version, List<ConfigViolation> violations) {
        version.rules().stream()
                .filter(rule -> rule.validFrom() != null && rule.validTo() != null)
                .filter(rule -> !rule.validFrom().isBefore(rule.validTo()))
                .forEach(rule -> violations.add(Check.VALIDITY.violation(rule.id())));
    }

    private static <T extends Rule> Stream<T> rulesOf(ConfigVersion version, Class<T> type) {
        return version.rules().stream().filter(type::isInstance).map(type::cast);
    }

    private enum Check {
        SCALE_TIERS("SCALE_INVALID_TIERS"),
        COMBO_PRICE("COMBO_PRICE_NOT_BELOW_LIST"),
        UNKNOWN_REFERENCE("RULE_UNKNOWN_REFERENCE"),
        TAX_RATE("TAX_RATE_INVALID"),
        LIST_PRICE("PRICE_INVALID_SCALE"),
        PRIORITY("RULE_PRIORITY_AMBIGUOUS"),
        VALIDITY("RULE_INVALID_VALIDITY");

        private final String code;

        Check(String code) {
            this.code = code;
        }

        ConfigViolation violation(String ref) {
            return new ConfigViolation(code, ref);
        }
    }
}
