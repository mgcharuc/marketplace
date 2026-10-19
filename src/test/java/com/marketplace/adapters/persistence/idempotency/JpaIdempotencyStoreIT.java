package com.marketplace.adapters.persistence.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.application.port.IdempotencyStore;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.order.IdempotencyKey;
import com.marketplace.domain.order.Reservation;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import com.marketplace.support.Latches;
import com.marketplace.support.LockWaits;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JpaIdempotencyStoreIT {

    private static final IdempotencyKey KEY =
            new IdempotencyKey("CO-MARCA-A", "CLI-CREDITO-ALTO", "key-1");
    private static final Instant NOW = FixedTimeSource.START;
    private static final Instant EXPIRES = NOW.plus(Duration.ofHours(24));
    private static final String BODY = "{\"orderId\": \"o_1\" }";

    @Autowired IdempotencyStore store;
    @Autowired UnitOfWork unitOfWork;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Una clave nueva queda reservada con el hash y sin respuesta")
    void reservesNewKey() {
        assertThat(reserve(KEY, "hash-a", NOW)).isInstanceOf(Reservation.Reserved.class);

        assertThat(
                        jdbc.queryForMap(
                                "SELECT request_hash, order_id, response_body FROM idempotency_record"))
                .containsEntry("request_hash", "hash-a")
                .containsEntry("order_id", null)
                .containsEntry("response_body", null);
    }

    @Test
    @DisplayName("Con el mismo hash se repite la respuesta guardada, tal cual")
    void replaysStoredResponse() {
        confirmed(KEY, "hash-a");

        assertThat(reserve(KEY, "hash-a", NOW.plusSeconds(60)))
                .isEqualTo(new Reservation.Replay("o_1", BODY));
    }

    @Test
    @DisplayName("Con otro hash la clave está en conflicto y el registro no cambia")
    void otherHashConflicts() {
        confirmed(KEY, "hash-a");

        assertThat(reserve(KEY, "hash-b", NOW.plusSeconds(60)))
                .isInstanceOf(Reservation.Conflict.class);
        assertThat(jdbc.queryForObject("SELECT request_hash FROM idempotency_record", String.class))
                .isEqualTo("hash-a");
    }

    @Test
    @DisplayName("Un segundo antes de vencer la clave todavía repite la respuesta")
    void keyReplaysUntilTheSecondBeforeExpiring() {
        confirmed(KEY, "hash-a");

        assertThat(reserve(KEY, "hash-a", EXPIRES.minusSeconds(1)))
                .isEqualTo(new Reservation.Replay("o_1", BODY));
    }

    @Test
    @DisplayName("Una clave vencida se reutiliza como nueva, aunque el hash sea otro")
    void expiredKeyIsReserved() {
        confirmed(KEY, "hash-a");

        assertThat(reserve(KEY, "hash-b", EXPIRES)).isInstanceOf(Reservation.Reserved.class);
        assertThat(
                        jdbc.queryForMap(
                                "SELECT request_hash, order_id, response_body FROM idempotency_record"))
                .containsEntry("request_hash", "hash-b")
                .containsEntry("order_id", null)
                .containsEntry("response_body", null);
    }

    @Test
    @DisplayName("La misma clave de otro tendero o de otro tenant es independiente")
    void keysAreScopedToTenantAndCustomer() {
        confirmed(KEY, "hash-a");

        assertThat(reserve(new IdempotencyKey("CO-MARCA-A", "CLI-CONTADO", "key-1"), "hash-b", NOW))
                .isInstanceOf(Reservation.Reserved.class);
        assertThat(reserve(new IdempotencyKey("CO-MARCA-B", "CLI-B-01", "key-1"), "hash-b", NOW))
                .isInstanceOf(Reservation.Reserved.class);
    }

    @Test
    @DisplayName("Una clave vigente con el mismo hash y sin respuesta es un error")
    void keyWithoutResponseIsAnError() {
        reserve(KEY, "hash-a", NOW);

        assertThatThrownBy(() -> reserve(KEY, "hash-a", NOW))
                .hasMessage("Clave vigente sin respuesta guardada");
    }

    @Test
    @DisplayName(
            "Mientras otra transacción tiene la clave, se espera y luego se repite su respuesta")
    void waitsForTheTransactionHoldingTheKey() throws Exception {
        assertThat(concurrentReserve(true)).isEqualTo(new Reservation.Replay("o_1", BODY));
    }

    @Test
    @DisplayName("Si la transacción que tenía la clave se revierte, la clave queda reservada")
    void reservesAfterTheHolderRollsBack() throws Exception {
        assertThat(concurrentReserve(false)).isInstanceOf(Reservation.Reserved.class);
    }

    private Reservation concurrentReserve(boolean holderCommits) throws Exception {
        var reserved = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> holder =
                    executor.submit(
                            () ->
                                    unitOfWork.run(
                                            () -> {
                                                store.reserve(KEY, "hash-a", NOW, EXPIRES);
                                                store.complete(KEY, "o_1", BODY);
                                                reserved.countDown();
                                                Latches.await(release);
                                                if (!holderCommits) {
                                                    throw new IllegalStateException("rollback");
                                                }
                                            }));
            Latches.await(reserved);
            Future<Reservation> second = executor.submit(() -> reserve(KEY, "hash-a", NOW));

            LockWaits.await(jdbc, 1);
            assertThat(second).isNotDone();
            release.countDown();
            if (holderCommits) {
                holder.get(10, TimeUnit.SECONDS);
            } else {
                assertThatThrownBy(() -> holder.get(10, TimeUnit.SECONDS))
                        .hasRootCauseMessage("rollback");
            }
            return second.get(10, TimeUnit.SECONDS);
        }
    }

    private void confirmed(IdempotencyKey key, String hash) {
        unitOfWork.run(
                () -> {
                    store.reserve(key, hash, NOW, EXPIRES);
                    store.complete(key, "o_1", BODY);
                });
    }

    private Reservation reserve(IdempotencyKey key, String hash, Instant now) {
        return unitOfWork.execute(
                () -> store.reserve(key, hash, now, now.plus(Duration.ofHours(24))));
    }
}
