package com.marketplace.support;

import com.marketplace.application.port.RelayHooks;
import com.marketplace.domain.outbox.Destination;
import com.marketplace.domain.outbox.OutboxEvent;
import java.util.concurrent.atomic.AtomicReference;

public class CrashingRelayHooks implements RelayHooks {

    private final AtomicReference<Destination> armed = new AtomicReference<>();

    public void armFor(Destination destination) {
        armed.set(destination);
    }

    @Override
    public void afterDeliver(OutboxEvent event) {
        if (armed.compareAndSet(event.destination(), null)) {
            throw new IllegalStateException(
                    "Caída simulada tras entregar a " + event.destination());
        }
    }

    void reset() {
        armed.set(null);
    }
}
