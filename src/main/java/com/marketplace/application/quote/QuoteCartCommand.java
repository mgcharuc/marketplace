package com.marketplace.application.quote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pedido de cotización con los textos ya recortados. Los ítems pueden faltar o venir en {@code
 * null}, porque validarlos con su posición es parte del caso de uso.
 */
public record QuoteCartCommand(
        String tenantId,
        String customerId,
        String distributorId,
        String paymentMethod,
        List<Item> items) {

    public QuoteCartCommand {
        items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
    }

    public record Item(String sku, Integer quantity) {}
}
