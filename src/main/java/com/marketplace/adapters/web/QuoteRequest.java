package com.marketplace.adapters.web;

import com.marketplace.application.quote.QuoteCartCommand;
import com.marketplace.domain.Identifier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

record QuoteRequest(
        @NotNull(message = "REQUIRED")
                @Pattern(regexp = Identifier.REGEX, message = "INVALID_FORMAT")
                String distributorId,
        @NotNull(message = "REQUIRED") String paymentMethod,
        List<QuoteItemRequest> items) {

    QuoteRequest normalized() {
        return new QuoteRequest(
                Text.normalize(distributorId), Text.blankAsNull(paymentMethod), items);
    }

    QuoteCartCommand toCommand(RequestIdentity identity) {
        List<QuoteCartCommand.Item> commandItems =
                items == null
                        ? null
                        : items.stream().map(item -> item == null ? null : item.toItem()).toList();
        return new QuoteCartCommand(
                identity.tenantId(),
                identity.customerId(),
                distributorId,
                paymentMethod,
                commandItems);
    }
}
