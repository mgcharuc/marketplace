package com.marketplace.application.port;

import com.marketplace.domain.Identifier;
import java.math.BigDecimal;
import java.util.Objects;

public record CreditAccountSeed(String distributorId, BigDecimal available) {

    public CreditAccountSeed {
        Identifier.require(distributorId, "distributorId");
        Objects.requireNonNull(available, "available");
        if (available.signum() < 0) {
            throw new IllegalArgumentException("Cupo disponible negativo: " + available);
        }
    }
}
