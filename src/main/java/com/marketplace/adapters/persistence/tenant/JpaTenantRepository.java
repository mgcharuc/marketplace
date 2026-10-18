package com.marketplace.adapters.persistence.tenant;

import com.marketplace.application.port.TenantRepository;
import com.marketplace.domain.config.Country;
import com.marketplace.domain.config.Tenant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaTenantRepository implements TenantRepository {

    private final TenantEntityRepository tenants;
    private final CountryEntityRepository countries;

    JpaTenantRepository(TenantEntityRepository tenants, CountryEntityRepository countries) {
        this.tenants = tenants;
        this.countries = countries;
    }

    @Override
    public Optional<Tenant> find(String tenantId) {
        return tenants.findById(tenantId)
                .map(tenant -> tenant.toDomain(country(tenant.countryCode())));
    }

    @Override
    public void add(Tenant tenant) {
        if (!countries.existsById(tenant.country().code())) {
            countries.save(new CountryEntity(tenant.country()));
        }
        tenants.save(new TenantEntity(tenant));
    }

    private Country country(String code) {
        return countries
                .findById(code)
                .map(CountryEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException("País sin registrar: " + code));
    }
}
