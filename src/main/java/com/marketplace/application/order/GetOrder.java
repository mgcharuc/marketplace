package com.marketplace.application.order;

import com.marketplace.application.CustomerAccess;
import com.marketplace.application.port.OrderRepository;
import com.marketplace.domain.order.OrderKey;
import com.marketplace.domain.order.OrderNotFoundException;

/** Devuelve el cuerpo guardado de la orden solo al tendero que la confirmó. */
public class GetOrder {

    private final CustomerAccess access;
    private final OrderRepository orders;

    public GetOrder(CustomerAccess access, OrderRepository orders) {
        this.access = access;
        this.orders = orders;
    }

    public String handle(OrderKey key) {
        access.tenantOf(key.tenantId(), key.customerId());
        return orders.findBody(key).orElseThrow(OrderNotFoundException::new);
    }
}
