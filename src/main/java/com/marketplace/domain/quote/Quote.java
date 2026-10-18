package com.marketplace.domain.quote;

import com.marketplace.domain.money.Money;
import java.util.Objects;

/**
 * Cotización guardada con el desglose tal como se respondió. El snapshot no se vuelve a calcular:
 * la consulta y la confirmación devuelven ese mismo texto.
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
