package com.marketplace.pricing;

import com.marketplace.domain.money.CurrencyConfig;
import java.util.List;

/**
 * Resultado del cálculo: cada línea con sus descuentos e impuesto, los descuentos, los obsequios y
 * los totales. {@code credit} es {@code null} en una compra de contado.
 */
public record Breakdown(
        int configVersion,
        CurrencyConfig currency,
        List<PricedLine> lines,
        List<AppliedDiscount> discounts,
        List<Gift> gifts,
        Totals totals,
        CreditCheck credit) {

    public Breakdown {
        lines = List.copyOf(lines);
        discounts = List.copyOf(discounts);
        gifts = List.copyOf(gifts);
    }
}
