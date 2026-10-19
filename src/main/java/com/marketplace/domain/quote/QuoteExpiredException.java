package com.marketplace.domain.quote;

import com.marketplace.domain.DomainException;
import java.time.Instant;
import java.util.Objects;

public class QuoteExpiredException extends DomainException {

    private final Instant expiresAt;

    public QuoteExpiredException(Instant expiresAt) {
        super("QUOTE_EXPIRED", "La cotización venció. Hay que cotizar de nuevo.");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
