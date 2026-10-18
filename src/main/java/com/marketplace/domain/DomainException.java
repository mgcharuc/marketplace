package com.marketplace.domain;

import java.util.Objects;

/** Rechazo de negocio con un código estable que el cliente puede interpretar. */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public String code() {
        return code;
    }
}
