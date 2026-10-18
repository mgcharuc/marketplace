package com.marketplace.pricing;

import static com.marketplace.domain.config.ConfigFixtures.CO;
import static com.marketplace.domain.config.ConfigFixtures.CO_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.EC;
import static com.marketplace.domain.config.ConfigFixtures.EC_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.ecMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.withCountry;
import static com.marketplace.pricing.Carts.cart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.config.Country;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PricingEngineCreditTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final PricingEngine engine = new PricingEngine();

    private Breakdown price(CreditAvailability credit) {
        var breakdown =
                engine.price(
                        new PricingInput(
                                coMarcaA(1), CO_MARCA_A, cart("GASEOSA-350", 25), credit, NOW));
        BreakdownInvariants.check(breakdown);
        return breakdown;
    }

    private static CreditAvailability available(String amount) {
        return CreditAvailability.available(
                new Money(new BigDecimal(amount), CO_MARCA_A.currency()));
    }

    private static String describe(CreditCheck credit) {
        return String.join(
                " ",
                credit.available().amount().toPlainString(),
                credit.required().amount().toPlainString(),
                String.valueOf(credit.fits()),
                credit.shortfall().amount().toPlainString());
    }

    @Test
    @DisplayName(
            "Si el total supera el cupo, el pedido no cabe y se informa cuánto falta, con el obsequio incluido en el cálculo")
    void reportsShortfallWhenTotalExceedsCredit() {
        var breakdown = price(available("50000"));

        assertThat(breakdown.gifts()).hasSize(1);
        assertThat(describe(breakdown.credit())).isEqualTo("50000 56882 false 6882");
    }

    @Test
    @DisplayName("Un total igual al cupo cabe y no falta nada")
    void fitsWhenTotalEqualsCredit() {
        assertThat(describe(price(available("56882")).credit())).isEqualTo("56882 56882 true 0");
        assertThat(describe(price(available("56881")).credit())).isEqualTo("56881 56882 false 1");
    }

    @Test
    @DisplayName("Si el cliente paga el impuesto del obsequio, el cupo requerido lo incluye")
    void requiresGiftTaxChargedToCustomer() {
        var charged =
                withCountry(
                        CO_MARCA_A,
                        new Country(
                                CO.code(),
                                CO.currency(),
                                GiftTaxPolicy.CHARGED_TO_CUSTOMER,
                                CO.taxClasses()));
        var breakdown =
                engine.price(
                        new PricingInput(
                                coMarcaA(1),
                                charged,
                                cart("GASEOSA-350", 25),
                                available("50000"),
                                NOW));
        BreakdownInvariants.check(breakdown);

        assertThat(describe(breakdown.credit())).isEqualTo("50000 57282 false 7282");
    }

    @Test
    @DisplayName("En dólares los montos del crédito llevan la escala de la moneda")
    void usesCurrencyScaleInDollars() {
        var breakdown =
                engine.price(
                        new PricingInput(
                                ecMarcaA(),
                                EC_MARCA_A,
                                cart("AGUA-500", 7),
                                CreditAvailability.available(
                                        new Money(new BigDecimal("500"), EC.currency())),
                                NOW));
        BreakdownInvariants.check(breakdown);

        assertThat(describe(breakdown.credit())).isEqualTo("500.00 2.74 true 0.00");
    }

    @Test
    @DisplayName("Una compra de contado no informa crédito")
    void omitsCreditForCash() {
        assertThat(price(CreditAvailability.none()).credit()).isNull();
    }

    @Test
    @DisplayName("Rechaza un cupo en una moneda distinta de la del tenant")
    void rejectsCreditInAnotherCurrency() {
        var dollars =
                CreditAvailability.available(new Money(new BigDecimal("500.00"), EC.currency()));

        assertThatThrownBy(
                        () ->
                                new PricingInput(
                                        coMarcaA(1),
                                        CO_MARCA_A,
                                        cart("GASEOSA-350", 1),
                                        dollars,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
