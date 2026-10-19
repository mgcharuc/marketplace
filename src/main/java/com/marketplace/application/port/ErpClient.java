package com.marketplace.application.port;

public interface ErpClient {

    /** Publica el pedido. El ERP descarta un segundo envío con la misma clave. */
    void send(String payload, String idempotencyKey) throws DeliveryException;
}
