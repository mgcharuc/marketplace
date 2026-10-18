package com.marketplace.adapters.persistence.credit;

import com.marketplace.adapters.persistence.StoredAmounts;
import com.marketplace.application.port.CreditRepository;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.credit.CreditAccount;
import com.marketplace.domain.credit.CreditKey;
import com.marketplace.domain.money.CurrencyConfig;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaCreditRepository implements CreditRepository {

    private final CreditAccountEntityRepository accounts;
    private final TenantRepository tenants;

    JpaCreditRepository(CreditAccountEntityRepository accounts, TenantRepository tenants) {
        this.accounts = accounts;
        this.tenants = tenants;
    }

    @Override
    public Optional<CreditAccount> find(CreditKey key) {
        return accounts.findById(CreditAccountId.of(key))
                .map(account -> account.toDomain(currency(key)));
    }

    @Override
    public void open(CreditKey key, BigDecimal creditLimit) {
        accounts.save(new CreditAccountEntity(CreditAccountId.of(key), creditLimit));
    }

    @Override
    public Optional<BigDecimal> consume(CreditKey key, BigDecimal amount) {
        return accounts.consume(CreditAccountId.of(key), amount)
                .map(available -> StoredAmounts.inScale(available, currency(key)));
    }

    private CurrencyConfig currency(CreditKey key) {
        return tenants.find(key.tenantId())
                .map(Tenant::currency)
                .orElseThrow(
                        () -> new IllegalStateException("Tenant sin registrar: " + key.tenantId()));
    }
}
