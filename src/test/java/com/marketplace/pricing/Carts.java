package com.marketplace.pricing;

import com.marketplace.domain.catalog.Quantity;
import com.marketplace.domain.catalog.Sku;
import java.util.ArrayList;

public final class Carts {

    private Carts() {}

    public static Cart cart(Object... skuAndQuantity) {
        var lines = new ArrayList<CartLine>();
        for (int i = 0; i < skuAndQuantity.length; i += 2) {
            lines.add(
                    new CartLine(
                            new Sku((String) skuAndQuantity[i]),
                            new Quantity((Integer) skuAndQuantity[i + 1])));
        }
        return new Cart(lines);
    }
}
