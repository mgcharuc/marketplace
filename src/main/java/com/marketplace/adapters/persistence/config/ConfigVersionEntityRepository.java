package com.marketplace.adapters.persistence.config;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface ConfigVersionEntityRepository
        extends JpaRepository<ConfigVersionEntity, ConfigVersionId> {

    Optional<ConfigVersionEntity> findByIdTenantIdAndCurrentTrue(String tenantId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE ConfigVersionEntity v SET v.current = false"
                    + " WHERE v.id.tenantId = :tenantId AND v.current = true")
    void retireCurrent(String tenantId);
}
