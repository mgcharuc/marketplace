package com.marketplace.adapters.web;

import com.marketplace.application.quote.QuoteCartCommand;

record QuoteItemRequest(String sku, Integer quantity) {

    QuoteCartCommand.Item toItem() {
        return new QuoteCartCommand.Item(Text.normalize(sku), quantity);
    }
}
