package com.marketplace.pricing;

import com.marketplace.domain.config.GiftCondition;
import com.marketplace.domain.config.GiftRule;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.config.Stage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class GiftStage implements PricingStage {

    @Override
    public Stage stage() {
        return Stage.GIFT;
    }

    @Override
    public void apply(PricingContext context) {
        TaxBearer bearer = bearer(context.giftTaxPolicy());
        for (GiftRule rule : context.rules(GiftRule.class)) {
            long quantity = quantity(rule, measure(context, rule.condition()));
            if (quantity > 0) {
                var product = context.product(rule.reward().sku());
                context.add(
                        new GiftState(
                                rule.id(),
                                product,
                                quantity,
                                context.currency(),
                                bearer == null ? null : context.taxRate(product),
                                bearer));
            }
        }
    }

    private static BigDecimal measure(PricingContext context, GiftCondition condition) {
        List<LineState> lines = context.linesIn(condition.scope());
        return switch (condition.kind()) {
            case QUANTITY -> BigDecimal.valueOf(lines.stream().mapToInt(LineState::quantity).sum());
            case NET_AMOUNT ->
                    lines.stream()
                            .map(line -> line.taxableBase().amount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
        };
    }

    private static long quantity(GiftRule rule, BigDecimal measured) {
        BigDecimal threshold = rule.condition().threshold();
        if (measured.compareTo(threshold) < 0) {
            return 0;
        }
        long times =
                rule.repeatable()
                        ? measured.divide(threshold, 0, RoundingMode.DOWN).longValueExact()
                        : 1;
        return Math.multiplyExact(times, rule.reward().qty());
    }

    private static TaxBearer bearer(GiftTaxPolicy policy) {
        return switch (policy) {
            case NONE -> null;
            case ABSORBED_BY_SELLER -> TaxBearer.SELLER;
            case CHARGED_TO_CUSTOMER -> TaxBearer.CUSTOMER;
        };
    }
}
