package com.marketplace.adapters.delivery;

import com.marketplace.application.port.ErpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingErpClient implements ErpClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingErpClient.class);

    @Override
    public void send(String payload, String idempotencyKey) {
        log.info("Pedido publicado para el ERP con la clave {}", idempotencyKey);
    }
}
