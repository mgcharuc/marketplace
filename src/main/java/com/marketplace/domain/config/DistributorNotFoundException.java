package com.marketplace.domain.config;

import com.marketplace.domain.DomainException;

public class DistributorNotFoundException extends DomainException {

    public DistributorNotFoundException() {
        super("DISTRIBUTOR_NOT_FOUND", "El distribuidor no pertenece al tenant.");
    }
}
