package com.marketplace.pricing;

import com.marketplace.domain.config.ComboComponent;
import com.marketplace.domain.money.Money;
import java.util.List;

/**
 * Combo aplicado. El valor de lista y el precio son por conjunto. El reparto entre líneas es un
 * dato fiscal para calcular la base gravable de cada SKU y no un descuento por producto.
 */
public record ComboDiscount(
        String ruleId,
        String name,
        int applications,
        List<ComboComponent> components,
        Money listValue,
        Money comboPrice,
        Money amount,
        List<LineAmount> lineAmounts)
        implements AppliedDiscount {

    public ComboDiscount {
        components = List.copyOf(components);
        lineAmounts = List.copyOf(lineAmounts);
    }
}
