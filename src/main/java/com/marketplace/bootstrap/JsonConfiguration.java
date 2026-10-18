package com.marketplace.bootstrap;

import com.marketplace.adapters.json.JsonConfigCodec;
import com.marketplace.adapters.json.JsonQuoteRenderer;
import com.marketplace.adapters.json.JsonSeedSource;
import com.marketplace.application.port.ConfigCodec;
import com.marketplace.application.port.QuoteRenderer;
import com.marketplace.application.port.SeedSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class JsonConfiguration {

    @Bean
    ConfigCodec configCodec() {
        return new JsonConfigCodec();
    }

    @Bean
    QuoteRenderer quoteRenderer() {
        return new JsonQuoteRenderer();
    }

    @Bean
    SeedSource seedSource() {
        return new JsonSeedSource();
    }
}
