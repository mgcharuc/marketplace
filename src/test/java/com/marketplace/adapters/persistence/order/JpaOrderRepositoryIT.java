package com.marketplace.adapters.persistence.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.application.port.OrderRepository;
import com.marketplace.application.port.PageQuery;
import com.marketplace.application.port.PageResult;
import com.marketplace.application.port.QuoteRepository;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.config.ConfigFixtures;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.order.DuplicateOrderException;
import com.marketplace.domain.order.Order;
import com.marketplace.domain.order.OrderHeader;
import com.marketplace.domain.order.OrderKey;
import com.marketplace.domain.order.OrderStatus;
import com.marketplace.domain.order.OrderSummary;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteHeader;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JpaOrderRepositoryIT {

    private static final String BODY = "{\"orderId\": \"o_1\",  \"total\": \"22610\" }";

    @Autowired OrderRepository orders;
    @Autowired QuoteRepository quotes;
    @Autowired UnitOfWork unitOfWork;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("La orden se lee con el mismo cuerpo que se guardó, sin reformatearlo")
    void readsSavedBody() {
        var order = order("o_1", "CO-A-00000001", quote("q_1"));
        orders.save(order);

        assertThat(orders.findBody(order.header().key())).hasValue(BODY);
    }

    @Test
    @DisplayName("Otro tendero u otro tenant no encuentran la orden")
    void bodyIsScopedToTenantAndCustomer() {
        orders.save(order("o_1", "CO-A-00000001", quote("q_1")));

        assertThat(orders.findBody(new OrderKey("CO-MARCA-A", "CLI-CREDITO-ALTO", "o_1")))
                .isEmpty();
        assertThat(orders.findBody(new OrderKey("CO-MARCA-B", "CLI-CONTADO", "o_1"))).isEmpty();
        assertThat(orders.findBody(new OrderKey("CO-MARCA-A", "CLI-CONTADO", "o_2"))).isEmpty();
    }

    @Test
    @DisplayName("La orden de una cotización se encuentra solo dentro de su tenant")
    void findsOrderByQuoteWithinTenant() {
        orders.save(order("o_1", "CO-A-00000001", quote("q_1")));

        assertThat(orders.findIdByQuote("CO-MARCA-A", "q_1")).hasValue("o_1");
        assertThat(orders.findIdByQuote("CO-MARCA-B", "q_1")).isEmpty();
        assertThat(orders.findIdByQuote("CO-MARCA-A", "q_2")).isEmpty();
    }

    @Test
    @DisplayName("Cada valor de la secuencia del número de orden es mayor que el anterior")
    void sequenceGrows() {
        long first = unitOfWork.execute(orders::nextSequence);
        long second = unitOfWork.execute(orders::nextSequence);

        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("Guardar una segunda orden para la misma cotización falla como orden repetida")
    void secondOrderForTheSameQuoteIsDuplicate() {
        var quote = quote("q_1");
        orders.save(order("o_1", "CO-A-00000001", quote));

        assertThatThrownBy(
                        () ->
                                unitOfWork.run(
                                        () -> orders.save(order("o_2", "CO-A-00000002", quote))))
                .isInstanceOf(DuplicateOrderException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class)).isOne();
    }

    @Test
    @DisplayName("Otra restricción violada no se toma como orden repetida")
    void otherViolationsAreNotTranslated() {
        orders.save(order("o_1", "CO-A-00000001", quote("q_1")));
        var otherQuote = quote("q_2");

        assertThatThrownBy(
                        () ->
                                unitOfWork.run(
                                        () ->
                                                orders.save(
                                                        order("o_2", "CO-A-00000001", otherQuote))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("El historial va de la más reciente a la más antigua y desempata por secuencia")
    void historyIsNewestFirst() {
        Instant later = FixedTimeSource.START.plusSeconds(60);
        orders.save(order("o_1", 100_000_000, later, quote("q_1")));
        orders.save(order("o_2", 99_999_999, later, quote("q_2")));
        orders.save(order("o_3", 100_000_001, FixedTimeSource.START.plusSeconds(30), quote("q_3")));

        var page = history(new PageQuery(0, 20));

        assertThat(page.items())
                .extracting(OrderSummary::orderId)
                .containsExactly("o_1", "o_2", "o_3");
        assertThat(page.items().get(0))
                .isEqualTo(
                        new OrderSummary(
                                "o_1",
                                "CO-A-100000000",
                                "DIST-NORTE",
                                later,
                                new Money(new BigDecimal("22610"), ConfigFixtures.CO.currency()),
                                OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("El historial no mezcla tenderos con el mismo id en tenants distintos")
    void historyIsScopedToTenant() {
        jdbc.update(
                "INSERT INTO customer (tenant_id, id, name) VALUES ('CO-MARCA-B', 'CLI-CONTADO', 'Otra tienda')");
        orders.save(order("o_1", 1, FixedTimeSource.START, quote("q_1")));
        orders.save(
                order("o_2", 2, FixedTimeSource.START, quote("q_2", "CO-MARCA-B", "DIST-B-01")));

        assertThat(history(new PageQuery(0, 20)).items())
                .extracting(OrderSummary::orderId)
                .containsExactly("o_1");
        assertThat(
                        orders.history(
                                        "CO-MARCA-B",
                                        "CLI-CONTADO",
                                        new PageQuery(0, 20),
                                        ConfigFixtures.CO.currency())
                                .items())
                .extracting(OrderSummary::orderId)
                .containsExactly("o_2");
    }

    @Test
    @DisplayName("Una página más allá de lo que cabe en la consulta sale vacía con el total")
    void historyBeyondTheLastPageIsEmpty() {
        orders.save(order("o_1", 1, FixedTimeSource.START, quote("q_1")));

        var page = history(new PageQuery(Integer.MAX_VALUE, 100));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isOne();
    }

    @Test
    @DisplayName("El historial se pagina y cuenta todas las órdenes del tendero")
    void historyIsPaged() {
        for (int n = 1; n <= 3; n++) {
            orders.save(order("o_" + n, n, FixedTimeSource.START.plusSeconds(n), quote("q_" + n)));
        }

        var second = history(new PageQuery(1, 2));

        assertThat(second.items()).extracting(OrderSummary::orderId).containsExactly("o_1");
        assertThat(second.page()).isOne();
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.totalItems()).isEqualTo(3);
        assertThat(history(new PageQuery(5, 2)).items()).isEmpty();
    }

    private PageResult<OrderSummary> history(PageQuery page) {
        return orders.history("CO-MARCA-A", "CLI-CONTADO", page, ConfigFixtures.CO.currency());
    }

    private Order order(String orderId, String orderNumber, QuoteHeader quote) {
        return order(orderId, orderNumber, 1, FixedTimeSource.START.plusSeconds(60), quote);
    }

    private Order order(String orderId, long sequence, Instant confirmedAt, QuoteHeader quote) {
        return order(orderId, "CO-A-%08d".formatted(sequence), sequence, confirmedAt, quote);
    }

    private Order order(
            String orderId,
            String orderNumber,
            long sequence,
            Instant confirmedAt,
            QuoteHeader quote) {
        var total = new Money(new BigDecimal("22610"), quote.currency());
        var header = new OrderHeader(orderId, orderNumber, quote, total, confirmedAt, null);
        return new Order(header, sequence, BODY);
    }

    private QuoteHeader quote(String quoteId) {
        return quote(quoteId, "CO-MARCA-A", "DIST-NORTE");
    }

    private QuoteHeader quote(String quoteId, String tenantId, String distributorId) {
        var currency = ConfigFixtures.CO.currency();
        var header =
                new QuoteHeader(
                        quoteId,
                        tenantId,
                        "CLI-CONTADO",
                        distributorId,
                        1,
                        currency,
                        PaymentMethod.CASH,
                        FixedTimeSource.START,
                        FixedTimeSource.START.plus(Duration.ofMinutes(15)));
        quotes.save(new Quote(header, new Money(new BigDecimal("22610"), currency), "{}"));
        return header;
    }
}
