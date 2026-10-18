package com.marketplace.pricing;

import com.marketplace.domain.config.Product;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;

final class GiftState {

    private final String ruleId;
    private final Product product;
    private final int quantity;
    private final Money unitPrice;
    private final BigDecimal taxRate;
    private final TaxBearer bearer;
    private Money taxAmount;

    GiftState(
            String ruleId,
            Product product,
            int quantity,
            CurrencyConfig currency,
            BigDecimal taxRate,
            TaxBearer bearer) {
        this.ruleId = ruleId;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = new Money(product.listPrice(), currency);
        this.taxRate = taxRate;
        this.bearer = bearer;
        this.taxAmount = new Money(BigDecimal.ZERO, currency);
    }

    boolean taxed() {
        return bearer != null;
    }

    boolean chargedToCustomer() {
        return bearer == TaxBearer.CUSTOMER;
    }

    BigDecimal taxRate() {
        return taxRate;
    }

    Money taxBase() {
        return unitPrice.forUnits(quantity);
    }

    Money taxAmount() {
        return taxAmount;
    }

    void tax(Money amount) {
        taxAmount = amount;
    }

    Gift gift() {
        return new Gift(
                ruleId,
                product.sku(),
                product.name(),
                quantity,
                unitPrice,
                taxBase(),
                taxRate,
                taxAmount,
                bearer);
    }
}
