package com.marketplace.application.outbox;

import com.marketplace.application.port.OutboxRepository;
import com.marketplace.application.port.RequeueResult;
import com.marketplace.application.port.TimeSource;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.outbox.EventNotFailedException;
import com.marketplace.domain.outbox.EventNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vuelve a encolar un evento fallido. El evento queda pendiente desde ahora, sin intentos y con una
 * ventana de reintentos nueva.
 */
public class RequeueEvent {

    private static final Logger log = LoggerFactory.getLogger(RequeueEvent.class);

    private final OutboxRepository events;
    private final UnitOfWork unitOfWork;
    private final TimeSource time;

    public RequeueEvent(OutboxRepository events, UnitOfWork unitOfWork, TimeSource time) {
        this.events = events;
        this.unitOfWork = unitOfWork;
        this.time = time;
    }

    public void handle(UUID eventId) {
        RequeueResult result = unitOfWork.execute(() -> events.requeue(eventId, time.now()));
        switch (result) {
            case RequeueResult.Requeued requeued -> log.info("Evento {} reencolado", eventId);
            case RequeueResult.NotFound notFound -> throw new EventNotFoundException();
            case RequeueResult.NotFailed notFailed ->
                    throw new EventNotFailedException(notFailed.status());
        }
    }
}
