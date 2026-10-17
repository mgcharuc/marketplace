package com.marketplace.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.config.Country;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.money.CurrencyConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantSeedTest {

    private static final Tenant TENANT =
            new Tenant(
                    "CO-MARCA-A",
                    new Country(
                            "CO",
                            new CurrencyConfig("COP", 0, RoundingMode.HALF_UP),
                            GiftTaxPolicy.NONE,
                            List.of()),
                    "Marca A",
                    "CO-A",
                    Duration.ofMinutes(15),
                    Duration.ofHours(24));

    private static final DistributorSeed NORTE = new DistributorSeed("DIST-NORTE", "Norte");

    private static CustomerSeed customer(String id, String... distributorIds) {
        return new CustomerSeed(
                id,
                id,
                List.of(distributorIds).stream()
                        .map(distributor -> new CreditAccountSeed(distributor, BigDecimal.TEN))
                        .toList());
    }

    @Test
    @DisplayName("Acepta tenderos con cuentas en distribuidores del tenant")
    void acceptsAccountsWithOwnDistributors() {
        var seed = new TenantSeed(TENANT, List.of(NORTE), List.of(customer("CLI-1", "DIST-NORTE")));

        assertThat(seed.customers()).hasSize(1);
    }

    @Test
    @DisplayName("Rechaza distribuidores, tenderos o cuentas de crédito repetidos")
    void rejectsDuplicates() {
        assertThatThrownBy(() -> new TenantSeed(TENANT, List.of(NORTE, NORTE), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new TenantSeed(
                                        TENANT,
                                        List.of(NORTE),
                                        List.of(customer("CLI-1"), customer("CLI-1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new TenantSeed(
                                        TENANT,
                                        List.of(NORTE),
                                        List.of(customer("CLI-1", "DIST-NORTE", "DIST-NORTE"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza una cuenta de crédito con un distribuidor de otro tenant")
    void rejectsForeignDistributor() {
        assertThatThrownBy(
                        () ->
                                new TenantSeed(
                                        TENANT,
                                        List.of(NORTE),
                                        List.of(customer("CLI-1", "DIST-OTRO"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
