package com.marketplace.bootstrap;

import com.marketplace.application.outbox.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "marketplace.relay.scheduler.enabled", havingValue = "true")
class RelaySchedulerConfiguration {

    private final OutboxRelay relay;

    RelaySchedulerConfiguration(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelay = 1000)
    void deliverPendingEvents() {
        relay.runOnce();
    }
}
