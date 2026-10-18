package com.marketplace.pricing;

import com.marketplace.domain.money.Money;
import java.util.Objects;

/**
 * Comparación del total del pedido con el cupo disponible. No reserva cupo, así que un pedido que
 * cabe al cotizar puede no caber al confirmar.
 */
public record CreditCheck(Money available, Money required, boolean fits, Money shortfall) {

    public CreditCheck {
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(shortfall, "shortfall");
        if (fits != (shortfall.amount().signum() == 0)) {
            throw new IllegalArgumentException(
                    "Un pedido cabe solo si no falta cupo: faltan " + shortfall.amount());
        }
    }
}
