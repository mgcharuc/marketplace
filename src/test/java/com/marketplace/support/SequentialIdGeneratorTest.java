package com.marketplace.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SequentialIdGeneratorTest {

    private final SequentialIdGenerator ids = new SequentialIdGenerator();

    @Test
    @DisplayName("Genera ids consecutivos con el mismo formato que los reales")
    void generatesSequentialIdsWithRealFormat() {
        assertThat(ids.quoteId()).isEqualTo("q_00000000000000000000000001");
        assertThat(ids.quoteId()).isEqualTo("q_00000000000000000000000002");
        assertThat(ids.orderId()).isEqualTo("o_00000000000000000000000001");
        assertThat(ids.eventId()).hasToString("00000000-0000-7000-8000-000000000001");
    }

    @Test
    @DisplayName("Al reiniciarse vuelve a empezar desde uno")
    void restartsAfterReset() {
        ids.quoteId();
        ids.eventId();
        ids.reset();

        assertThat(ids.quoteId()).endsWith("01");
        assertThat(ids.eventId().version()).isEqualTo(7);
        assertThat(ids.eventId()).hasToString("00000000-0000-7000-8000-000000000002");
    }
}
