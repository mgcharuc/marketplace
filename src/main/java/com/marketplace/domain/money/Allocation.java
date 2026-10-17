package com.marketplace.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Reparto de un monto en partes proporcionales por mayor residuo. Se usa para repartir el combo
 * entre líneas y el monto de una escala entre sus tramos.
 */
public final class Allocation {

    private Allocation() {}

    /**
     * Reparte el monto en proporción a los pesos. Cada cuota se trunca a la escala de la moneda y
     * las unidades mínimas que sobran van, de a una, a las partes con mayor fracción descartada. Si
     * dos partes empatan, gana la de clave menor (el SKU o el inicio del tramo). La suma de las
     * cuotas es siempre el monto repartido.
     */
    public static <K extends Comparable<? super K>> SortedMap<K, Money> split(
            Money amount, Map<K, BigDecimal> weights) {
        BigDecimal totalWeight = totalOf(weights);
        SortedMap<K, BigDecimal> ordered = new TreeMap<>(weights);
        if (totalWeight.signum() == 0) {
            return zeros(amount, ordered);
        }
        return withLeftover(amount, truncatedShares(amount, ordered, totalWeight));
    }

    private static <K> SortedMap<K, Money> zeros(Money amount, SortedMap<K, BigDecimal> weights) {
        if (amount.amount().signum() > 0) {
            throw new IllegalArgumentException("No se puede repartir un monto entre pesos en cero");
        }
        SortedMap<K, Money> result = new TreeMap<>(weights.comparator());
        weights.keySet().forEach(key -> result.put(key, amount));
        return Collections.unmodifiableSortedMap(result);
    }

    private static BigDecimal totalOf(Map<?, BigDecimal> weights) {
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("El reparto necesita al menos una parte");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal weight : weights.values()) {
            Objects.requireNonNull(weight, "weight");
            if (weight.signum() < 0) {
                throw new IllegalArgumentException("Peso negativo en el reparto: " + weight);
            }
            total = total.add(weight);
        }
        return total;
    }

    private static <K> List<Share<K>> truncatedShares(
            Money amount, SortedMap<K, BigDecimal> weights, BigDecimal totalWeight) {
        int scale = amount.currency().scale();
        List<Share<K>> shares = new ArrayList<>(weights.size());
        for (var weight : weights.entrySet()) {
            BigDecimal numerator = amount.amount().multiply(weight.getValue());
            BigDecimal quota = numerator.divide(totalWeight, scale, RoundingMode.DOWN);
            BigDecimal remainder = numerator.subtract(quota.multiply(totalWeight));
            shares.add(new Share<>(weight.getKey(), quota, remainder));
        }
        return shares;
    }

    private static <K extends Comparable<? super K>> SortedMap<K, Money> withLeftover(
            Money amount, List<Share<K>> shares) {
        CurrencyConfig currency = amount.currency();
        BigDecimal unit = BigDecimal.ONE.movePointLeft(currency.scale());
        BigDecimal assigned =
                shares.stream().map(Share::quota).reduce(BigDecimal.ZERO, BigDecimal::add);
        int leftover =
                amount.amount().subtract(assigned).movePointRight(currency.scale()).intValueExact();

        List<Share<K>> byRemainder = new ArrayList<>(shares);
        byRemainder.sort(
                Comparator.comparing(Share<K>::remainder).reversed().thenComparing(Share::key));

        SortedMap<K, Money> result = new TreeMap<>();
        for (int i = 0; i < byRemainder.size(); i++) {
            Share<K> share = byRemainder.get(i);
            BigDecimal quota = i < leftover ? share.quota().add(unit) : share.quota();
            result.put(share.key(), new Money(quota, currency));
        }
        return Collections.unmodifiableSortedMap(result);
    }

    private record Share<K>(K key, BigDecimal quota, BigDecimal remainder) {}
}
