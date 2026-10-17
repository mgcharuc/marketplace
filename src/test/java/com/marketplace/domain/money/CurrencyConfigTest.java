package com.marketplace.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CurrencyConfigTest {

    @Test
    @DisplayName("Acepta una moneda con código de tres letras, escala entre 0 y 4 y modo admitido")
    void acceptsValidCurrency() {
        var cop = new CurrencyConfig("COP", 0, RoundingMode.HALF_UP);

        assertThat(cop.code()).isEqualTo("COP");
        assertThat(cop.scale()).isZero();
        assertThat(cop.roundingMode()).isEqualTo(RoundingMode.HALF_UP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "US", "USDX", "usd", "U5D"})
    @DisplayName("Rechaza un código que no sea de tres letras mayúsculas")
    void rejectsInvalidCode(String code) {
        assertThatThrownBy(() -> new CurrencyConfig(code, 2, RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 5})
    @DisplayName("Rechaza una escala fuera del rango de 0 a 4")
    void rejectsScaleOutOfRange(int scale) {
        assertThatThrownBy(() -> new CurrencyConfig("USD", scale, RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = RoundingMode.class,
            names = {"HALF_UP", "HALF_EVEN"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Solo admite redondeo al más cercano, con empate hacia arriba o hacia el par")
    void rejectsUnsupportedRoundingMode(RoundingMode mode) {
        assertThatThrownBy(() -> new CurrencyConfig("USD", 2, mode))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza código o modo de redondeo nulos")
    void rejectsNulls() {
        assertThatThrownBy(() -> new CurrencyConfig(null, 2, RoundingMode.HALF_UP))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CurrencyConfig("USD", 2, null))
                .isInstanceOf(NullPointerException.class);
    }
}
