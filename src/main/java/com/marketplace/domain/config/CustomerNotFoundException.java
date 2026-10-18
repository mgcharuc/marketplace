package com.marketplace.domain.config;

import com.marketplace.domain.DomainException;

public class CustomerNotFoundException extends DomainException {

    public CustomerNotFoundException() {
        super("CUSTOMER_NOT_FOUND", "El tendero no existe en el tenant.");
    }
}
