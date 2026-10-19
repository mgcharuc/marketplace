package com.marketplace.adapters.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

record OrderRequest(
        @NotNull(message = "REQUIRED")
                @Pattern(regexp = IdFormat.QUOTE_ID, message = "INVALID_FORMAT")
                String quoteId) {

    OrderRequest normalized() {
        return new OrderRequest(Text.normalize(quoteId));
    }
}
