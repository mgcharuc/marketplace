package com.marketplace.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    @DisplayName("La página empieza en 0 y el tamaño va de 1 a 100")
    void acceptsOnlyTheAllowedRange() {
        assertThat(new PageQuery(0, 1).size()).isOne();
        assertThat(new PageQuery(0, 100).size()).isEqualTo(100);
        assertThatThrownBy(() -> new PageQuery(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
