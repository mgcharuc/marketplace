package com.marketplace.adapters.persistence.credit;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.application.port.CreditRepository;
import com.marketplace.domain.config.ConfigFixtures;
import com.marketplace.domain.credit.CreditKey;
import com.marketplace.domain.money.Money;
import com.marketplace.support.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JpaCreditRepositoryIT {

    private static final CreditKey LOW_NORTH =
            new CreditKey("CO-MARCA-A", "CLI-CREDITO-BAJO", "DIST-NORTE");

    @Autowired CreditRepository credits;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Consumir cupo suficiente descuenta el monto y devuelve el saldo que queda")
    void consumesWhenAvailableCovers() {
        var after = credits.consume(LOW_NORTH, cop("28858"));

        assertThat(after).hasValue(cop("21142"));
        assertThat(available(LOW_NORTH)).isEqualByComparingTo("21142");
    }

    @Test
    @DisplayName("Consumir exactamente el cupo disponible lo deja en cero")
    void consumesTheWholeBalance() {
        assertThat(credits.consume(LOW_NORTH, cop("50000"))).hasValue(cop("0"));
    }

    @Test
    @DisplayName("Si el cupo no alcanza no se consume nada")
    void rejectsWhenAvailableIsShort() {
        assertThat(credits.consume(LOW_NORTH, cop("50001"))).isEmpty();
        assertThat(available(LOW_NORTH)).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("Sin cuenta para el par tendero y distribuidor no se consume nada")
    void rejectsUnknownAccount() {
        var cashCustomer = new CreditKey("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE");

        assertThat(credits.consume(cashCustomer, cop("1"))).isEmpty();
        assertThat(credits.find(cashCustomer, ConfigFixtures.CO.currency())).isEmpty();
    }

    @Test
    @DisplayName("El consumo afecta solo la cuenta con ese distribuidor")
    void consumesOnlyTheGivenDistributor() {
        var lowSouth = new CreditKey("CO-MARCA-A", "CLI-CREDITO-BAJO", "DIST-SUR");

        credits.consume(lowSouth, cop("56882"));

        assertThat(available(lowSouth)).isEqualByComparingTo("43118");
        assertThat(available(LOW_NORTH)).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("La cuenta de un tenant no se alcanza con la clave de otro tenant")
    void isolatesTenants() {
        var otherTenant = new CreditKey("CO-MARCA-B", "CLI-CREDITO-BAJO", "DIST-NORTE");

        assertThat(credits.consume(otherTenant, cop("1"))).isEmpty();
        assertThat(credits.find(otherTenant, ConfigFixtures.CO.currency())).isEmpty();
        assertThat(available(LOW_NORTH)).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("La cuenta se lee con los montos en la escala de la moneda del país")
    void readsAccountInCurrencyScale() {
        var cop = credits.find(LOW_NORTH, ConfigFixtures.CO.currency()).orElseThrow();
        var usd =
                credits.find(
                                new CreditKey("EC-MARCA-A", "CLI-EC-01", "DIST-EC-01"),
                                ConfigFixtures.EC.currency())
                        .orElseThrow();

        assertThat(cop.key()).isEqualTo(LOW_NORTH);
        assertThat(cop.creditLimit().amount()).isEqualTo("50000");
        assertThat(cop.available().amount()).isEqualTo("50000");
        assertThat(usd.available().amount()).isEqualTo("500.00");
        assertThat(usd.available().currency().code()).isEqualTo("USD");
    }

    @Test
    @DisplayName("El saldo que devuelve el consumo está en la escala de la moneda")
    void consumeReturnsBalanceInCurrencyScale() {
        var usd = new CreditKey("EC-MARCA-A", "CLI-EC-01", "DIST-EC-01");
        var currency = ConfigFixtures.EC.currency();

        assertThat(credits.consume(usd, new Money(new BigDecimal("2.30"), currency)))
                .hasValueSatisfying(value -> assertThat(value.amount()).isEqualTo("497.70"));
    }

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), ConfigFixtures.CO.currency());
    }

    private BigDecimal available(CreditKey key) {
        return jdbc.queryForObject(
                "SELECT available FROM credit_account"
                        + " WHERE tenant_id = ? AND customer_id = ? AND distributor_id = ?",
                BigDecimal.class,
                key.tenantId(),
                key.customerId(),
                key.distributorId());
    }
}
