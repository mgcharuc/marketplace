package com.marketplace.application.outbox;

import com.marketplace.application.port.DeliveryException;
import com.marketplace.application.port.EventDeliverer;
import com.marketplace.application.port.Jitter;
import com.marketplace.application.port.OutboxRepository;
import com.marketplace.application.port.TimeSource;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.outbox.Destination;
import com.marketplace.domain.outbox.OutboxEvent;
import com.marketplace.domain.outbox.RetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrega los eventos pendientes, cada uno en su propia transacción y con su fila bloqueada. Un
 * fallo se reintenta mientras la ventana del destino siga abierta. Si algo falla fuera de la
 * entrega, el evento se pospone para que no frene a los demás.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private enum Outcome {
        DELIVERED,
        RETRIED,
        FAILED,
        DISCARDED
    }

    private final OutboxRepository events;
    private final Map<Destination, EventDeliverer> deliverers = new EnumMap<>(Destination.class);
    private final UnitOfWork unitOfWork;
    private final TimeSource time;
    private final Jitter jitter;

    public OutboxRelay(
            OutboxRepository events,
            List<EventDeliverer> deliverers,
            UnitOfWork unitOfWork,
            TimeSource time,
            Jitter jitter) {
        this.events = events;
        for (EventDeliverer deliverer : deliverers) {
            if (this.deliverers.put(deliverer.destination(), deliverer) != null) {
                throw new IllegalArgumentException(
                        "Hay dos entregadores para " + deliverer.destination());
            }
        }
        for (Destination destination : Destination.values()) {
            if (!this.deliverers.containsKey(destination)) {
                throw new IllegalArgumentException("Falta el entregador de " + destination);
            }
        }
        this.unitOfWork = unitOfWork;
        this.time = time;
        this.jitter = jitter;
    }

    public RelayResult runOnce() {
        var outcomes = new EnumMap<Outcome, Integer>(Outcome.class);
        for (int i = 0; i < BATCH_SIZE; i++) {
            Optional<Outcome> outcome = processNext();
            if (outcome.isEmpty()) {
                break;
            }
            outcomes.merge(outcome.get(), 1, Integer::sum);
        }
        return new RelayResult(
                outcomes.getOrDefault(Outcome.DELIVERED, 0),
                outcomes.getOrDefault(Outcome.RETRIED, 0),
                outcomes.getOrDefault(Outcome.FAILED, 0),
                outcomes.getOrDefault(Outcome.DISCARDED, 0));
    }

    private Optional<Outcome> processNext() {
        var locked = new AtomicReference<OutboxEvent>();
        try {
            return unitOfWork.execute(() -> deliverNext(locked));
        } catch (RuntimeException e) {
            if (locked.get() == null) {
                throw e;
            }
            return Optional.of(postpone(locked.get(), e));
        }
    }

    private Optional<Outcome> deliverNext(AtomicReference<OutboxEvent> locked) {
        Instant now = time.now();
        return events.lockNextPending(now)
                .map(
                        event -> {
                            locked.set(event);
                            return deliver(event, now);
                        });
    }

    private Outcome deliver(OutboxEvent event, Instant now) {
        if (event.destination() == Destination.PUSH && RetryPolicy.windowClosed(event, now)) {
            events.markDiscarded(event.id(), event.attempts(), "Venció la ventana de entrega");
            log.warn("Notificación {} descartada sin enviar porque venció su ventana", event.id());
            return Outcome.DISCARDED;
        }
        try {
            deliverers.get(event.destination()).deliver(event);
        } catch (DeliveryException | RuntimeException e) {
            return failed(event, now, e);
        }
        events.markSent(event.id(), now);
        log.info("Evento {} entregado a {}", event.id(), event.destination());
        return Outcome.DELIVERED;
    }

    // Una notificación con la ventana cerrada se descarta antes de intentar, así que aquí solo se
    // cierra un evento del ERP.
    private Outcome failed(OutboxEvent event, Instant now, Exception error) {
        int attempts = event.attempts() + 1;
        String reason =
                error.getMessage() == null ? error.getClass().getName() : error.getMessage();
        if (RetryPolicy.windowClosed(event, now)) {
            events.markFailed(event.id(), attempts, reason);
            log.error(
                    "El evento {} no llegó al ERP en su ventana y queda fallido",
                    event.id(),
                    error);
            return Outcome.FAILED;
        }
        Duration wait = RetryPolicy.backoff(attempts);
        Instant next = now.plus(wait).plus(jitter.apply(wait));
        events.markRetry(event.id(), attempts, next, reason);
        log.warn(
                "Falló la entrega del evento {} a {} ({}), próximo intento {}",
                event.id(),
                event.destination(),
                reason,
                next);
        return Outcome.RETRIED;
    }

    // La transacción del evento ya se revirtió, así que el intento se anota en otra.
    private Outcome postpone(OutboxEvent event, RuntimeException error) {
        int attempts = event.attempts() + 1;
        Instant next = time.now().plus(RetryPolicy.backoff(attempts));
        unitOfWork.run(
                () -> events.markRetry(event.id(), attempts, next, "Error al procesar el evento"));
        log.error(
                "No se pudo procesar el evento {}, se reintenta desde {}", event.id(), next, error);
        return Outcome.RETRIED;
    }
}
