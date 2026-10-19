package com.marketplace.domain.credit;

import com.marketplace.domain.DomainException;
import com.marketplace.domain.money.Money;
import java.util.Objects;

public class CreditLimitExceededException extends DomainException {

    private final Money available;
    private final Money required;

    public CreditLimitExceededException(Money available, Money required) {
        super("CREDIT_LIMIT_EXCEEDED", "El cupo de crédito no alcanza para el pedido.");
        this.available = Objects.requireNonNull(available, "available");
        this.required = Objects.requireNonNull(required, "required");
    }

    public Money available() {
        return available;
    }

    public Money required() {
        return required;
    }
}
