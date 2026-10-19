package com.marketplace.adapters.persistence.outbox;

import com.marketplace.application.port.DeliveryLog;
import com.marketplace.domain.outbox.Destination;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JpaDeliveryLog implements DeliveryLog {

    private final DeliveredEventEntityRepository deliveries;
    private final TransactionTemplate ownTransaction;
    private final TransactionTemplate ownReadOnly;

    JpaDeliveryLog(
            DeliveredEventEntityRepository deliveries, PlatformTransactionManager transactions) {
        this.deliveries = deliveries;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownReadOnly = new TransactionTemplate(transactions);
        this.ownReadOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownReadOnly.setReadOnly(true);
    }

    @Override
    public boolean exists(UUID eventId, Destination destination) {
        var id = new DeliveredEventId(eventId, destination.name());
        return Boolean.TRUE.equals(ownReadOnly.execute(status -> deliveries.existsById(id)));
    }

    @Override
    public void record(UUID eventId, Destination destination, Instant at) {
        ownTransaction.executeWithoutResult(
                status -> {
                    deliveries.limitWaitsInTransaction();
                    deliveries.record(eventId, destination.name(), at);
                });
    }
}
