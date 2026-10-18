package com.marketplace.domain.quote;

import com.marketplace.domain.DomainException;
import java.util.List;

public class InvalidCartException extends DomainException {

    private final List<CartError> errors;

    public InvalidCartException(List<CartError> errors) {
        super("INVALID_CART", "El carrito tiene errores.");
        this.errors = List.copyOf(errors);
    }

    public List<CartError> errors() {
        return errors;
    }
}
