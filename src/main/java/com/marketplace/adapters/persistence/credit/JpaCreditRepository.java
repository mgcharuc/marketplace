package com.marketplace.adapters.persistence.credit;

import com.marketplace.adapters.persistence.StoredAmounts;
import com.marketplace.application.port.CreditRepository;
import com.marketplace.domain.credit.CreditAccount;
import com.marketplace.domain.credit.CreditKey;
import com.marketplace.domain.credit.CreditMovement;
import com.marketplace.domain.money.CurrencyConfig;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaCreditRepository implements CreditRepository {

    private final CreditAccountEntityRepository accounts;
    private final CreditMovementEntityRepository movements;

    JpaCreditRepository(
            CreditAccountEntityRepository accounts, CreditMovementEntityRepository movements) {
        this.accounts = accounts;
        this.movements = movements;
    }

    @Override
    public Optional<CreditAccount> find(CreditKey key, CurrencyConfig currency) {
        return accounts.findById(CreditAccountId.of(key))
                .map(account -> account.toDomain(currency));
    }

    @Override
    public void open(CreditKey key, BigDecimal creditLimit) {
        accounts.save(new CreditAccountEntity(CreditAccountId.of(key), creditLimit));
    }

    @Override
    public Optional<Money> consume(CreditKey key, Money amount) {
        return accounts.consume(CreditAccountId.of(key), amount.amount())
                .map(available -> StoredAmounts.money(available, amount.currency()));
    }

    @Override
    public void record(CreditMovement movement) {
        movements.save(new CreditMovementEntity(movement));
    }
}
