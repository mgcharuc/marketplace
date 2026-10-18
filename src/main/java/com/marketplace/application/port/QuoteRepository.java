package com.marketplace.application.port;

import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteKey;
import java.util.Optional;

public interface QuoteRepository {

    void save(Quote quote);

    Optional<Quote> find(QuoteKey key);
}
