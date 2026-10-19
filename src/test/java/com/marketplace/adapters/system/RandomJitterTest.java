package com.marketplace.adapters.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RandomJitterTest {

    @Test
    @DisplayName("La espera adicional va de cero al 20 % del intervalo, ambos incluidos")
    void staysWithinTwentyPercent() {
        assertThat(new RandomJitter(fixed(true)).apply(Duration.ofSeconds(10)))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(new RandomJitter(fixed(false)).apply(Duration.ofSeconds(10)))
                .isEqualTo(Duration.ZERO);
        assertThat(new RandomJitter(fixed(true)).apply(Duration.ZERO)).isEqualTo(Duration.ZERO);
    }

    private static RandomGenerator fixed(boolean highest) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long nextLong(long bound) {
                return highest ? bound - 1 : 0;
            }
        };
    }
}
