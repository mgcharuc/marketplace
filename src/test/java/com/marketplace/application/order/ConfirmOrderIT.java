package com.marketplace.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.application.port.CreditRepository;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.application.quote.QuoteCart;
import com.marketplace.application.quote.QuoteCartCommand;
import com.marketplace.domain.config.ConfigFixtures;
import com.marketplace.domain.credit.CreditKey;
import com.marketplace.domain.credit.CreditLimitExceededException;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.order.DuplicateOrderException;
import com.marketplace.domain.order.QuoteAlreadyConfirmedException;
import com.marketplace.domain.quote.QuoteKey;
import com.marketplace.support.IntegrationTest;
import com.marketplace.support.Latches;
import com.marketplace.support.LockWaits;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ConfirmOrderIT {

    @Autowired ConfirmOrder confirmOrder;
    @Autowired QuoteCart quoteCart;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CreditRepository credits;
    @Autowired UnitOfWork unitOfWork;

    @Test
    @DisplayName("De dos órdenes que juntas superan el cupo y se confirman a la vez, solo pasa una")
    void concurrentOrdersNeverOverdrawCredit() throws Exception {
        QuoteKey first = quote("CLI-CREDITO-BAJO", "CREDIT", 5);
        QuoteKey second = quote("CLI-CREDITO-BAJO", "CREDIT", 5);

        List<Throwable> outcomes = runTogether(confirmation(first), confirmation(second));

        assertThat(outcomes).filteredOn(outcome -> outcome == null).hasSize(1);
        assertThat(outcomes)
                .filteredOn(outcome -> outcome != null)
                .singleElement()
                .isInstanceOf(CreditLimitExceededException.class);
        assertThat(available("CLI-CREDITO-BAJO")).isEqualByComparingTo("21142");
        assertThat(count("orders")).isOne();
        assertThat(count("credit_movement")).isOne();
        assertThat(count("outbox_event")).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "Una confirmación espera al consumo de cupo en curso y se rechaza si ya no alcanza")
    void confirmationWaitsForCreditInUseAndRechecksIt() throws Exception {
        QuoteKey key = quote("CLI-CREDITO-BAJO", "CREDIT", 5);
        var account = new CreditKey("CO-MARCA-A", "CLI-CREDITO-BAJO", "DIST-NORTE");
        var consumed = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> other =
                    executor.submit(
                            () ->
                                    unitOfWork.run(
                                            () -> {
                                                credits.consume(account, cop("28858"));
                                                consumed.countDown();
                                                Latches.await(release);
                                            }));
            Latches.await(consumed);
            Future<?> confirmation = executor.submit(() -> confirmOrder.handle(key));

            LockWaits.await(jdbc, 1);
            assertThat(confirmation).isNotDone();
            release.countDown();
            other.get(10, TimeUnit.SECONDS);

            assertThatThrownBy(() -> confirmation.get(10, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(
                            CreditLimitExceededException.class,
                            rejection -> {
                                assertThat(rejection.available()).isEqualTo(cop("21142"));
                                assertThat(rejection.required()).isEqualTo(cop("28858"));
                            });
        }
        assertThat(available("CLI-CREDITO-BAJO")).isEqualByComparingTo("21142");
        assertThat(count("orders")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("Dos confirmaciones simultáneas de la misma cotización crean una sola orden")
    void concurrentConfirmationsOfOneQuoteCreateOneOrder() throws Exception {
        QuoteKey key = quote("CLI-CREDITO-ALTO", "CREDIT", 5);

        List<Throwable> outcomes = runTogether(confirmation(key), confirmation(key));

        assertThat(outcomes).filteredOn(outcome -> outcome == null).hasSize(1);
        assertThat(outcomes)
                .filteredOn(outcome -> outcome != null)
                .singleElement()
                .isInstanceOfSatisfying(
                        QuoteAlreadyConfirmedException.class,
                        conflict ->
                                assertThat(conflict.orderId())
                                        .isEqualTo(
                                                jdbc.queryForObject(
                                                        "SELECT id FROM orders", String.class)));
        assertThat(available("CLI-CREDITO-ALTO")).isEqualByComparingTo("971142");
        assertThat(count("credit_movement")).isOne();
        assertThat(count("outbox_event")).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "Si otra transacción guarda primero una orden para la cotización, se informa esa orden")
    void orderSavedByAnotherTransactionIsReported() throws Exception {
        QuoteKey key = quote("CLI-CREDITO-ALTO", "CREDIT", 5);
        var inserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> other =
                    executor.submit(
                            () ->
                                    transaction.executeWithoutResult(
                                            status -> {
                                                insertOrder("o_00000000000000000000000099", key);
                                                inserted.countDown();
                                                Latches.await(release);
                                            }));
            Latches.await(inserted);
            Future<?> confirmation = executor.submit(() -> confirmOrder.handle(key));

            LockWaits.await(jdbc, 1);
            release.countDown();
            other.get(10, TimeUnit.SECONDS);

            assertThatThrownBy(() -> confirmation.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(
                            QuoteAlreadyConfirmedException.class,
                            conflict ->
                                    assertThat(conflict.orderId())
                                            .isEqualTo("o_00000000000000000000000099"))
                    .hasCauseInstanceOf(DuplicateOrderException.class);
        }
        assertThat(available("CLI-CREDITO-ALTO")).isEqualByComparingTo("1000000");
        assertThat(count("credit_movement")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    private QuoteKey quote(String customerId, String paymentMethod, int quantity) {
        var command =
                new QuoteCartCommand(
                        "CO-MARCA-A",
                        customerId,
                        "DIST-NORTE",
                        paymentMethod,
                        List.of(new QuoteCartCommand.Item("GASEOSA-1500", quantity)));
        return quoteCart.handle(command).header().key();
    }

    private Callable<Void> confirmation(QuoteKey key) {
        return () -> {
            confirmOrder.handle(key);
            return null;
        };
    }

    // Cada resultado es null si la tarea terminó bien, o la excepción que lanzó.
    @SafeVarargs
    private static List<Throwable> runTogether(Callable<Void>... tasks) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Void>>();
            for (Callable<Void> task : tasks) {
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return task.call();
                                }));
            }
            start.countDown();
            var outcomes = new ArrayList<Throwable>();
            for (Future<Void> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    outcomes.add(null);
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        }
    }

    private void insertOrder(String orderId, QuoteKey key) {
        jdbc.update(
                """
                INSERT INTO orders (id, tenant_id, customer_id, distributor_id, order_seq,
                                    order_number, quote_id, status, total, response_body, confirmed_at)
                VALUES (?, ?, ?, 'DIST-NORTE', 999, 'CO-A-00000999', ?, 'CONFIRMED', 28858, '{}', now())
                """,
                orderId,
                key.tenantId(),
                key.customerId(),
                key.quoteId());
    }

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), ConfigFixtures.CO.currency());
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private BigDecimal available(String customerId) {
        return jdbc.queryForObject(
                "SELECT available FROM credit_account WHERE tenant_id = 'CO-MARCA-A'"
                        + " AND customer_id = ? AND distributor_id = 'DIST-NORTE'",
                BigDecimal.class,
                customerId);
    }
}
