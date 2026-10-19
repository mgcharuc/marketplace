package com.marketplace.application.port;

/** El sistema externo no recibió el evento. */
public class DeliveryException extends Exception {

    public DeliveryException(String message) {
        super(message);
    }

    public DeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
