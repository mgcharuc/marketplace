package com.marketplace.adapters.persistence.credit;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import com.marketplace.adapters.persistence.StoredAmounts;
import com.marketplace.domain.credit.CreditAccount;
import com.marketplace.domain.money.CurrencyConfig;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "credit_account")
class CreditAccountEntity extends AssignedIdEntity<CreditAccountId> {

    @EmbeddedId private CreditAccountId id;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(precision = 19, scale = 4)
    private BigDecimal available;

    protected CreditAccountEntity() {}

    CreditAccountEntity(CreditAccountId id, BigDecimal creditLimit) {
        this.id = id;
        this.creditLimit = creditLimit;
        this.available = creditLimit;
    }

    @Override
    public CreditAccountId getId() {
        return id;
    }

    CreditAccount toDomain(CurrencyConfig currency) {
        return new CreditAccount(
                id.toKey(),
                StoredAmounts.money(creditLimit, currency),
                StoredAmounts.money(available, currency));
    }
}
