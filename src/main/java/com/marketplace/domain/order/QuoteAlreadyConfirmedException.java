package com.marketplace.domain.order;

import com.marketplace.domain.DomainException;
import com.marketplace.domain.Identifier;

public class QuoteAlreadyConfirmedException extends DomainException {

    private static final String CODE = "QUOTE_ALREADY_CONFIRMED";
    private static final String MESSAGE = "La cotización ya fue confirmada.";

    private final String orderId;

    public QuoteAlreadyConfirmedException(String orderId) {
        super(CODE, MESSAGE);
        this.orderId = Identifier.require(orderId, "orderId");
    }

    public QuoteAlreadyConfirmedException(String orderId, Throwable cause) {
        super(CODE, MESSAGE, cause);
        this.orderId = Identifier.require(orderId, "orderId");
    }

    public String orderId() {
        return orderId;
    }
}
