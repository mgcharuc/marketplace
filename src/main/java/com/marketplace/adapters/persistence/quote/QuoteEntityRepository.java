package com.marketplace.adapters.persistence.quote;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface QuoteEntityRepository extends JpaRepository<QuoteEntity, String> {

    Optional<QuoteEntity> findByIdAndTenantIdAndCustomerId(
            String id, String tenantId, String customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QuoteEntity> findLockedByIdAndTenantIdAndCustomerId(
            String id, String tenantId, String customerId);
}
