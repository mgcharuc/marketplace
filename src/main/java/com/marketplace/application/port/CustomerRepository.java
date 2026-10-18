package com.marketplace.application.port;

public interface CustomerRepository {

    boolean exists(String tenantId, String customerId);

    /** Registra el tendero sin sus cuentas de crédito, que se abren en {@link CreditRepository}. */
    void add(String tenantId, CustomerSeed customer);
}
