package com.marketplace.domain.outbox;

import com.marketplace.domain.DomainException;

public class EventNotFoundException extends DomainException {

    public EventNotFoundException() {
        super("EVENT_NOT_FOUND", "El evento no existe.");
    }
}
