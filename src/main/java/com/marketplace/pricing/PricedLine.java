package com.marketplace.pricing;

import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.util.List;

public record PricedLine(
        String lineId,
        Sku sku,
        String name,
        int quantity,
        Money unitPrice,
        Money grossAmount,
        List<RuleAmount> scaleDiscounts,
        List<RuleAmount> taxBaseAllocations,
        Money taxableBase,
        BigDecimal taxRate,
        Money taxAmount,
        Money netAmount) {

    public PricedLine {
        scaleDiscounts = List.copyOf(scaleDiscounts);
        taxBaseAllocations = List.copyOf(taxBaseAllocations);
    }
}
