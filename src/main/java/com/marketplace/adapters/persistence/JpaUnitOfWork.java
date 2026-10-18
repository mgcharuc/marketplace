package com.marketplace.adapters.persistence;

import com.marketplace.application.port.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class JpaUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transaction;

    JpaUnitOfWork(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }
}
