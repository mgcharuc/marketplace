package com.marketplace.pricing;

import com.marketplace.domain.money.Money;
import java.util.Objects;

/**
 * Cupo de crédito que recibe el motor. Una compra de contado usa {@link #none()} y una a crédito
 * usa {@link #available(Money)} con el cupo disponible del tendero con ese distribuidor.
 */
public sealed interface CreditAvailability
        permits CreditAvailability.None, CreditAvailability.Available {

    static CreditAvailability none() {
        return new None();
    }

    static CreditAvailability available(Money amount) {
        return new Available(amount);
    }

    record None() implements CreditAvailability {}

    record Available(Money amount) implements CreditAvailability {

        public Available {
            Objects.requireNonNull(amount, "amount");
        }
    }
}
