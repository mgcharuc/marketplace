package com.marketplace.support;

import com.marketplace.application.port.Outbox;
import com.marketplace.domain.outbox.OutboxEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public class FailingOutbox implements Outbox {

    private final Outbox delegate;
    private final AtomicBoolean armed = new AtomicBoolean();

    FailingOutbox(Outbox delegate) {
        this.delegate = delegate;
    }

    public void arm() {
        armed.set(true);
    }

    @Override
    public void append(OutboxEvent event) {
        if (armed.getAndSet(false)) {
            throw new IllegalStateException("Fallo inyectado al escribir en el outbox");
        }
        delegate.append(event);
    }

    void reset() {
        armed.set(false);
    }
}
