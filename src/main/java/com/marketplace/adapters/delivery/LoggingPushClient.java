package com.marketplace.adapters.delivery;

import com.marketplace.application.port.PushClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingPushClient implements PushClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushClient.class);

    @Override
    public void send(String payload, String collapseKey) {
        log.info("Notificación enviada al tendero de la orden {}", collapseKey);
    }
}
