package com.marketplace.adapters.persistence.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.application.port.OrderRepository;
import com.marketplace.application.port.QuoteRepository;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.config.ConfigFixtures;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.order.DuplicateOrderException;
import com.marketplace.domain.order.Order;
import com.marketplace.domain.order.OrderHeader;
import com.marketplace.domain.order.OrderKey;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteHeader;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
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

    private Order order(String orderId, String orderNumber, QuoteHeader quote) {
        var total = new Money(new BigDecimal("22610"), quote.currency());
        var header =
                new OrderHeader(
                        orderId,
                        orderNumber,
                        quote,
                        total,
                        FixedTimeSource.START.plusSeconds(60),
                        null);
        return new Order(header, 1, BODY);
    }

    private QuoteHeader quote(String quoteId) {
        var currency = ConfigFixtures.CO.currency();
        var header =
                new QuoteHeader(
                        quoteId,
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        "DIST-NORTE",
                        1,
                        currency,
                        PaymentMethod.CASH,
                        FixedTimeSource.START,
                        FixedTimeSource.START.plus(Duration.ofMinutes(15)));
        quotes.save(new Quote(header, new Money(new BigDecimal("22610"), currency), "{}"));
        return header;
    }
}
