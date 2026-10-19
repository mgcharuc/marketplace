package com.marketplace.adapters.persistence;

import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.order.DuplicateOrderException;
import java.util.Optional;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class JpaUnitOfWork implements UnitOfWork {

    private static final String ORDER_QUOTE_UNIQUE = "orders_quote_uk";

    private final TransactionTemplate transaction;

    JpaUnitOfWork(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // La violación se traduce después del rollback, fuera del callback de la transacción.
    @Override
    public <T> T execute(Supplier<T> work) {
        try {
            return transaction.execute(status -> work.get());
        } catch (DataIntegrityViolationException violation) {
            if (constraint(violation).filter(ORDER_QUOTE_UNIQUE::equals).isPresent()) {
                throw new DuplicateOrderException(violation);
            }
            throw violation;
        }
    }

    private static Optional<String> constraint(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return Optional.ofNullable(violation.getConstraintName());
            }
        }
        return Optional.empty();
    }
}
