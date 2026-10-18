package com.marketplace.pricing;

import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.Tenant;
import java.time.Instant;
import java.util.Objects;

/** Todo lo que el motor necesita para calcular un carrito, ya resuelto para el tenant. */
public record PricingInput(
        ConfigVersion config, Tenant tenant, Cart cart, CreditAvailability credit, Instant now) {

    public PricingInput {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(cart, "cart");
        Objects.requireNonNull(credit, "credit");
        Objects.requireNonNull(now, "now");
        if (!config.tenantId().equals(tenant.id())) {
            throw new IllegalArgumentException(
                    "La versión es de " + config.tenantId() + " y no de " + tenant.id());
        }
        if (credit instanceof CreditAvailability.Available(var available)
                && !available.currency().equals(tenant.currency())) {
            throw new IllegalArgumentException(
                    "El cupo está en "
                            + available.currency().code()
                            + " y el tenant usa "
                            + tenant.currency().code());
        }
        for (CartLine line : cart.lines()) {
            if (config.product(line.sku()).isEmpty()) {
                throw new IllegalArgumentException(
                        "El SKU " + line.sku().value() + " no existe en la versión");
            }
        }
    }
}
