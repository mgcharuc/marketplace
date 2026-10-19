package com.marketplace.bootstrap;

import com.marketplace.adapters.delivery.ErpDeliverer;
import com.marketplace.adapters.delivery.LoggingErpClient;
import com.marketplace.adapters.delivery.LoggingPushClient;
import com.marketplace.adapters.delivery.PushDeliverer;
import com.marketplace.adapters.system.RandomJitter;
import com.marketplace.application.outbox.OutboxRelay;
import com.marketplace.application.port.DeliveryLog;
import com.marketplace.application.port.ErpClient;
import com.marketplace.application.port.EventDeliverer;
import com.marketplace.application.port.Jitter;
import com.marketplace.application.port.OutboxRepository;
import com.marketplace.application.port.PushClient;
import com.marketplace.application.port.TimeSource;
import com.marketplace.application.port.UnitOfWork;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OutboxConfiguration {

    @Bean
    ErpClient erpClient() {
        return new LoggingErpClient();
    }

    @Bean
    PushClient pushClient() {
        return new LoggingPushClient();
    }

    @Bean
    Jitter jitter() {
        return new RandomJitter();
    }

    @Bean
    ErpDeliverer erpDeliverer(ErpClient erp, DeliveryLog deliveries, TimeSource time) {
        return new ErpDeliverer(erp, deliveries, time);
    }

    @Bean
    PushDeliverer pushDeliverer(PushClient push, DeliveryLog deliveries, TimeSource time) {
        return new PushDeliverer(push, deliveries, time);
    }

    @Bean
    OutboxRelay outboxRelay(
            OutboxRepository events,
            List<EventDeliverer> deliverers,
            UnitOfWork unitOfWork,
            TimeSource time,
            Jitter jitter) {
        return new OutboxRelay(events, deliverers, unitOfWork, time, jitter);
    }
}
