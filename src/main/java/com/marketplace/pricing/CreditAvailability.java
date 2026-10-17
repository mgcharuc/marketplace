package com.marketplace.pricing;

/** Cupo de crédito que recibe el motor. Una compra de contado usa {@link #none()}. */
public sealed interface CreditAvailability permits CreditAvailability.None {

    static CreditAvailability none() {
        return new None();
    }

    record None() implements CreditAvailability {}
}
