package com.marketplace.domain.money;

import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Moneda de un país con la escala y el modo de redondeo que usa en la práctica comercial, que puede
 * diferir de la escala oficial de la moneda.
 */
public record CurrencyConfig(String code, int scale, RoundingMode roundingMode) {

    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z]{3}");
    private static final int MAX_SCALE = 4;
    private static final Set<RoundingMode> SUPPORTED_MODES =
            Set.of(RoundingMode.HALF_UP, RoundingMode.HALF_EVEN);

    public CurrencyConfig {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(roundingMode, "roundingMode");
        if (!CODE_FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("Código de moneda inválido: " + code);
        }
        if (scale < 0 || scale > MAX_SCALE) {
            throw new IllegalArgumentException("Escala fuera de rango: " + scale);
        }
        if (!SUPPORTED_MODES.contains(roundingMode)) {
            throw new IllegalArgumentException("Modo de redondeo no admitido: " + roundingMode);
        }
    }
}
