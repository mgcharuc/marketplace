package com.marketplace.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public final class BreakdownInvariants {

    private BreakdownInvariants() {}

    public static void check(Breakdown breakdown) {
        var totals = breakdown.totals();
        var lines = breakdown.lines();
        lines.forEach(BreakdownInvariants::checkLine);
        assertThat(totals.grossSubtotal().amount())
                .isEqualByComparingTo(sum(lines, PricedLine::grossAmount));
        assertThat(totals.discountTotal().amount())
                .isEqualByComparingTo(sum(breakdown.discounts(), AppliedDiscount::amount));
        assertThat(totals.netSubtotal())
                .isEqualTo(totals.grossSubtotal().minus(totals.discountTotal()));
        assertThat(totals.netSubtotal().amount())
                .isEqualByComparingTo(sum(lines, PricedLine::taxableBase));
        breakdown.gifts().forEach(BreakdownInvariants::checkGift);
        List<Gift> charged =
                breakdown.gifts().stream()
                        .filter(gift -> gift.taxBorneBy() == TaxBearer.CUSTOMER)
                        .toList();
        assertThat(totals.taxableBase().amount())
                .isEqualByComparingTo(
                        sum(lines, PricedLine::taxableBase).add(sum(charged, Gift::taxBase)));
        assertThat(totals.taxTotal().amount())
                .isEqualByComparingTo(
                        sum(lines, PricedLine::taxAmount).add(sum(charged, Gift::taxAmount)));
        assertThat(totals.total()).isEqualTo(totals.netSubtotal().plus(totals.taxTotal()));
        assertThat(breakdown.gifts())
                .extracting(Gift::ruleId)
                .isSortedAccordingTo(Comparator.naturalOrder());
        if (breakdown.credit() != null) {
            checkCredit(breakdown.credit(), totals.total());
        }
        for (AppliedDiscount discount : breakdown.discounts()) {
            assertThat(sum(discount.lineAmounts(), LineAmount::amount))
                    .as("reparto por línea de %s", discount.ruleId())
                    .isEqualByComparingTo(discount.amount().amount());
            if (discount instanceof ScaleDiscount scale) {
                assertThat(sum(scale.tiers(), TierAmount::amount))
                        .as("tramos de %s", scale.ruleId())
                        .isEqualByComparingTo(scale.amount().amount());
            }
        }
        allAmounts(breakdown)
                .forEach(amount -> assertThat(amount.currency()).isEqualTo(breakdown.currency()));
    }

    private static void checkLine(PricedLine line) {
        Money expectedBase = line.grossAmount();
        for (RuleAmount discount : line.scaleDiscounts()) {
            expectedBase = expectedBase.minus(discount.amount());
        }
        for (RuleAmount allocation : line.taxBaseAllocations()) {
            expectedBase = expectedBase.minus(allocation.amount());
        }
        assertThat(line.grossAmount().amount())
                .isEqualByComparingTo(line.unitPrice().times(BigDecimal.valueOf(line.quantity())));
        assertThat(line.taxableBase()).isEqualTo(expectedBase);
        assertThat(line.netAmount()).isEqualTo(line.taxableBase().plus(line.taxAmount()));
    }

    private static void checkGift(Gift gift) {
        assertThat(gift.quantity()).isPositive();
        assertThat(gift.taxBase().amount())
                .isEqualByComparingTo(
                        gift.referenceUnitPrice().times(BigDecimal.valueOf(gift.quantity())));
        if (gift.taxBorneBy() == null) {
            assertThat(gift.taxRate()).isNull();
            assertThat(gift.taxAmount().amount()).isZero();
        } else {
            assertThat(gift.taxAmount())
                    .as("impuesto del obsequio de %s", gift.ruleId())
                    .isEqualTo(
                            Money.round(
                                    gift.taxBase().times(gift.taxRate()),
                                    gift.taxBase().currency()));
        }
    }

    private static void checkCredit(CreditCheck credit, Money total) {
        assertThat(credit.required()).isEqualTo(total);
        boolean fits = credit.required().amount().compareTo(credit.available().amount()) <= 0;
        assertThat(credit.fits()).isEqualTo(fits);
        assertThat(credit.shortfall().amount())
                .isEqualByComparingTo(
                        fits
                                ? BigDecimal.ZERO
                                : credit.required().minus(credit.available()).amount());
    }

    private static Stream<Money> allAmounts(Breakdown breakdown) {
        var totals = breakdown.totals();
        return Stream.of(
                        breakdown.lines().stream().flatMap(BreakdownInvariants::lineAmounts),
                        breakdown.discounts().stream()
                                .flatMap(BreakdownInvariants::discountAmounts),
                        breakdown.gifts().stream()
                                .flatMap(
                                        gift ->
                                                Stream.of(
                                                        gift.referenceUnitPrice(),
                                                        gift.taxBase(),
                                                        gift.taxAmount())),
                        creditAmounts(breakdown.credit()),
                        Stream.of(
                                totals.grossSubtotal(),
                                totals.discountTotal(),
                                totals.netSubtotal(),
                                totals.taxableBase(),
                                totals.taxTotal(),
                                totals.total()))
                .flatMap(Function.identity());
    }

    private static Stream<Money> creditAmounts(CreditCheck credit) {
        return credit == null
                ? Stream.empty()
                : Stream.of(credit.available(), credit.required(), credit.shortfall());
    }

    private static Stream<Money> lineAmounts(PricedLine line) {
        return Stream.of(
                        Stream.of(
                                line.unitPrice(),
                                line.grossAmount(),
                                line.taxableBase(),
                                line.taxAmount(),
                                line.netAmount()),
                        line.scaleDiscounts().stream().map(RuleAmount::amount),
                        line.taxBaseAllocations().stream().map(RuleAmount::amount))
                .flatMap(Function.identity());
    }

    private static Stream<Money> discountAmounts(AppliedDiscount discount) {
        Stream<Money> own =
                switch (discount) {
                    case ComboDiscount combo -> Stream.of(combo.listValue(), combo.comboPrice());
                    case ScaleDiscount scale -> scale.tiers().stream().map(TierAmount::amount);
                };
        return Stream.of(
                        Stream.of(discount.amount()),
                        discount.lineAmounts().stream().map(LineAmount::amount),
                        own)
                .flatMap(Function.identity());
    }

    private static <T> BigDecimal sum(List<T> items, Function<T, Money> amount) {
        return items.stream()
                .map(amount)
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
