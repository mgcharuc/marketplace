package com.marketplace.application.order;

import com.marketplace.application.CustomerAccess;
import com.marketplace.application.port.OrderRepository;
import com.marketplace.application.port.PageQuery;
import com.marketplace.application.port.PageResult;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.order.OrderSummary;

/** Historial de órdenes del tendero. */
public class GetOrderHistory {

    private final CustomerAccess access;
    private final OrderRepository orders;

    public GetOrderHistory(CustomerAccess access, OrderRepository orders) {
        this.access = access;
        this.orders = orders;
    }

    public PageResult<OrderSummary> handle(String tenantId, String customerId, PageQuery page) {
        Tenant tenant = access.tenantOf(tenantId, customerId);
        return orders.history(tenantId, customerId, page, tenant.currency());
    }
}
