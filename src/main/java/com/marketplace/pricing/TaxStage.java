package com.marketplace.pricing;

import com.marketplace.domain.config.Stage;
import com.marketplace.domain.money.Money;

final class TaxStage implements PricingStage {

    @Override
    public Stage stage() {
        return Stage.TAX;
    }

    @Override
    public void apply(PricingContext context) {
        for (LineState line : context.lines()) {
            line.tax(Money.round(line.taxableBase().times(line.taxRate()), context.currency()));
        }
        for (GiftState gift : context.gifts()) {
            if (gift.taxed()) {
                gift.tax(Money.round(gift.taxBase().times(gift.taxRate()), context.currency()));
            }
        }
    }
}
