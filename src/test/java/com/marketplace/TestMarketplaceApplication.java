package com.marketplace;

import com.marketplace.support.PostgresContainerConfiguration;
import org.springframework.boot.SpringApplication;

public class TestMarketplaceApplication {

    public static void main(String[] args) {
        SpringApplication.from(MarketplaceApplication::main)
                .with(PostgresContainerConfiguration.class)
                .run(args);
    }
}
