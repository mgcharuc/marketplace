package com.marketplace.application;

import com.marketplace.application.port.CustomerRepository;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.domain.config.CustomerNotFoundException;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.config.TenantNotFoundException;

/** Comprueba que el tenant y el tendero de la petición existen antes de atenderla. */
public class CustomerAccess {

    private final TenantRepository tenants;
    private final CustomerRepository customers;

    public CustomerAccess(TenantRepository tenants, CustomerRepository customers) {
        this.tenants = tenants;
        this.customers = customers;
    }

    public Tenant tenantOf(String tenantId, String customerId) {
        Tenant tenant = tenants.find(tenantId).orElseThrow(TenantNotFoundException::new);
        if (!customers.exists(tenantId, customerId)) {
            throw new CustomerNotFoundException();
        }
        return tenant;
    }
}
