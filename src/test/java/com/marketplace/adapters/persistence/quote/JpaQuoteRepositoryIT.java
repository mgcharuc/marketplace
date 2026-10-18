package com.marketplace.adapters.persistence.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.application.port.QuoteRepository;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteHeader;
import com.marketplace.domain.quote.QuoteKey;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class JpaQuoteRepositoryIT {

    private static final String SNAPSHOT =
            "{\"quoteId\": \"q_1\",  \"total\": \"2.30\" ,\"a\":[ ]}";

    @Autowired QuoteRepository quotes;
    @Autowired TenantRepository tenants;

    @Test
    @DisplayName("La cotización se lee igual a como se guardó, con el snapshot intacto")
    void readsSavedQuote() {
        var quote = quote("EC-MARCA-A", "CLI-EC-01", "DIST-EC-01", "2.3");
        quotes.save(quote);

        var found = quotes.find(new QuoteKey("EC-MARCA-A", "CLI-EC-01", quote.header().quoteId()));

        assertThat(found).contains(quote);
        assertThat(found.orElseThrow().total().amount()).isEqualTo("2.30");
        assertThat(found.orElseThrow().snapshot()).isEqualTo(SNAPSHOT);
    }

    @Test
    @DisplayName("Otro tendero u otro tenant no encuentran la cotización")
    void quoteIsScopedToTenantAndCustomer() {
        var quote = quote("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", "22610");
        quotes.save(quote);
        String id = quote.header().quoteId();

        assertThat(quotes.find(new QuoteKey("CO-MARCA-A", "CLI-CREDITO-ALTO", id))).isEmpty();
        assertThat(quotes.find(new QuoteKey("CO-MARCA-B", "CLI-CONTADO", id))).isEmpty();
        assertThat(quotes.find(new QuoteKey("CO-MARCA-A", "CLI-CONTADO", "q_otro"))).isEmpty();
    }

    private Quote quote(String tenantId, String customerId, String distributorId, String total) {
        var currency = tenants.find(tenantId).orElseThrow().currency();
        var header =
                new QuoteHeader(
                        "q_" + tenantId,
                        tenantId,
                        customerId,
                        distributorId,
                        1,
                        currency,
                        PaymentMethod.CASH,
                        FixedTimeSource.START,
                        FixedTimeSource.START.plus(Duration.ofMinutes(15)));
        return new Quote(header, new Money(new BigDecimal(total), currency), SNAPSHOT);
    }
}
