package com.marketplace.adapters.web;

import java.util.List;

class InvalidRequestException extends RuntimeException {

    private final List<ApiError> errors;

    InvalidRequestException(List<ApiError> errors) {
        super("Petición inválida");
        this.errors = List.copyOf(errors);
    }

    List<ApiError> errors() {
        return errors;
    }
}
