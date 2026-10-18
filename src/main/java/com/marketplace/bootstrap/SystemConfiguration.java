package com.marketplace.bootstrap;

import com.marketplace.adapters.system.RandomIdGenerator;
import com.marketplace.adapters.system.SystemTimeSource;
import com.marketplace.application.port.IdGenerator;
import com.marketplace.application.port.TimeSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SystemConfiguration {

    @Bean
    TimeSource timeSource() {
        return new SystemTimeSource();
    }

    @Bean
    IdGenerator idGenerator() {
        return new RandomIdGenerator();
    }
}
