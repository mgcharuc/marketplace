package com.marketplace.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final CurrencyConfig USD = new CurrencyConfig("USD", 2, RoundingMode.HALF_UP);
    private static final CurrencyConfig USD_EVEN =
            new CurrencyConfig("USD", 2, RoundingMode.HALF_EVEN);
    private static final CurrencyConfig COP = new CurrencyConfig("COP", 0, RoundingMode.HALF_UP);
    private static final CurrencyConfig COP_EVEN =
            new CurrencyConfig("COP", 0, RoundingMode.HALF_EVEN);

    private static Money usd(String amount) {
        return new Money(new BigDecimal(amount), USD);
    }

    @Test
    @DisplayName("Completa con ceros un monto con menos decimales que la moneda")
    void padsLowerScaleWithZeros() {
        assertThat(usd("2.3").amount().toPlainString()).isEqualTo("2.30");
        assertThat(usd("7").amount().toPlainString()).isEqualTo("7.00");
    }

    @Test
    @DisplayName("Rechaza un monto con más decimales que la moneda en lugar de redondearlo")
    void rejectsHigherScale() {
        assertThatThrownBy(() -> usd("2.345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(new BigDecimal("10.5"), COP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza montos negativos y valores nulos")
    void rejectsNegativeAndNull() {
        assertThatThrownBy(() -> usd("-0.01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(null, USD)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("El monto de varias unidades conserva la escala de la moneda sin redondear")
    void multipliesByUnits() {
        assertThat(usd("0.35").forUnits(7)).isEqualTo(usd("2.45"));
        assertThat(usd("0.35").forUnits(0)).isEqualTo(usd("0"));
        assertThatThrownBy(() -> usd("0.35").forUnits(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Dos montos iguales con distinta cantidad de decimales son el mismo valor")
    void equalityIgnoresInputScale() {
        assertThat(usd("2.3")).isEqualTo(usd("2.30"));
    }

    @Test
    @DisplayName("Suma y resta montos de la misma moneda")
    void addsAndSubtractsSameCurrency() {
        assertThat(usd("2.30").plus(usd("0.75"))).isEqualTo(usd("3.05"));
        assertThat(usd("2.30").minus(usd("0.75"))).isEqualTo(usd("1.55"));
    }

    @Test
    @DisplayName("Una resta que deja el monto negativo se rechaza")
    void rejectsSubtractionBelowZero() {
        assertThatThrownBy(() -> usd("1.00").minus(usd("1.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resta");
    }

    @Test
    @DisplayName("No suma ni resta montos de monedas distintas")
    void rejectsOperationsBetweenCurrencies() {
        var cop = new Money(new BigDecimal("100"), COP);

        assertThatThrownBy(() -> usd("1.00").plus(cop))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> usd("1.00").minus(cop))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("No opera montos de la misma moneda con distinto modo de redondeo")
    void rejectsOperationsBetweenRoundingModes() {
        var even = new Money(new BigDecimal("1.00"), USD_EVEN);

        assertThatThrownBy(() -> usd("1.00").plus(even))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HALF_EVEN");
    }

    @Test
    @DisplayName(
            "Multiplicar por una tasa devuelve un decimal con precisión completa, sin redondear")
    void timesKeepsFullPrecision() {
        BigDecimal tax = usd("10.05").times(new BigDecimal("0.19"));

        assertThat(tax.toPlainString()).isEqualTo("1.9095");
    }

    @Test
    @DisplayName("En el punto medio, el redondeo hacia arriba sube y el redondeo al par baja")
    void roundsHalfUpAndHalfEvenAtMidpoint() {
        var midpoint = new BigDecimal("0.125");

        assertThat(Money.round(midpoint, USD)).isEqualTo(usd("0.13"));
        assertThat(Money.round(midpoint, USD_EVEN))
                .isEqualTo(new Money(new BigDecimal("0.12"), USD_EVEN));
    }

    @Test
    @DisplayName("Con escala 0, el punto medio se redondea según el modo de la moneda")
    void roundsMidpointWithScaleZero() {
        var midpoint = new BigDecimal("2.5");

        assertThat(Money.round(midpoint, COP).amount()).isEqualByComparingTo("3");
        assertThat(Money.round(midpoint, COP_EVEN).amount()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("Fuera del punto medio, ambos modos redondean al valor más cercano")
    void roundsToNearestOutsideMidpoint() {
        assertThat(Money.round(new BigDecimal("1.9095"), USD)).isEqualTo(usd("1.91"));
        assertThat(Money.round(new BigDecimal("1.9049"), USD_EVEN).amount().toPlainString())
                .isEqualTo("1.90");
    }
}
