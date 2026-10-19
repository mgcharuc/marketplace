package com.marketplace.pricing;

import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.config.Product;
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
import java.util.stream.Stream;

final class PricingContext {

    private final PricingInput input;
    private final List<LineState> lines = new ArrayList<>();
    private final List<AppliedDiscount> discounts = new ArrayList<>();
    private final List<GiftState> gifts = new ArrayList<>();
    private CreditCheck credit;

    PricingContext(PricingInput input) {
        this.input = input;
        int position = 1;
        for (CartLine cartLine : input.cart().lines()) {
            var product = product(cartLine.sku());
            lines.add(
                    new LineState(
                            "L" + position++,
                            product,
                            cartLine.quantity().value(),
                            currency(),
                            taxRate(product)));
        }
    }

    CurrencyConfig currency() {
        return input.tenant().currency();
    }

    GiftTaxPolicy giftTaxPolicy() {
        return input.tenant().country().giftTaxPolicy();
    }

    BigDecimal taxRate(Product product) {
        return input.tenant().taxRate(product);
    }

    CreditAvailability creditAvailability() {
        return input.credit();
    }

    Money zero() {
        return new Money(BigDecimal.ZERO, currency());
    }

    Product product(Sku sku) {
        return input.config()
                .product(sku)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "El SKU " + sku.value() + " no existe en la versión"));
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

    List<GiftState> gifts() {
        return Collections.unmodifiableList(gifts);
    }

    <T extends Rule> List<T> rules(Class<T> type) {
        return input.config().rules().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(rule -> rule.activeAt(input.now()))
                .sorted(Comparator.comparingInt(Rule::priority).thenComparing(Rule::id))
                .toList();
    }

    void add(AppliedDiscount discount) {
        discounts.add(discount);
    }

    void add(GiftState gift) {
        gifts.add(gift);
    }

    void credit(CreditCheck check) {
        credit = check;
    }

    Totals totals() {
        Money gross = sum(lines.stream().map(LineState::gross));
        Money discountTotal = sum(discounts.stream().map(AppliedDiscount::amount));
        Money netSubtotal = gross.minus(discountTotal);
        List<GiftState> charged = gifts.stream().filter(GiftState::chargedToCustomer).toList();
        Money taxableBase =
                sum(
                        Stream.concat(
                                lines.stream().map(LineState::taxableBase),
                                charged.stream().map(GiftState::taxBase)));
        Money taxTotal =
                sum(
                        Stream.concat(
                                lines.stream().map(LineState::taxAmount),
                                charged.stream().map(GiftState::taxAmount)));
        return new Totals(
                gross,
                discountTotal,
                netSubtotal,
                taxableBase,
                taxTotal,
                netSubtotal.plus(taxTotal));
    }

    Breakdown breakdown() {
        return new Breakdown(
                input.config().number(),
                currency(),
                lines.stream().map(LineState::priced).toList(),
                discounts,
                gifts.stream()
                        .map(GiftState::gift)
                        .sorted(Comparator.comparing(Gift::ruleId))
                        .toList(),
                totals(),
                credit);
    }

    private Money sum(Stream<Money> amounts) {
        return amounts.reduce(zero(), Money::plus);
    }
}
