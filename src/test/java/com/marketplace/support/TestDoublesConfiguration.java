package com.marketplace.support;

import com.marketplace.application.port.Outbox;
import com.marketplace.application.seed.LoadSeeds;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@TestConfiguration(proxyBeanMethods = false)
public class TestDoublesConfiguration {

    @Bean
    @Primary
    FixedTimeSource fixedTimeSource() {
        return new FixedTimeSource();
    }

    @Bean
    @Primary
    SequentialIdGenerator sequentialIdGenerator() {
        return new SequentialIdGenerator();
    }

    @Bean
    @Primary
    FailingOutbox failingOutbox(@Qualifier("jpaOutbox") Outbox outbox) {
        return new FailingOutbox(outbox);
    }

    @Bean
    DatabaseCleaner databaseCleaner(
            JdbcTemplate jdbc,
            LoadSeeds loadSeeds,
            FixedTimeSource time,
            SequentialIdGenerator ids,
            FailingOutbox outbox) {
        return new DatabaseCleaner(jdbc, loadSeeds, time, ids, outbox);
    }
}
