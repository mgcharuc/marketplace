package com.marketplace.application.port;

import com.marketplace.domain.Identifier;
import java.util.List;
import java.util.Objects;

public record CustomerSeed(String id, String name, List<CreditAccountSeed> creditAccounts) {

    public CustomerSeed {
        Identifier.require(id, "customerId");
        Objects.requireNonNull(name, "name");
        creditAccounts = List.copyOf(creditAccounts);
    }
}
