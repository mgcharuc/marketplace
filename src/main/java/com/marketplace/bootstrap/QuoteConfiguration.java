package com.marketplace.bootstrap;

import com.marketplace.application.CustomerAccess;
import com.marketplace.application.port.ConfigRepository;
import com.marketplace.application.port.CreditRepository;
import com.marketplace.application.port.CustomerRepository;
import com.marketplace.application.port.DistributorRepository;
import com.marketplace.application.port.IdGenerator;
import com.marketplace.application.port.QuoteRenderer;
import com.marketplace.application.port.QuoteRepository;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.application.port.TimeSource;
import com.marketplace.application.quote.GetQuote;
import com.marketplace.application.quote.QuoteCart;
import com.marketplace.pricing.PricingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class QuoteConfiguration {

    @Bean
    PricingEngine pricingEngine() {
        return new PricingEngine();
    }

    @Bean
    CustomerAccess customerAccess(TenantRepository tenants, CustomerRepository customers) {
        return new CustomerAccess(tenants, customers);
    }

    @Bean
    QuoteCart quoteCart(
            CustomerAccess access,
            DistributorRepository distributors,
            ConfigRepository configs,
            CreditRepository credits,
            QuoteRepository quotes,
            QuoteRenderer renderer,
            PricingEngine engine,
            TimeSource time,
            IdGenerator ids) {
        return new QuoteCart(
                access, distributors, configs, credits, quotes, renderer, engine, time, ids);
    }

    @Bean
    GetQuote getQuote(CustomerAccess access, QuoteRepository quotes) {
        return new GetQuote(access, quotes);
    }
}
