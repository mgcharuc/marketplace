package com.marketplace.adapters.persistence.credit;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface CreditAccountEntityRepository
        extends JpaRepository<CreditAccountEntity, CreditAccountId> {

    @Transactional
    @Query(
            nativeQuery = true,
            value =
                    """
                    UPDATE credit_account SET available = available - :amount
                    WHERE tenant_id = :#{#id.tenantId} AND customer_id = :#{#id.customerId}
                      AND distributor_id = :#{#id.distributorId} AND available >= :amount
                    RETURNING available
                    """)
    Optional<BigDecimal> consume(CreditAccountId id, BigDecimal amount);
}
