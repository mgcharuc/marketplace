package com.marketplace.domain.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Monto no negativo expresado en la escala de su moneda. Un valor con menos decimales se completa
 * con ceros y uno con más se rechaza, porque la única forma de redondear es {@link #round}.
 */
public record Money(BigDecimal amount, CurrencyConfig currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("El monto no puede ser negativo: " + amount);
        }
        if (amount.scale() > currency.scale()) {
            throw new IllegalArgumentException(
                    "El monto " + amount + " tiene más decimales que " + currency.code());
        }
        amount = amount.setScale(currency.scale());
    }

    /**
     * Redondea un valor intermedio a la escala y con el modo de la moneda. Es el único paso de un
     * cálculo con precisión completa a un monto.
     */
    public static Money round(BigDecimal value, CurrencyConfig currency) {
        return new Money(value.setScale(currency.scale(), currency.roundingMode()), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        if (amount.compareTo(other.amount) < 0) {
            throw new IllegalArgumentException(
                    "La resta de " + other.amount + " a " + amount + " deja un monto negativo");
        }
        return new Money(amount.subtract(other.amount), currency);
    }

    /**
     * Multiplica por una tasa o un porcentaje sin redondear. El resultado es un valor intermedio.
     */
    public BigDecimal times(BigDecimal factor) {
        return amount.multiply(factor);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "No se puede operar " + currency + " con " + other.currency);
        }
    }
}
