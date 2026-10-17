package com.marketplace.domain.config;

import com.marketplace.domain.money.CurrencyConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Ámbito fiscal y monetario. Todos los tenants del país comparten su moneda, su redondeo, la
 * política del obsequio y las tasas de sus clases tributarias.
 */
public record Country(
        String code,
        CurrencyConfig currency,
        GiftTaxPolicy giftTaxPolicy,
        List<TaxClass> taxClasses) {

    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z]{2}");

    public Country {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(giftTaxPolicy, "giftTaxPolicy");
        if (!CODE_FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("Código de país inválido: " + code);
        }
        taxClasses = List.copyOf(taxClasses);
        var codes = new HashSet<String>();
        for (TaxClass taxClass : taxClasses) {
            if (!codes.add(taxClass.code())) {
                throw new IllegalArgumentException(
                        "Clase tributaria repetida en " + code + ": " + taxClass.code());
            }
        }
    }

    public Optional<TaxClass> taxClass(String code) {
        return taxClasses.stream().filter(taxClass -> taxClass.code().equals(code)).findFirst();
    }
}
