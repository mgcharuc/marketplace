package com.marketplace.adapters.persistence.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
record TenantScopedId(
        @Column(name = "tenant_id") String tenantId, @Column(name = "id") String id) {}
