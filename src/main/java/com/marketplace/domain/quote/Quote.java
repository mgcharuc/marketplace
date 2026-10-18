package com.marketplace.domain.quote;

import com.marketplace.domain.money.Money;
import java.util.Objects;

/**
 * Cotización guardada con su desglose. La consulta y la confirmación devuelven el snapshot
 * guardado, sin volver a calcularlo.
 */
public record Quote(QuoteHeader header, Money total, String snapshot) {

    public Quote {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!total.currency().equals(header.currency())) {
            throw new IllegalArgumentException(
                    "El total está en "
                            + total.currency().code()
                            + " y la cotización en "
                            + header.currency().code());
        }
    }
}
