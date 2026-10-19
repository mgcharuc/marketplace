package com.marketplace.application.order;

import com.marketplace.application.CustomerAccess;
import com.marketplace.application.port.CreditRepository;
import com.marketplace.application.port.EventPayloadRenderer;
import com.marketplace.application.port.IdGenerator;
import com.marketplace.application.port.IdempotencyStore;
import com.marketplace.application.port.OrderRepository;
import com.marketplace.application.port.OrderResponseRenderer;
import com.marketplace.application.port.Outbox;
import com.marketplace.application.port.QuoteRepository;
import com.marketplace.application.port.TimeSource;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.DomainException;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.credit.CreditAccountNotFoundException;
import com.marketplace.domain.credit.CreditKey;
import com.marketplace.domain.credit.CreditLimitExceededException;
import com.marketplace.domain.credit.CreditMovement;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.order.DuplicateOrderException;
import com.marketplace.domain.order.IdempotencyKey;
import com.marketplace.domain.order.IdempotencyKeyReusedException;
import com.marketplace.domain.order.Order;
import com.marketplace.domain.order.OrderHeader;
import com.marketplace.domain.order.QuoteAlreadyConfirmedException;
import com.marketplace.domain.order.Reservation;
import com.marketplace.domain.outbox.Destination;
import com.marketplace.domain.outbox.OutboxEvent;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteExpiredException;
import com.marketplace.domain.quote.QuoteHeader;
import com.marketplace.domain.quote.QuoteKey;
import com.marketplace.domain.quote.QuoteNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Crea la orden de una cotización vigente copiando su desglose, sin recalcularlo. Una cotización se
 * confirma una sola vez, y si ya tiene orden se informa esa orden aunque haya vencido. Un reintento
 * con la misma clave y el mismo cuerpo recibe la respuesta original sin crear nada.
 */
public class ConfirmOrder {

    private static final Logger log = LoggerFactory.getLogger(ConfirmOrder.class);

    private final CustomerAccess access;
    private final QuoteRepository quotes;
    private final OrderRepository orders;
    private final CreditRepository credits;
    private final IdempotencyStore idempotency;
    private final Outbox outbox;
    private final OrderResponseRenderer renderer;
    private final EventPayloadRenderer payloads;
    private final UnitOfWork unitOfWork;
    private final TimeSource time;
    private final IdGenerator ids;

    public ConfirmOrder(
            CustomerAccess access,
            QuoteRepository quotes,
            OrderRepository orders,
            CreditRepository credits,
            IdempotencyStore idempotency,
            Outbox outbox,
            OrderResponseRenderer renderer,
            EventPayloadRenderer payloads,
            UnitOfWork unitOfWork,
            TimeSource time,
            IdGenerator ids) {
        this.access = access;
        this.quotes = quotes;
        this.orders = orders;
        this.credits = credits;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.renderer = renderer;
        this.payloads = payloads;
        this.unitOfWork = unitOfWork;
        this.time = time;
        this.ids = ids;
    }

    public OrderConfirmation handle(ConfirmOrderCommand command) {
        QuoteKey key = command.quoteKey();
        Tenant tenant = access.tenantOf(key.tenantId(), key.customerId());
        OrderConfirmation confirmation;
        try {
            confirmation = unitOfWork.execute(() -> confirm(tenant, command));
        } catch (DuplicateOrderException duplicate) {
            String orderId =
                    unitOfWork
                            .execute(() -> orders.findIdByQuote(key.tenantId(), key.quoteId()))
                            .orElseThrow(() -> duplicate);
            throw new QuoteAlreadyConfirmedException(orderId, duplicate);
        }
        MDC.put("orderId", confirmation.orderId());
        log.info(confirmation.replayed() ? "Respuesta repetida de la orden" : "Orden confirmada");
        return confirmation;
    }

    private OrderConfirmation confirm(Tenant tenant, ConfirmOrderCommand command) {
        IdempotencyKey key = command.key();
        Instant now = time.now();
        Reservation reservation =
                idempotency.reserve(
                        key, command.requestHash(), now, now.plus(tenant.idempotencyTtl()));
        return switch (reservation) {
            case Reservation.Replay replay ->
                    new OrderConfirmation(replay.orderId(), replay.body(), true);
            case Reservation.Conflict conflict -> throw new IdempotencyKeyReusedException();
            case Reservation.Reserved reserved -> {
                Order order = create(tenant, command.quoteKey());
                idempotency.complete(key, order.header().orderId(), order.body());
                yield new OrderConfirmation(order.header().orderId(), order.body(), false);
            }
        };
    }

    private Order create(Tenant tenant, QuoteKey key) {
        Quote quote = quotes.lock(key, tenant.currency()).orElseThrow(QuoteNotFoundException::new);
        QuoteHeader header = quote.header();
        MDC.put("distributorId", header.distributorId());
        orders.findIdByQuote(tenant.id(), header.quoteId())
                .ifPresent(
                        orderId -> {
                            throw new QuoteAlreadyConfirmedException(orderId);
                        });
        Instant now = time.now();
        if (!now.isBefore(header.expiresAt())) {
            throw new QuoteExpiredException(header.expiresAt());
        }
        Money availableAfter =
                header.paymentMethod() == PaymentMethod.CREDIT
                        ? consume(account(header), quote.total())
                        : null;
        long sequence = orders.nextSequence();
        String orderId = ids.orderId();
        var orderHeader =
                new OrderHeader(
                        orderId,
                        tenant.orderNumber(sequence),
                        header,
                        quote.total(),
                        now,
                        availableAfter);
        var order =
                new Order(orderHeader, sequence, renderer.render(orderHeader, quote.snapshot()));
        orders.save(order);
        if (availableAfter != null) {
            credits.record(new CreditMovement(orderId, account(header), quote.total(), now));
        }
        enqueue(order, now);
        return order;
    }

    private Money consume(CreditKey account, Money required) {
        return credits.consume(account, required)
                .orElseThrow(() -> creditRejection(account, required));
    }

    private DomainException creditRejection(CreditKey account, Money required) {
        return credits.find(account, required.currency())
                .<DomainException>map(
                        found -> new CreditLimitExceededException(found.available(), required))
                .orElseGet(CreditAccountNotFoundException::new);
    }

    // El ERP va primero porque el relay desempata por id.
    private void enqueue(Order order, Instant now) {
        OrderHeader header = order.header();
        UUID erp = ids.eventId();
        UUID push = ids.eventId();
        outbox.append(
                event(erp, header, Destination.ERP, payloads.erp(erp, header, order.body()), now));
        outbox.append(event(push, header, Destination.PUSH, payloads.push(push, header), now));
    }

    private static OutboxEvent event(
            UUID id, OrderHeader header, Destination destination, String payload, Instant now) {
        return OutboxEvent.orderConfirmed(
                id, header.quote().tenantId(), header.orderId(), destination, payload, now);
    }

    private static CreditKey account(QuoteHeader header) {
        return new CreditKey(header.tenantId(), header.customerId(), header.distributorId());
    }
}
