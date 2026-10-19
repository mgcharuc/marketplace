package com.marketplace.adapters.persistence.order;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OrderEntityRepository extends JpaRepository<OrderEntity, String> {

    @Query("SELECT o.id FROM OrderEntity o WHERE o.tenantId = :tenantId AND o.quoteId = :quoteId")
    Optional<String> findIdByQuote(String tenantId, String quoteId);

    @Query(
            """
            SELECT o.responseBody FROM OrderEntity o
            WHERE o.id = :orderId AND o.tenantId = :tenantId AND o.customerId = :customerId
            """)
    Optional<String> findBody(String tenantId, String customerId, String orderId);

    @Query(nativeQuery = true, value = "SELECT nextval('order_number_seq')")
    long nextSequence();
}
