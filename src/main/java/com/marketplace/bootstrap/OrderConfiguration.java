package com.marketplace.bootstrap;

import com.marketplace.application.CustomerAccess;
import com.marketplace.application.order.ConfirmOrder;
import com.marketplace.application.order.GetOrder;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OrderConfiguration {

    @Bean
    ConfirmOrder confirmOrder(
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
        return new ConfirmOrder(
                access,
                quotes,
                orders,
                credits,
                idempotency,
                outbox,
                renderer,
                payloads,
                unitOfWork,
                time,
                ids);
    }

    @Bean
    GetOrder getOrder(CustomerAccess access, OrderRepository orders) {
        return new GetOrder(access, orders);
    }
}
