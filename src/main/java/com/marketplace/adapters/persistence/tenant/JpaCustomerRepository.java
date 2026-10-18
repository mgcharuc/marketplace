package com.marketplace.adapters.persistence.tenant;

import com.marketplace.application.port.CustomerRepository;
import com.marketplace.application.port.CustomerSeed;
import org.springframework.stereotype.Repository;

@Repository
class JpaCustomerRepository implements CustomerRepository {

    private final CustomerEntityRepository customers;

    JpaCustomerRepository(CustomerEntityRepository customers) {
        this.customers = customers;
    }

    @Override
    public boolean exists(String tenantId, String customerId) {
        return customers.existsById(new TenantScopedId(tenantId, customerId));
    }

    @Override
    public void add(String tenantId, CustomerSeed customer) {
        customers.save(
                new CustomerEntity(new TenantScopedId(tenantId, customer.id()), customer.name()));
    }
}
