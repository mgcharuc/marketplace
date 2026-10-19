package com.marketplace.adapters.persistence.order;

import com.marketplace.application.port.OrderRepository;
import com.marketplace.domain.order.Order;
import com.marketplace.domain.order.OrderKey;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaOrderRepository implements OrderRepository {

    private final OrderEntityRepository orders;

    JpaOrderRepository(OrderEntityRepository orders) {
        this.orders = orders;
    }

    @Override
    public Optional<String> findIdByQuote(String tenantId, String quoteId) {
        return orders.findIdByQuote(tenantId, quoteId);
    }

    @Override
    public long nextSequence() {
        return orders.nextSequence();
    }

    @Override
    public void save(Order order) {
        orders.saveAndFlush(new OrderEntity(order));
    }

    @Override
    public Optional<String> findBody(OrderKey key) {
        return orders.findBody(key.tenantId(), key.customerId(), key.orderId());
    }
}
