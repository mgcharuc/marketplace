package com.marketplace.application.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.marketplace.application.quote.QuoteCartCommand.Item;
import com.marketplace.domain.config.ConfigFixtures;
import com.marketplace.domain.quote.CartError;
import com.marketplace.domain.quote.InvalidCartException;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.pricing.Carts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartValidatorTest {

    private final CartValidator validator = new CartValidator(ConfigFixtures.coMarcaA(1));

    @Test
    @DisplayName("Un carrito válido se consolida por SKU en la posición de su primera aparición")
    void consolidatesRepeatedSkus() {
        var valid =
                validator.validate(
                        items(
                                new Item("GASEOSA-1500", 2),
                                new Item("SNACK-PAPAS", 3),
                                new Item("GASEOSA-1500", 1)),
                        "CASH");

        assertThat(valid.cart()).isEqualTo(Carts.cart("GASEOSA-1500", 3, "SNACK-PAPAS", 3));
        assertThat(valid.paymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    @DisplayName("Un carrito sin ítems o sin la lista de ítems se rechaza como vacío")
    void rejectsEmptyCart() {
        assertThat(errors(null, "CASH")).containsExactly(cartError("items", "EMPTY_CART"));
        assertThat(errors(List.of(), "CASH")).containsExactly(cartError("items", "EMPTY_CART"));
    }

    @Test
    @DisplayName("Más de 200 ítems antes de consolidar se rechaza")
    void rejectsTooManyItems() {
        var items = Collections.nCopies(201, new Item("GASEOSA-350", 1));

        assertThat(errors(items, "CASH")).containsExactly(cartError("items", "TOO_MANY_ITEMS"));
    }

    @Test
    @DisplayName("Doscientos ítems del mismo SKU se aceptan si la suma no pasa de 9999")
    void acceptsTwoHundredItems() {
        var items = Collections.nCopies(200, new Item("GASEOSA-350", 1));

        assertThat(validator.validate(items, "CREDIT").cart())
                .isEqualTo(Carts.cart("GASEOSA-350", 200));
    }

    @Test
    @DisplayName("Un método de pago fuera de la lista se rechaza, también si cambia de mayúsculas")
    void rejectsUnknownPaymentMethod() {
        var items = items(new Item("GASEOSA-350", 1));

        assertThat(errors(items, "TRANSFER"))
                .containsExactly(cartError("paymentMethod", "INVALID_PAYMENT_METHOD"));
        assertThat(errors(items, "cash"))
                .containsExactly(cartError("paymentMethod", "INVALID_PAYMENT_METHOD"));
    }

    @Test
    @DisplayName("Cada ítem inválido se informa con su posición original, su campo y su SKU")
    void reportsEachInvalidItem() {
        var items =
                items(
                        new Item("GASEOSA-350", 1),
                        null,
                        new Item(null, 1),
                        new Item(null, 1),
                        new Item("GASEOSA 350", 1),
                        new Item("NO-EXISTE", 1),
                        new Item("gaseosa-350", 1),
                        new Item("JUGO-1L", 0),
                        new Item("JUGO-1L", 10000),
                        new Item("SNACK-PAPAS", null));

        assertThat(errors(items, "CASH"))
                .containsExactly(
                        new CartError(1, null, null, "INVALID_ITEM"),
                        new CartError(2, "sku", null, "MISSING_SKU"),
                        new CartError(3, "sku", null, "MISSING_SKU"),
                        new CartError(4, "sku", "GASEOSA 350", "INVALID_SKU"),
                        new CartError(5, "sku", "NO-EXISTE", "UNKNOWN_SKU"),
                        new CartError(6, "sku", "gaseosa-350", "UNKNOWN_SKU"),
                        new CartError(7, "quantity", "JUGO-1L", "INVALID_QUANTITY"),
                        new CartError(8, "quantity", "JUGO-1L", "INVALID_QUANTITY"),
                        new CartError(9, "quantity", "SNACK-PAPAS", "INVALID_QUANTITY"));
    }

    @Test
    @DisplayName("Un SKU desconocido repetido genera un error por cada aparición")
    void reportsEveryAppearanceOfUnknownSku() {
        assertThat(errors(items(new Item("X", 1), new Item("X", 2)), "CASH"))
                .containsExactly(
                        new CartError(0, "sku", "X", "UNKNOWN_SKU"),
                        new CartError(1, "sku", "X", "UNKNOWN_SKU"));
    }

    @Test
    @DisplayName("Un ítem con SKU y cantidad inválidos informa los dos errores")
    void reportsSkuAndQuantityOfTheSameItem() {
        assertThat(errors(items(new Item("NO-EXISTE", 0)), "CASH"))
                .containsExactly(
                        new CartError(0, "quantity", "NO-EXISTE", "INVALID_QUANTITY"),
                        new CartError(0, "sku", "NO-EXISTE", "UNKNOWN_SKU"));
    }

    @Test
    @DisplayName("Si la cantidad sumada de un SKU pasa de 9999 se informa en su primera aparición")
    void rejectsConsolidatedQuantityOverLimit() {
        var items =
                items(
                        new Item("JUGO-1L", 1),
                        new Item("GASEOSA-350", 9000),
                        new Item("GASEOSA-350", 1000));

        assertThat(errors(items, "CASH"))
                .containsExactly(new CartError(1, "quantity", "GASEOSA-350", "INVALID_QUANTITY"));
    }

    @Test
    @DisplayName("Los errores del carrito completo van primero y luego los de cada ítem")
    void ordersCartErrorsBeforeItemErrors() {
        var items = new ArrayList<>(Collections.nCopies(201, new Item("GASEOSA-350", 1)));
        items.set(3, new Item("NO-EXISTE", 1));

        assertThat(errors(items, "TRANSFER"))
                .containsExactly(
                        cartError("items", "TOO_MANY_ITEMS"),
                        cartError("paymentMethod", "INVALID_PAYMENT_METHOD"),
                        new CartError(3, "sku", "NO-EXISTE", "UNKNOWN_SKU"));
    }

    private List<CartError> errors(List<Item> items, String paymentMethod) {
        return catchThrowableOfType(
                        InvalidCartException.class, () -> validator.validate(items, paymentMethod))
                .errors();
    }

    private static List<Item> items(Item... items) {
        return Arrays.asList(items);
    }

    private static CartError cartError(String field, String code) {
        return new CartError(null, field, null, code);
    }
}
