package com.marketplace.adapters.persistence.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.application.port.Outbox;
import com.marketplace.application.port.OutboxRepository;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.outbox.Destination;
import com.marketplace.domain.outbox.OutboxEvent;
import com.marketplace.domain.outbox.OutboxStatus;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import com.marketplace.support.Latches;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JpaOutboxRepositoryIT {

    private static final Instant NOW = FixedTimeSource.START;

    @Autowired Outbox outbox;
    @Autowired OutboxRepository events;
    @Autowired UnitOfWork unitOfWork;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Toma el pendiente cuyo próximo intento es el más antiguo y desempata por id")
    void locksTheEarliestDueEvent() {
        append(1, OutboxStatus.PENDING, NOW.plusSeconds(10));
        append(2, OutboxStatus.PENDING, NOW);
        append(3, OutboxStatus.PENDING, NOW);
        append(4, OutboxStatus.SENT, NOW.minusSeconds(60));
        append(5, OutboxStatus.PENDING, NOW.plusSeconds(3600));

        assertThat(lockAt(NOW.plusSeconds(20))).map(OutboxEvent::id).contains(id(2));
    }

    @Test
    @DisplayName("Sin pendientes que ya toque entregar no bloquea nada")
    void findsNothingNotYetDue() {
        append(1, OutboxStatus.PENDING, NOW.plusSeconds(10));
        append(2, OutboxStatus.FAILED, NOW);

        assertThat(lockAt(NOW)).isEmpty();
    }

    @Test
    @DisplayName("El evento bloqueado por otra transacción se salta sin esperar")
    void skipsLockedEvents() throws Exception {
        append(1, OutboxStatus.PENDING, NOW);
        append(2, OutboxStatus.PENDING, NOW);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var holder =
                    executor.submit(
                            () ->
                                    unitOfWork.run(
                                            () -> {
                                                events.lockNextPending(NOW);
                                                locked.countDown();
                                                Latches.await(release);
                                            }));
            Latches.await(locked);

            assertThat(lockAt(NOW)).map(OutboxEvent::id).contains(id(2));
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Las esperas de bloqueo se acotan solo dentro de la transacción del relay")
    void limitsWaitsOnlyInsideTheTransaction() {
        String inside =
                unitOfWork.execute(
                        () -> {
                            events.lockNextPending(NOW);
                            return jdbc.queryForObject("SHOW lock_timeout", String.class)
                                    + " "
                                    + jdbc.queryForObject("SHOW statement_timeout", String.class);
                        });
        String outside =
                unitOfWork.execute(() -> jdbc.queryForObject("SHOW lock_timeout", String.class));

        assertThat(inside).isEqualTo("5s 30s");
        assertThat(outside).isEqualTo("0");
    }

    @Test
    @DisplayName("Un reintento guarda los intentos, el próximo intento y el error")
    void marksRetry() {
        append(1, OutboxStatus.PENDING, NOW);

        unitOfWork.run(() -> events.markRetry(id(1), 1, NOW.plusSeconds(1), "ERP caído"));

        assertThat(row(1))
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 1)
                .containsEntry("next_attempt_at", Timestamp.from(NOW.plusSeconds(1)))
                .containsEntry("last_error", "ERP caído");
    }

    @Test
    @DisplayName("Marcar un evento lo deja enviado con su fecha, fallido o descartado")
    void marksSentFailedAndDiscarded() {
        append(1, OutboxStatus.PENDING, NOW);
        append(2, OutboxStatus.PENDING, NOW);
        append(3, OutboxStatus.PENDING, NOW);

        unitOfWork.run(
                () -> {
                    events.markSent(id(1), NOW);
                    events.markFailed(id(2), 7, "ERP caído");
                    events.markDiscarded(id(3), 2, "Ventana vencida");
                });

        assertThat(row(1))
                .containsEntry("status", "SENT")
                .containsEntry("sent_at", Timestamp.from(NOW));
        assertThat(row(2)).containsEntry("status", "FAILED").containsEntry("attempts", 7);
        assertThat(row(3))
                .containsEntry("status", "DISCARDED")
                .containsEntry("last_error", "Ventana vencida");
    }

    @Test
    @DisplayName("Marcar un evento que no existe es un error")
    void markingUnknownEventFails() {
        assertThatThrownBy(() -> unitOfWork.run(() -> events.markSent(id(9), NOW)))
                .hasMessage("El evento " + id(9) + " no existe");
    }

    private Optional<OutboxEvent> lockAt(Instant now) {
        return unitOfWork.execute(() -> events.lockNextPending(now));
    }

    private void append(int n, OutboxStatus status, Instant nextAttemptAt) {
        outbox.append(
                new OutboxEvent(
                        id(n),
                        "CO-MARCA-A",
                        "o_" + n,
                        OutboxEvent.ORDER_CONFIRMED,
                        Destination.ERP,
                        "{}",
                        status,
                        0,
                        nextAttemptAt,
                        NOW,
                        NOW));
    }

    private Map<String, Object> row(int n) {
        return jdbc.queryForMap(
                "SELECT status, attempts, next_attempt_at, last_error, sent_at FROM outbox_event WHERE id = ?",
                id(n));
    }

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-7000-8000-%012d".formatted(n));
    }
}
