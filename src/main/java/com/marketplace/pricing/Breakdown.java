package com.marketplace.pricing;

import com.marketplace.domain.money.CurrencyConfig;
import java.util.List;

/**
 * Resultado del cálculo: cada línea con sus descuentos e impuesto, los descuentos y los totales.
 */
public record Breakdown(
        int configVersion,
        CurrencyConfig currency,
        List<PricedLine> lines,
        List<AppliedDiscount> discounts,
        Totals totals) {

    public Breakdown {
        lines = List.copyOf(lines);
        discounts = List.copyOf(discounts);
    }
}
