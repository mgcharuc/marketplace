package com.marketplace.adapters.persistence.tenant;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "distributor")
class DistributorEntity extends AssignedIdEntity<TenantScopedId> {

    @EmbeddedId private TenantScopedId id;

    private String name;

    protected DistributorEntity() {}

    DistributorEntity(TenantScopedId id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public TenantScopedId getId() {
        return id;
    }
}
