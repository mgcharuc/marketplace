package com.marketplace.application.port;

public interface PushClient {

    /** Envía la notificación. El dispositivo agrupa las que llegan con la misma clave. */
    void send(String payload, String collapseKey) throws DeliveryException;
}
