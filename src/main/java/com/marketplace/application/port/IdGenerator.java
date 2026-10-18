package com.marketplace.application.port;

import java.util.UUID;

public interface IdGenerator {

    String quoteId();

    String orderId();

    UUID eventId();
}
