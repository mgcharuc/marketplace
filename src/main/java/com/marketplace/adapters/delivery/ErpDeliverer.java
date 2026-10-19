package com.marketplace.adapters.delivery;

import com.marketplace.application.port.DeliveryException;
import com.marketplace.application.port.DeliveryLog;
import com.marketplace.application.port.ErpClient;
import com.marketplace.application.port.EventDeliverer;
import com.marketplace.application.port.TimeSource;
import com.marketplace.domain.outbox.Destination;
import com.marketplace.domain.outbox.OutboxEvent;

public class ErpDeliverer implements EventDeliverer {

    private final ErpClient erp;
    private final DeliveryLog deliveries;
    private final TimeSource time;

    public ErpDeliverer(ErpClient erp, DeliveryLog deliveries, TimeSource time) {
        this.erp = erp;
        this.deliveries = deliveries;
        this.time = time;
    }

    @Override
    public Destination destination() {
        return Destination.ERP;
    }

    @Override
    public void deliver(OutboxEvent event) throws DeliveryException {
        erp.send(event.payload(), event.id().toString());
        deliveries.record(event.id(), Destination.ERP, time.now());
    }
}
