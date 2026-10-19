package com.marketplace.adapters.delivery;

import com.marketplace.application.port.DeliveryException;
import com.marketplace.application.port.DeliveryLog;
import com.marketplace.application.port.EventDeliverer;
import com.marketplace.application.port.PushClient;
import com.marketplace.application.port.TimeSource;
import com.marketplace.domain.outbox.Destination;
import com.marketplace.domain.outbox.OutboxEvent;

public class PushDeliverer implements EventDeliverer {

    private final PushClient push;
    private final DeliveryLog deliveries;
    private final TimeSource time;

    public PushDeliverer(PushClient push, DeliveryLog deliveries, TimeSource time) {
        this.push = push;
        this.deliveries = deliveries;
        this.time = time;
    }

    @Override
    public Destination destination() {
        return Destination.PUSH;
    }

    @Override
    public void deliver(OutboxEvent event) throws DeliveryException {
        if (deliveries.exists(event.id(), Destination.PUSH)) {
            return;
        }
        push.send(event.payload(), event.aggregateId());
        deliveries.record(event.id(), Destination.PUSH, time.now());
    }
}
