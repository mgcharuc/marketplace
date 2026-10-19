package com.marketplace.application.port;

import java.time.Duration;

public interface Jitter {

    /** Espera adicional que se suma a la del reintento. */
    Duration apply(Duration interval);
}
