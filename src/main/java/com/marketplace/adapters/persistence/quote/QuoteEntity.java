package com.marketplace.adapters.persistence.quote;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import com.marketplace.adapters.persistence.StoredAmounts;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteHeader;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "quote")
class QuoteEntity extends AssignedIdEntity<String> {

    @Id private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "distributor_id")
    private String distributorId;

    @Column(name = "config_version")
    private int configVersion;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(precision = 19, scale = 4)
    private BigDecimal total;

    private String snapshot;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected QuoteEntity() {}

    QuoteEntity(Quote quote) {
        QuoteHeader header = quote.header();
        this.id = header.quoteId();
        this.tenantId = header.tenantId();
        this.customerId = header.customerId();
        this.distributorId = header.distributorId();
        this.configVersion = header.configVersion();
        this.paymentMethod = header.paymentMethod().name();
        this.total = quote.total().amount();
        this.snapshot = quote.snapshot();
        this.createdAt = header.createdAt();
        this.expiresAt = header.expiresAt();
    }

    @Override
    public String getId() {
        return id;
    }

    Quote toDomain(CurrencyConfig currency) {
        var header =
                new QuoteHeader(
                        id,
                        tenantId,
                        customerId,
                        distributorId,
                        configVersion,
                        currency,
                        PaymentMethod.valueOf(paymentMethod),
                        createdAt,
                        expiresAt);
        return new Quote(header, StoredAmounts.money(total, currency), snapshot);
    }
}
