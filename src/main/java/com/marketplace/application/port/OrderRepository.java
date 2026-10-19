package com.marketplace.application.port;

import com.marketplace.domain.order.Order;
import com.marketplace.domain.order.OrderKey;
import java.util.Optional;

public interface OrderRepository {

    Optional<String> findIdByQuote(String tenantId, String quoteId);

    /** Siguiente valor de la secuencia global del número de orden. No bloquea ni se revierte. */
    long nextSequence();

    /** Guarda la orden de inmediato, así una orden repetida falla aquí y no en el commit. */
    void save(Order order);

    Optional<String> findBody(OrderKey key);
}
