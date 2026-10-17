package com.marketplace.application.port;

import com.marketplace.domain.Identifier;
import java.util.Objects;

public record DistributorSeed(String id, String name) {

    public DistributorSeed {
        Identifier.require(id, "distributorId");
        Objects.requireNonNull(name, "name");
    }
}
