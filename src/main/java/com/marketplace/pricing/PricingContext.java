package com.marketplace.pricing;

import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.Rule;
import com.marketplace.domain.config.Scope;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class PricingContext {

    private final PricingInput input;
    private final List<LineState> lines = new ArrayList<>();
    private final List<AppliedDiscount> discounts = new ArrayList<>();

    PricingContext(PricingInput input) {
        this.input = input;
        int position = 1;
        for (CartLine cartLine : input.cart().lines()) {
            var product = input.config().product(cartLine.sku()).orElseThrow();
            lines.add(
                    new LineState(
                            "L" + position++,
                            product,
                            cartLine.quantity().value(),
                            currency(),
                            input.tenant().taxRate(product)));
        }
    }

    CurrencyConfig currency() {
        return input.tenant().currency();
    }

    Money zero() {
        return new Money(BigDecimal.ZERO, currency());
    }

    List<LineState> lines() {
        return Collections.unmodifiableList(lines);
    }

    Optional<LineState> line(Sku sku) {
        return lines.stream().filter(line -> line.product().sku().equals(sku)).findFirst();
    }

    List<LineState> linesIn(Scope scope) {
        return lines.stream().filter(line -> scope.covers(line.product())).toList();
    }

    <T extends Rule> List<T> rules(Class<T> type) {
        return input.config().rules().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .sorted(Comparator.comparingInt(Rule::priority).thenComparing(Rule::id))
                .toList();
    }

    void add(AppliedDiscount discount) {
        discounts.add(discount);
    }

    Breakdown breakdown() {
        List<PricedLine> priced = lines.stream().map(LineState::priced).toList();
        Money gross = sum(priced.stream().map(PricedLine::grossAmount).toList());
        Money discountTotal = sum(discounts.stream().map(AppliedDiscount::amount).toList());
        Money netSubtotal = gross.minus(discountTotal);
        Money taxableBase = sum(priced.stream().map(PricedLine::taxableBase).toList());
        Money taxTotal = sum(priced.stream().map(PricedLine::taxAmount).toList());
        return new Breakdown(
                input.config().number(),
                currency(),
                priced,
                discounts,
                new Totals(
                        gross,
                        discountTotal,
                        netSubtotal,
                        taxableBase,
                        taxTotal,
                        netSubtotal.plus(taxTotal)));
    }

    private Money sum(List<Money> amounts) {
        Money total = zero();
        for (Money amount : amounts) {
            total = total.plus(amount);
        }
        return total;
    }
}
