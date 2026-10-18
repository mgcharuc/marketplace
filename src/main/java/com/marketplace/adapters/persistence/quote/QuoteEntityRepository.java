package com.marketplace.adapters.persistence.quote;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuoteEntityRepository extends JpaRepository<QuoteEntity, String> {

    Optional<QuoteEntity> findByIdAndTenantIdAndCustomerId(
            String id, String tenantId, String customerId);
}
