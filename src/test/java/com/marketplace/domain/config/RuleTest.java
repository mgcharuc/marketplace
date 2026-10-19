package com.marketplace.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleTest {

    private static final Instant FROM = Instant.parse("2026-10-16T05:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-17T05:00:00Z");

    @Test
    @DisplayName("Una regla vale desde su inicio y deja de valer en el instante de su fin")
    void appliesFromStartUntilJustBeforeEnd() {
        Rule rule = scale(FROM, TO);

        assertThat(rule.activeAt(FROM.minusSeconds(1))).isFalse();
        assertThat(rule.activeAt(FROM)).isTrue();
        assertThat(rule.activeAt(TO.minusSeconds(1))).isTrue();
        assertThat(rule.activeAt(TO)).isFalse();
    }

    @Test
    @DisplayName("Sin inicio o sin fin la vigencia queda abierta de ese lado")
    void missingEndsAreOpen() {
        assertThat(scale(null, TO).activeAt(Instant.EPOCH)).isTrue();
        assertThat(scale(FROM, null).activeAt(Instant.parse("2100-01-01T00:00:00Z"))).isTrue();
        assertThat(scale(null, null).activeAt(Instant.EPOCH)).isTrue();
    }

    private static ScaleRule scale(Instant validFrom, Instant validTo) {
        return new ScaleRule(
                "ESC-GAS350",
                "Escala Gaseosa 350",
                10,
                validFrom,
                validTo,
                Scope.sku("GASEOSA-350"),
                ScaleMode.MARGINAL,
                List.of(new Tier(10, new BigDecimal("5"))));
    }
}
