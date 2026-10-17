package com.marketplace.application.port;

import com.marketplace.domain.config.Tenant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record TenantSeed(
        Tenant tenant, List<DistributorSeed> distributors, List<CustomerSeed> customers) {

    public TenantSeed {
        Objects.requireNonNull(tenant, "tenant");
        distributors = List.copyOf(distributors);
        customers = List.copyOf(customers);
        requireUnique(distributors, DistributorSeed::id, "Distribuidor repetido");
        requireUnique(customers, CustomerSeed::id, "Tendero repetido");
        Set<String> distributorIds =
                distributors.stream().map(DistributorSeed::id).collect(Collectors.toSet());
        for (CustomerSeed customer : customers) {
            requireUnique(
                    customer.creditAccounts(),
                    CreditAccountSeed::distributorId,
                    "Cuenta de crédito repetida para " + customer.id());
            customer.creditAccounts().stream()
                    .filter(account -> !distributorIds.contains(account.distributorId()))
                    .findFirst()
                    .ifPresent(
                            account -> {
                                throw new IllegalArgumentException(
                                        "El distribuidor "
                                                + account.distributorId()
                                                + " no es de "
                                                + tenant.id());
                            });
        }
    }

    private static <T> void requireUnique(List<T> items, Function<T, String> key, String error) {
        var seen = new HashSet<String>();
        for (T item : items) {
            if (!seen.add(key.apply(item))) {
                throw new IllegalArgumentException(error + ": " + key.apply(item));
            }
        }
    }
}
