package com.marketplace.adapters.persistence.order;

import com.marketplace.adapters.persistence.StoredAmounts;
import com.marketplace.application.port.OrderRepository;
import com.marketplace.application.port.PageQuery;
import com.marketplace.application.port.PageResult;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.order.Order;
import com.marketplace.domain.order.OrderKey;
import com.marketplace.domain.order.OrderStatus;
import com.marketplace.domain.order.OrderSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class JpaOrderRepository implements OrderRepository {

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("confirmedAt"), Sort.Order.desc("orderSeq"));

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

    @Override
    public PageResult<OrderSummary> history(
            String tenantId, String customerId, PageQuery page, CurrencyConfig currency) {
        if ((long) page.page() * page.size() > Integer.MAX_VALUE) {
            long total = orders.countByTenantIdAndCustomerId(tenantId, customerId);
            return new PageResult<>(List.of(), page.page(), page.size(), total);
        }
        Page<OrderSummaryRow> rows =
                orders.findByTenantIdAndCustomerId(
                        tenantId,
                        customerId,
                        PageRequest.of(page.page(), page.size(), NEWEST_FIRST));
        List<OrderSummary> items = rows.stream().map(row -> summary(row, currency)).toList();
        return new PageResult<>(items, page.page(), page.size(), rows.getTotalElements());
    }

    private static OrderSummary summary(OrderSummaryRow row, CurrencyConfig currency) {
        return new OrderSummary(
                row.getId(),
                row.getOrderNumber(),
                row.getDistributorId(),
                row.getConfirmedAt(),
                StoredAmounts.money(row.getTotal(), currency),
                OrderStatus.valueOf(row.getStatus()));
    }
}
