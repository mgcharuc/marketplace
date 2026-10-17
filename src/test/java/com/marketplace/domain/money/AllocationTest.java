package com.marketplace.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.catalog.Sku;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AllocationTest {

    private static final CurrencyConfig COP = new CurrencyConfig("COP", 0, RoundingMode.HALF_UP);
    private static final CurrencyConfig USD = new CurrencyConfig("USD", 2, RoundingMode.HALF_UP);

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }

    private static Money usd(String amount) {
        return new Money(new BigDecimal(amount), USD);
    }

    private static Map<Sku, BigDecimal> skuWeights(String... skuAndWeight) {
        var weights = new LinkedHashMap<Sku, BigDecimal>();
        for (int i = 0; i < skuAndWeight.length; i += 2) {
            weights.put(new Sku(skuAndWeight[i]), new BigDecimal(skuAndWeight[i + 1]));
        }
        return weights;
    }

    @Test
    @DisplayName("La unidad que sobra va a la parte con mayor fracción descartada")
    void givesLeftoverToLargestRemainder() {
        var shares = Allocation.split(cop("10"), skuWeights("A", "2", "B", "3", "C", "4"));

        assertThat(shares)
                .containsExactly(
                        Map.entry(new Sku("A"), cop("2")),
                        Map.entry(new Sku("B"), cop("3")),
                        Map.entry(new Sku("C"), cop("5")));
    }

    @Test
    @DisplayName("Con fracciones empatadas, la unidad que sobra va al SKU menor")
    void breaksTiesBySkuAscending() {
        var weights = skuWeights("SNACK-PAPAS", "1", "AGUA-600", "1", "GASEOSA-1500", "1");

        var shares = Allocation.split(cop("100"), weights);

        assertThat(shares)
                .containsExactly(
                        Map.entry(new Sku("AGUA-600"), cop("34")),
                        Map.entry(new Sku("GASEOSA-1500"), cop("33")),
                        Map.entry(new Sku("SNACK-PAPAS"), cop("33")));
    }

    @Test
    @DisplayName("Con fracciones empatadas, el centavo que sobra va al tramo que empieza antes")
    void breaksTiesByTierStartAscending() {
        var weights = new HashMap<Integer, BigDecimal>();
        weights.put(50, BigDecimal.ONE);
        weights.put(10, BigDecimal.ONE);
        weights.put(30, BigDecimal.ONE);

        var shares = Allocation.split(usd("10.00"), weights);

        assertThat(shares)
                .containsExactly(
                        Map.entry(10, usd("3.34")),
                        Map.entry(30, usd("3.33")),
                        Map.entry(50, usd("3.33")));
    }

    @Test
    @DisplayName("Reparte en centavos en proporción a pesos con decimales y la suma cuadra")
    void splitsProportionallyWithScaleTwo() {
        var weights = new HashMap<Integer, BigDecimal>();
        weights.put(1, new BigDecimal("4.5"));
        weights.put(11, new BigDecimal("2.25"));
        weights.put(21, new BigDecimal("3.1"));

        var shares = Allocation.split(usd("12.35"), weights);

        assertThat(shares)
                .containsExactly(
                        Map.entry(1, usd("5.64")),
                        Map.entry(11, usd("2.82")),
                        Map.entry(21, usd("3.89")));
    }

    @Test
    @DisplayName("Con varias unidades sobrantes, cada parte recibe a lo sumo una")
    void distributesSeveralLeftoverUnits() {
        var weights =
                skuWeights("A", "1", "B", "1", "C", "1", "D", "1", "E", "1", "F", "1", "G", "1");

        var shares = Allocation.split(cop("1000"), weights);

        assertThat(shares.values())
                .containsExactly(
                        cop("143"),
                        cop("143"),
                        cop("143"),
                        cop("143"),
                        cop("143"),
                        cop("143"),
                        cop("142"));
        assertThat(shares.values().stream().map(Money::amount).reduce(BigDecimal::add))
                .hasValue(new BigDecimal("1000"));
    }

    @Test
    @DisplayName("Una parte con peso cero recibe cero")
    void givesZeroToZeroWeight() {
        var shares = Allocation.split(cop("909"), skuWeights("A", "0", "B", "5"));

        assertThat(shares)
                .containsExactly(
                        Map.entry(new Sku("A"), cop("0")), Map.entry(new Sku("B"), cop("909")));
    }

    @Test
    @DisplayName("El reparto devuelto no se puede modificar")
    void returnsUnmodifiableShares() {
        var shares = Allocation.split(cop("10"), skuWeights("A", "1", "B", "1"));
        var zeroShares = Allocation.split(cop("0"), skuWeights("A", "0"));

        assertThatThrownBy(() -> shares.put(new Sku("A"), cop("0")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> zeroShares.put(new Sku("A"), cop("1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Repartir un monto cero deja todas las partes en cero")
    void splitsZeroAmount() {
        var shares = Allocation.split(usd("0"), skuWeights("A", "0", "B", "0"));

        assertThat(shares.values()).containsExactly(usd("0.00"), usd("0.00"));
    }

    @Test
    @DisplayName(
            "Rechaza un reparto sin partes, con pesos negativos o nulos, o con todos los pesos en cero y un monto mayor que cero")
    void rejectsInvalidWeights() {
        var nullWeight = new HashMap<Sku, BigDecimal>();
        nullWeight.put(new Sku("A"), null);

        assertThatThrownBy(() -> Allocation.split(cop("10"), Map.<Sku, BigDecimal>of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Allocation.split(cop("10"), skuWeights("A", "-1", "B", "2")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Allocation.split(cop("10"), nullWeight))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Allocation.split(cop("10"), skuWeights("A", "0", "B", "0")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
