package com.marketplace.support;

import com.marketplace.application.port.IdGenerator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class SequentialIdGenerator implements IdGenerator {

    private static final long UUID_V7_HIGH = 0x7000L;
    private static final long UUID_VARIANT = 0x8000_0000_0000_0000L;

    private final AtomicLong quotes = new AtomicLong();
    private final AtomicLong orders = new AtomicLong();
    private final AtomicLong events = new AtomicLong();

    @Override
    public String quoteId() {
        return "q_%026d".formatted(quotes.incrementAndGet());
    }

    @Override
    public String orderId() {
        return "o_%026d".formatted(orders.incrementAndGet());
    }

    @Override
    public UUID eventId() {
        return new UUID(UUID_V7_HIGH, UUID_VARIANT | events.incrementAndGet());
    }

    public void reset() {
        quotes.set(0);
        orders.set(0);
        events.set(0);
    }
}
