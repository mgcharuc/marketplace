package com.marketplace.application.port;

import java.time.Instant;

public interface TimeSource {

    /** Instante actual truncado a segundos. */
    Instant now();
}
