package com.marketplace.pricing;

import com.marketplace.domain.config.Product;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class LineState {

    private final String lineId;
    private final Product product;
    private final int quantity;
    private final Money unitPrice;
    private final BigDecimal taxRate;
    private final List<RuleAmount> scaleDiscounts = new ArrayList<>();
    private final List<RuleAmount> allocations = new ArrayList<>();
    private int eligible;
    private Money taxAmount;

    LineState(
            String lineId,
            Product product,
            int quantity,
            CurrencyConfig currency,
            BigDecimal taxRate) {
        this.lineId = lineId;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = new Money(product.listPrice(), currency);
        this.taxRate = taxRate;
        this.eligible = quantity;
        this.taxAmount = new Money(BigDecimal.ZERO, currency);
    }

    String lineId() {
        return lineId;
    }

    Product product() {
        return product;
    }

    int quantity() {
        return quantity;
    }

    Money unitPrice() {
        return unitPrice;
    }

    int eligible() {
        return eligible;
    }

    BigDecimal taxRate() {
        return taxRate;
    }

    Money gross() {
        return unitPrice.forUnits(quantity);
    }

    void consume(int units) {
        if (units > eligible) {
            throw new IllegalStateException(
                    "La línea " + lineId + " no tiene " + units + " unidades elegibles");
        }
        eligible -= units;
    }

    void addScaleDiscount(String ruleId, Money amount) {
        scaleDiscounts.add(new RuleAmount(ruleId, amount));
    }

    void addAllocation(String ruleId, Money amount) {
        allocations.add(new RuleAmount(ruleId, amount));
    }

    Money taxableBase() {
        Money base = gross();
        for (RuleAmount discount : scaleDiscounts) {
            base = base.minus(discount.amount());
        }
        for (RuleAmount allocation : allocations) {
            base = base.minus(allocation.amount());
        }
        return base;
    }

    Money taxAmount() {
        return taxAmount;
    }

    void tax(Money amount) {
        taxAmount = amount;
    }

    PricedLine priced() {
        Money base = taxableBase();
        return new PricedLine(
                lineId,
                product.sku(),
                product.name(),
                quantity,
                unitPrice,
                gross(),
                scaleDiscounts,
                allocations,
                base,
                taxRate,
                taxAmount,
                base.plus(taxAmount));
    }
}
