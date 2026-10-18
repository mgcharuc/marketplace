package com.marketplace.domain.credit;

import com.marketplace.domain.DomainException;

public class CreditAccountNotFoundException extends DomainException {

    public CreditAccountNotFoundException() {
        super(
                "CREDIT_ACCOUNT_NOT_FOUND",
                "El tendero no tiene cupo de crédito con ese distribuidor.");
    }
}
