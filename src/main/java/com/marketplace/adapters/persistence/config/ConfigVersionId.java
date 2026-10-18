package com.marketplace.adapters.persistence.config;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
record ConfigVersionId(
        @Column(name = "tenant_id") String tenantId, @Column(name = "version") int version) {}
