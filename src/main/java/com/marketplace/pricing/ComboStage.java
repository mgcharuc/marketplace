package com.marketplace.pricing;

import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.ComboComponent;
import com.marketplace.domain.config.ComboRule;
import com.marketplace.domain.config.Stage;
import com.marketplace.domain.money.Allocation;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ComboStage implements PricingStage {

    @Override
    public Stage stage() {
        return Stage.COMBO;
    }

    @Override
    public void apply(PricingContext context) {
        for (ComboRule rule : context.rules(ComboRule.class)) {
            int applications = applications(context, rule);
            if (applications > 0) {
                applyCombo(context, rule, applications);
            }
        }
    }

    private static int applications(PricingContext context, ComboRule rule) {
        return rule.components().stream()
                .mapToInt(
                        component ->
                                context.line(component.sku())
                                        .map(line -> line.eligible() / component.qty())
                                        .orElse(0))
                .min()
                .orElse(0);
    }

    private static void applyCombo(PricingContext context, ComboRule rule, int applications) {
        Money listValue = context.zero();
        Map<Sku, BigDecimal> consumedValue = new LinkedHashMap<>();
        for (ComboComponent component : rule.components()) {
            LineState line = context.line(component.sku()).orElseThrow();
            Money setValue =
                    new Money(
                            line.unitPrice().times(BigDecimal.valueOf(component.qty())),
                            context.currency());
            listValue = listValue.plus(setValue);
            consumedValue.merge(
                    component.sku(),
                    setValue.times(BigDecimal.valueOf(applications)),
                    BigDecimal::add);
            line.consume(component.qty() * applications);
        }
        Money comboPrice = new Money(rule.comboPrice(), context.currency());
        Money amount =
                Money.round(
                        listValue.minus(comboPrice).times(BigDecimal.valueOf(applications)),
                        context.currency());
        if (amount.amount().signum() == 0) {
            return;
        }
        Map<Sku, Money> shares = Allocation.split(amount, consumedValue);
        List<LineAmount> lineAmounts = new ArrayList<>();
        for (LineState line : context.lines()) {
            Money share = shares.get(line.product().sku());
            if (share != null && share.amount().signum() > 0) {
                line.addAllocation(rule.id(), share);
                lineAmounts.add(new LineAmount(line.lineId(), share));
            }
        }
        context.add(
                new ComboDiscount(
                        rule.id(),
                        rule.name(),
                        applications,
                        rule.components(),
                        listValue,
                        comboPrice,
                        amount,
                        lineAmounts));
    }
}
