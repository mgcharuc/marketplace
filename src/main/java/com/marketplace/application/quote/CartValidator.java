package com.marketplace.application.quote;

import com.marketplace.application.quote.QuoteCartCommand.Item;
import com.marketplace.domain.Identifier;
import com.marketplace.domain.catalog.Quantity;
import com.marketplace.domain.catalog.Sku;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.quote.CartError;
import com.marketplace.domain.quote.InvalidCartException;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.pricing.Cart;
import com.marketplace.pricing.CartLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class CartValidator {

    private static final int MAX_ITEMS = 200;
    private static final Comparator<CartError> ORDER =
            Comparator.comparing(CartError::index, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(
                            CartError::field, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(CartError::code);

    private final ConfigVersion config;

    CartValidator(ConfigVersion config) {
        this.config = config;
    }

    ValidCart validate(List<Item> items, String paymentMethod) {
        var errors = new ArrayList<CartError>();
        if (items == null || items.isEmpty()) {
            errors.add(new CartError(null, "items", null, "EMPTY_CART"));
        } else if (items.size() > MAX_ITEMS) {
            errors.add(new CartError(null, "items", null, "TOO_MANY_ITEMS"));
        }
        PaymentMethod method = paymentMethod(paymentMethod, errors);
        Map<String, Consolidated> lines = consolidate(items == null ? List.of() : items, errors);
        if (!errors.isEmpty()) {
            throw new InvalidCartException(errors.stream().sorted(ORDER).toList());
        }
        return new ValidCart(cart(lines), method);
    }

    private static PaymentMethod paymentMethod(String value, List<CartError> errors) {
        return Arrays.stream(PaymentMethod.values())
                .filter(method -> method.name().equals(value))
                .findFirst()
                .orElseGet(
                        () -> {
                            errors.add(
                                    new CartError(
                                            null, "paymentMethod", null, "INVALID_PAYMENT_METHOD"));
                            return null;
                        });
    }

    private Map<String, Consolidated> consolidate(List<Item> items, List<CartError> errors) {
        var lines = new LinkedHashMap<String, Consolidated>();
        for (int index = 0; index < items.size(); index++) {
            check(index, items.get(index), lines, errors);
        }
        lines.forEach(
                (sku, line) -> {
                    if (!Quantity.isValid(line.total)) {
                        errors.add(
                                new CartError(
                                        line.firstIndex, "quantity", sku, "INVALID_QUANTITY"));
                    }
                });
        return lines;
    }

    private void check(
            int index, Item item, Map<String, Consolidated> lines, List<CartError> errors) {
        if (item == null) {
            errors.add(new CartError(index, null, null, "INVALID_ITEM"));
            return;
        }
        String sku = item.sku();
        boolean validQuantity = item.quantity() != null && Quantity.isValid(item.quantity());
        if (!validQuantity) {
            errors.add(new CartError(index, "quantity", sku, "INVALID_QUANTITY"));
        }
        String skuError = skuError(sku);
        if (skuError != null) {
            errors.add(new CartError(index, "sku", sku, skuError));
        } else if (validQuantity) {
            lines.computeIfAbsent(sku, key -> new Consolidated(index)).add(item.quantity());
        }
    }

    private String skuError(String sku) {
        if (sku == null) {
            return "MISSING_SKU";
        }
        if (!Identifier.isValid(sku)) {
            return "INVALID_SKU";
        }
        if (config.product(new Sku(sku)).isEmpty()) {
            return "UNKNOWN_SKU";
        }
        return null;
    }

    private static Cart cart(Map<String, Consolidated> lines) {
        var cartLines = new ArrayList<CartLine>();
        lines.forEach(
                (sku, line) ->
                        cartLines.add(
                                new CartLine(
                                        new Sku(sku), new Quantity(Math.toIntExact(line.total)))));
        return new Cart(cartLines);
    }

    private static final class Consolidated {

        private final int firstIndex;
        private long total;

        private Consolidated(int firstIndex) {
            this.firstIndex = firstIndex;
        }

        private void add(int quantity) {
            total += quantity;
        }
    }
}
