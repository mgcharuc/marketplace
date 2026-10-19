package com.marketplace.domain.order;

import com.marketplace.domain.DomainException;

public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException() {
        super("ORDER_NOT_FOUND", "La orden no existe.");
    }
}
