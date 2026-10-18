package com.marketplace.support;

import com.marketplace.application.port.TimeSource;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class FixedTimeSource implements TimeSource {

    public static final Instant START = Instant.parse("2026-10-16T15:00:00Z");

    private volatile Instant now = START;

    @Override
    public Instant now() {
        return now;
    }

    public void set(Instant instant) {
        now = instant.truncatedTo(ChronoUnit.SECONDS);
    }

    public void advance(Duration duration) {
        set(now.plus(duration));
    }

    public void reset() {
        now = START;
    }
}
