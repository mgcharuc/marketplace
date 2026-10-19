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
    @Primary
    FakeErp fakeErp() {
        return new FakeErp();
    }

    @Bean
    @Primary
    FakePushProvider fakePushProvider() {
        return new FakePushProvider();
    }

    @Bean
    @Primary
    FixedJitter fixedJitter() {
        return new FixedJitter();
    }

    @Bean
    @Primary
    CrashingRelayHooks crashingRelayHooks() {
        return new CrashingRelayHooks();
    }

    @Bean
    DatabaseCleaner databaseCleaner(
            JdbcTemplate jdbc,
            LoadSeeds loadSeeds,
            FixedTimeSource time,
            SequentialIdGenerator ids,
            FailingOutbox outbox,
            FakeErp erp,
            FakePushProvider push,
            FixedJitter jitter,
            CrashingRelayHooks hooks) {
        return new DatabaseCleaner(jdbc, loadSeeds, time, ids, outbox, erp, push, jitter, hooks);
    }
}
