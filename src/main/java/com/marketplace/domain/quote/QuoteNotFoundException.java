package com.marketplace.domain.quote;

import com.marketplace.domain.DomainException;

public class QuoteNotFoundException extends DomainException {

    public QuoteNotFoundException() {
        super("QUOTE_NOT_FOUND", "La cotización no existe.");
    }
}
