package com.marketplace.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuantityTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 25, 9999})
    @DisplayName("Acepta cantidades entre 1 y 9999")
    void acceptsQuantityInRange(int value) {
        assertThat(new Quantity(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 10000})
    @DisplayName("Rechaza cantidades menores que 1 o mayores que 9999")
    void rejectsQuantityOutOfRange(int value) {
        assertThatThrownBy(() -> new Quantity(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
