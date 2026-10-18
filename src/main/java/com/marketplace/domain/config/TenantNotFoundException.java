package com.marketplace.domain.config;

import com.marketplace.domain.DomainException;

public class TenantNotFoundException extends DomainException {

    public TenantNotFoundException() {
        super("TENANT_NOT_FOUND", "El tenant no existe.");
    }
}
