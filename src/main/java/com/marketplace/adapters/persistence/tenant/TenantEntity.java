package com.marketplace.adapters.persistence.tenant;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import com.marketplace.domain.config.Country;
import com.marketplace.domain.config.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;

@Entity
@Table(name = "tenant")
class TenantEntity extends AssignedIdEntity<String> {

    @Id private String id;

    @Column(name = "country_code")
    private String countryCode;

    private String brand;

    @Column(name = "order_number_prefix")
    private String orderNumberPrefix;

    @Column(name = "quote_ttl_seconds")
    private int quoteTtlSeconds;

    @Column(name = "idempotency_ttl_seconds")
    private int idempotencyTtlSeconds;

    protected TenantEntity() {}

    TenantEntity(Tenant tenant) {
        this.id = tenant.id();
        this.countryCode = tenant.country().code();
        this.brand = tenant.brand();
        this.orderNumberPrefix = tenant.orderNumberPrefix();
        this.quoteTtlSeconds = Math.toIntExact(tenant.quoteTtl().toSeconds());
        this.idempotencyTtlSeconds = Math.toIntExact(tenant.idempotencyTtl().toSeconds());
    }

    @Override
    public String getId() {
        return id;
    }

    String countryCode() {
        return countryCode;
    }

    Tenant toDomain(Country country) {
        return new Tenant(
                id,
                country,
                brand,
                orderNumberPrefix,
                Duration.ofSeconds(quoteTtlSeconds),
                Duration.ofSeconds(idempotencyTtlSeconds));
    }
}
