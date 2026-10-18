package com.marketplace.support;

import com.marketplace.application.seed.LoadSeeds;
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
    DatabaseCleaner databaseCleaner(
            JdbcTemplate jdbc,
            LoadSeeds loadSeeds,
            FixedTimeSource time,
            SequentialIdGenerator ids) {
        return new DatabaseCleaner(jdbc, loadSeeds, time, ids);
    }
}
