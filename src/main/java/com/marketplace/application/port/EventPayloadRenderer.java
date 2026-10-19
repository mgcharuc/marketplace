package com.marketplace.application.port;

import com.marketplace.domain.order.OrderHeader;
import java.util.UUID;

public interface EventPayloadRenderer {

    /** Mensaje para el ERP, con el cuerpo completo de la orden. */
    String erp(UUID eventId, OrderHeader header, String orderBody);

    /** Notificación al tendero, con el número y el total de la orden. */
    String push(UUID eventId, OrderHeader header);
}
