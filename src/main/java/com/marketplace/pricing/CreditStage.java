package com.marketplace.pricing;

import com.marketplace.domain.config.Stage;
import com.marketplace.domain.money.Money;

final class CreditStage implements PricingStage {

    @Override
    public Stage stage() {
        return Stage.CREDIT;
    }

    @Override
    public void apply(PricingContext context) {
        if (context.creditAvailability() instanceof CreditAvailability.Available(Money available)) {
            Money required = context.totals().total();
            boolean fits = required.amount().compareTo(available.amount()) <= 0;
            context.credit(
                    new CreditCheck(
                            available,
                            required,
                            fits,
                            fits ? context.zero() : required.minus(available)));
        }
    }
}
