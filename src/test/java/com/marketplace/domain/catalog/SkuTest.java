package com.marketplace.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SkuTest {

    @ParameterizedTest
    @ValueSource(strings = {"GASEOSA-1500", "snack_papas", "A"})
    @DisplayName("Acepta letras, dígitos, guion y guion bajo y conserva las mayúsculas")
    void acceptsValidSku(String value) {
        assertThat(new Sku(value).value()).isEqualTo(value);
    }

    @Test
    @DisplayName("Acepta hasta 64 caracteres y rechaza uno más")
    void enforcesMaxLength() {
        assertThat(new Sku("A".repeat(64)).value()).hasSize(64);
        assertThatThrownBy(() -> new Sku("A".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " GASEOSA", "GASEOSA 1500", "GASEOSA.1500", "AGUA-ÑOÑA"})
    @DisplayName("Rechaza vacío, espacios y caracteres fuera del formato")
    void rejectsInvalidFormat(String value) {
        assertThatThrownBy(() -> new Sku(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza un SKU nulo")
    void rejectsNull() {
        assertThatThrownBy(() -> new Sku(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Distingue mayúsculas y se ordena alfabéticamente")
    void isCaseSensitiveAndOrdered() {
        assertThat(new Sku("agua")).isNotEqualTo(new Sku("AGUA"));
        var skus = new ArrayList<>(List.of(new Sku("SNACK"), new Sku("AGUA"), new Sku("GASEOSA")));

        Collections.sort(skus);

        assertThat(skus).containsExactly(new Sku("AGUA"), new Sku("GASEOSA"), new Sku("SNACK"));
    }
}
