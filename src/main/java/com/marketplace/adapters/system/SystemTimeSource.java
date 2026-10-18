package com.marketplace.adapters.system;

import com.marketplace.application.port.TimeSource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SystemTimeSource implements TimeSource {

    private final Clock clock = Clock.systemUTC();

    @Override
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }
}
