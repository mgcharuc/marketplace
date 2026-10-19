package com.marketplace.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    @DisplayName("La clave admite de 1 a 128 caracteres ASCII visibles")
    void acceptsVisibleAsciiUpTo128() {
        assertThat(IdempotencyKey.isValid("a")).isTrue();
        assertThat(IdempotencyKey.isValid("!~" + "k".repeat(126))).isTrue();
        assertThat(IdempotencyKey.isValid("")).isFalse();
        assertThat(IdempotencyKey.isValid("k".repeat(129))).isFalse();
        assertThat(IdempotencyKey.isValid(" key")).isFalse();
        assertThat(IdempotencyKey.isValid("clave-ñ")).isFalse();
    }

    @Test
    @DisplayName("No se crea una clave con formato inválido")
    void rejectsInvalidValue() {
        assertThatThrownBy(() -> new IdempotencyKey("CO-MARCA-A", "CLI-CONTADO", "key 1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
