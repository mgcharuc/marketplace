package com.marketplace.pricing;

import com.marketplace.domain.money.Money;
import java.util.List;

/** Descuento de una regla aplicada, con su monto total y lo que corresponde a cada línea. */
public sealed interface AppliedDiscount permits ComboDiscount, ScaleDiscount {

    String ruleId();

    String name();

    Money amount();

    List<LineAmount> lineAmounts();
}
