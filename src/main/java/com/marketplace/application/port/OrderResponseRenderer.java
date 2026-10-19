package com.marketplace.application.port;

import com.marketplace.domain.order.OrderHeader;

public interface OrderResponseRenderer {

    /**
     * Cuerpo de la respuesta de la orden, que se guarda tal cual. El desglose se copia del snapshot
     * de la cotización.
     */
    String render(OrderHeader header, String snapshot);
}
