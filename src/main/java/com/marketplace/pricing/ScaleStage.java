package com.marketplace.pricing;

import com.marketplace.domain.config.ScaleRule;
import com.marketplace.domain.config.Stage;
import com.marketplace.domain.money.Allocation;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ScaleStage implements PricingStage {

    private static final Comparator<LineState> MOST_EXPENSIVE_FIRST =
            Comparator.comparing((LineState line) -> line.unitPrice().amount())
                    .reversed()
                    .thenComparing(line -> line.product().sku());

    @Override
    public Stage stage() {
        return Stage.SCALE;
    }

    @Override
    public void apply(PricingContext context) {
        for (ScaleRule rule : context.rules(ScaleRule.class)) {
            List<LineState> lines =
                    context.linesIn(rule.scope()).stream()
                            .filter(line -> line.eligible() > 0)
                            .sorted(MOST_EXPENSIVE_FIRST)
                            .toList();
            ScaleWalk walk = ScaleWalk.over(rule, lines);
            walk.unitsByLine().forEach(LineState::consume);
            record(context, rule, walk);
        }
    }

    private static void record(PricingContext context, ScaleRule rule, ScaleWalk walk) {
        Money amount = context.zero();
        List<LineAmount> lineAmounts = new ArrayList<>();
        for (LineState line : context.lines()) {
            BigDecimal exact = walk.exactByLine().get(line);
            if (exact == null) {
                continue;
            }
            Money discount = Money.round(exact, context.currency());
            if (discount.amount().signum() > 0) {
                line.addScaleDiscount(rule.id(), discount);
                lineAmounts.add(new LineAmount(line.lineId(), discount));
                amount = amount.plus(discount);
            }
        }
        if (amount.amount().signum() > 0) {
            context.add(
                    new ScaleDiscount(
                            rule.id(),
                            rule.name(),
                            amount,
                            lineAmounts,
                            rule.mode(),
                            tierAmounts(amount, walk.tiers())));
        }
    }

    private static List<TierAmount> tierAmounts(Money amount, List<ScaleWalk.TierTotal> tiers) {
        var weights = new TreeMap<Integer, BigDecimal>();
        tiers.forEach(total -> weights.put(total.tier().fromQty(), total.exact()));
        Map<Integer, Money> shares = Allocation.split(amount, weights);
        return tiers.stream()
                .map(
                        total ->
                                new TierAmount(
                                        total.tier().fromQty(),
                                        total.tier().percent(),
                                        total.units(),
                                        shares.get(total.tier().fromQty())))
                .toList();
    }
}
