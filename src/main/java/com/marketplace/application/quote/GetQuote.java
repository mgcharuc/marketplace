package com.marketplace.application.quote;

import com.marketplace.application.CustomerAccess;
import com.marketplace.application.port.QuoteRepository;
import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteKey;
import com.marketplace.domain.quote.QuoteNotFoundException;

/** Devuelve la cotización guardada solo al tendero que la pidió. */
public class GetQuote {

    private final CustomerAccess access;
    private final QuoteRepository quotes;

    public GetQuote(CustomerAccess access, QuoteRepository quotes) {
        this.access = access;
        this.quotes = quotes;
    }

    public Quote handle(QuoteKey key) {
        access.tenantOf(key.tenantId(), key.customerId());
        return quotes.find(key).orElseThrow(QuoteNotFoundException::new);
    }
}
