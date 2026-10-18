package com.marketplace.adapters.persistence.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerEntityRepository extends JpaRepository<CustomerEntity, TenantScopedId> {}
