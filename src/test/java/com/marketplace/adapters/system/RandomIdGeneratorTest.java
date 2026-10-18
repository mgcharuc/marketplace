package com.marketplace.adapters.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RandomIdGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00.123Z");
    private static final String CROCKFORD = "[0-9A-HJKMNP-TV-Z]";

    private final RandomIdGenerator ids =
            new RandomIdGenerator(Clock.fixed(NOW, ZoneOffset.UTC), new Random(7));

    @Test
    @DisplayName("El id de la cotización es q_ seguido de un ULID en base32 de Crockford")
    void quoteIdIsPrefixedUlid() {
        assertThat(ids.quoteId()).matches("q_" + CROCKFORD + "{26}");
    }

    @Test
    @DisplayName("El id de la orden es o_ seguido de un ULID en base32 de Crockford")
    void orderIdIsPrefixedUlid() {
        assertThat(ids.orderId()).matches("o_" + CROCKFORD + "{26}");
    }

    @Test
    @DisplayName("Los primeros diez caracteres del ULID son los milisegundos del instante")
    void ulidStartsWithTimestamp() {
        String ulid = ids.quoteId().substring(2);

        assertThat(decodeCrockford(ulid.substring(0, 10))).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    @DisplayName("Los ULID ordenan por instante de creación")
    void ulidsSortByTime() {
        var later =
                new RandomIdGenerator(
                        Clock.fixed(NOW.plusMillis(1), ZoneOffset.UTC), new Random(1));

        assertThat(ids.orderId()).isLessThan(later.orderId());
    }

    @Test
    @DisplayName("El id del evento es un UUID versión 7 con los milisegundos del instante")
    void eventIdIsUuidV7() {
        var uuid = ids.eventId();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
        assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    @DisplayName("Ids generados en el mismo milisegundo no se repiten")
    void idsAreUnique() {
        var seen = new HashSet<String>();
        IntStream.range(0, 10_000)
                .forEach(
                        i -> {
                            seen.add(ids.quoteId());
                            seen.add(ids.eventId().toString());
                        });

        assertThat(seen).hasSize(20_000);
    }

    private static long decodeCrockford(String text) {
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        long value = 0;
        for (char c : text.toCharArray()) {
            value = value * 32 + alphabet.indexOf(c);
        }
        return value;
    }
}
