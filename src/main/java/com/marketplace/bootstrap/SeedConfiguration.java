package com.marketplace.bootstrap;

import com.marketplace.application.port.ConfigRepository;
import com.marketplace.application.port.CreditRepository;
import com.marketplace.application.port.CustomerRepository;
import com.marketplace.application.port.DistributorRepository;
import com.marketplace.application.port.SeedSource;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.application.port.TimeSource;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.application.seed.LoadSeeds;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SeedConfiguration {

    @Bean
    LoadSeeds loadSeeds(
            SeedSource seeds,
            TenantRepository tenants,
            DistributorRepository distributors,
            CustomerRepository customers,
            CreditRepository credits,
            ConfigRepository configs,
            UnitOfWork unitOfWork,
            TimeSource time) {
        return new LoadSeeds(
                seeds, tenants, distributors, customers, credits, configs, unitOfWork, time);
    }

    @Bean
    ApplicationRunner loadSeedsOnStartup(LoadSeeds loadSeeds) {
        return arguments -> loadSeeds.handle();
    }
}
