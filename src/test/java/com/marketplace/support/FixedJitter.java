package com.marketplace.support;

import com.marketplace.application.port.Jitter;
import java.time.Duration;

public class FixedJitter implements Jitter {

    private volatile Duration extra = Duration.ZERO;

    @Override
    public Duration apply(Duration interval) {
        return extra;
    }

    public void set(Duration extra) {
        this.extra = extra;
    }

    void reset() {
        extra = Duration.ZERO;
    }
}
