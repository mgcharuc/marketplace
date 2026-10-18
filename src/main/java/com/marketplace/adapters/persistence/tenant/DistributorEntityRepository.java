package com.marketplace.adapters.persistence.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

interface DistributorEntityRepository extends JpaRepository<DistributorEntity, TenantScopedId> {}
