package com.marketplace.adapters.web;

import java.util.List;

class IdentityHeadersException extends RuntimeException {

    private final List<ApiError> errors;

    IdentityHeadersException(List<ApiError> errors) {
        super("Cabeceras de identidad inválidas");
        this.errors = List.copyOf(errors);
    }

    List<ApiError> errors() {
        return errors;
    }
}
