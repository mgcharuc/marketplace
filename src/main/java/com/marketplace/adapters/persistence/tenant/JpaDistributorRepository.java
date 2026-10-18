package com.marketplace.adapters.persistence.tenant;

import com.marketplace.application.port.DistributorRepository;
import com.marketplace.application.port.DistributorSeed;
import org.springframework.stereotype.Repository;

@Repository
class JpaDistributorRepository implements DistributorRepository {

    private final DistributorEntityRepository distributors;

    JpaDistributorRepository(DistributorEntityRepository distributors) {
        this.distributors = distributors;
    }

    @Override
    public boolean exists(String tenantId, String distributorId) {
        return distributors.existsById(new TenantScopedId(tenantId, distributorId));
    }

    @Override
    public void add(String tenantId, DistributorSeed distributor) {
        distributors.save(
                new DistributorEntity(
                        new TenantScopedId(tenantId, distributor.id()), distributor.name()));
    }
}
