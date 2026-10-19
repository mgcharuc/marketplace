package com.marketplace.application.port;

import com.marketplace.domain.outbox.OutboxEvent;

/**
 * El relay lo llama después de entregar un evento y antes de marcarlo enviado. Las pruebas lo usan
 * para simular una caída del proceso en ese punto.
 */
public interface RelayHooks {

    RelayHooks NONE = event -> {};

    void afterDeliver(OutboxEvent event);
}
