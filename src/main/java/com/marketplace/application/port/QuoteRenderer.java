package com.marketplace.application.port;

import com.marketplace.domain.quote.QuoteHeader;
import com.marketplace.pricing.Breakdown;

public interface QuoteRenderer {

    /** Cuerpo de la respuesta de la cotización, que se guarda tal cual como su snapshot. */
    String render(QuoteHeader header, Breakdown breakdown);
}
