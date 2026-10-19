package com.marketplace.adapters.persistence.quote;

import com.marketplace.application.port.QuoteRepository;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteKey;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaQuoteRepository implements QuoteRepository {

    private final QuoteEntityRepository quotes;
    private final TenantRepository tenants;

    JpaQuoteRepository(QuoteEntityRepository quotes, TenantRepository tenants) {
        this.quotes = quotes;
        this.tenants = tenants;
    }

    @Override
    public void save(Quote quote) {
        quotes.save(new QuoteEntity(quote));
    }

    @Override
    public Optional<Quote> find(QuoteKey key) {
        return quotes.findByIdAndTenantIdAndCustomerId(
                        key.quoteId(), key.tenantId(), key.customerId())
                .map(quote -> quote.toDomain(currency(key.tenantId())));
    }

    @Override
    public Optional<Quote> lock(QuoteKey key, CurrencyConfig currency) {
        return quotes.findLockedByIdAndTenantIdAndCustomerId(
                        key.quoteId(), key.tenantId(), key.customerId())
                .map(quote -> quote.toDomain(currency));
    }

    private CurrencyConfig currency(String tenantId) {
        return tenants.find(tenantId)
                .map(Tenant::currency)
                .orElseThrow(() -> new IllegalStateException("Tenant sin registrar: " + tenantId));
    }
}
