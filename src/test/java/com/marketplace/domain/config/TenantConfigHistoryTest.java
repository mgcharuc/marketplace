package com.marketplace.domain.config;

import static com.marketplace.domain.config.ConfigFixtures.CO_MARCA_A;
import static com.marketplace.domain.config.ConfigFixtures.PIPELINE;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaA;
import static com.marketplace.domain.config.ConfigFixtures.coMarcaAProducts;
import static com.marketplace.domain.config.ConfigFixtures.combo;
import static com.marketplace.domain.config.ConfigFixtures.component;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantConfigHistoryTest {

    @Test
    @DisplayName("La primera versión de un tenant es la 1 y cada publicación avanza la vigente")
    void publishesConsecutiveVersions() {
        var history = new TenantConfigHistory(CO_MARCA_A, 0);

        history.publish(coMarcaA(1));
        history.publish(coMarcaA(2));

        assertThat(history.currentNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Rechaza publicar un número de versión ya usado")
    void rejectsUsedNumber() {
        var history = new TenantConfigHistory(CO_MARCA_A, 2);

        assertThatThrownBy(() -> history.publish(coMarcaA(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.publish(coMarcaA(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(history.currentNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Rechaza publicar un número que salta versiones")
    void rejectsNonConsecutiveNumber() {
        var history = new TenantConfigHistory(CO_MARCA_A, 1);

        assertThatThrownBy(() -> history.publish(coMarcaA(3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(history.currentNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Una versión inválida no se publica, informa sus violaciones y la vigente no cambia")
    void rejectsInvalidVersionWithViolations() {
        var history = new TenantConfigHistory(CO_MARCA_A, 1);
        var invalid =
                new ConfigVersion(
                        "CO-MARCA-A",
                        2,
                        PIPELINE,
                        coMarcaAProducts(),
                        List.of(combo("CMB-FIESTA", 10, "0", component("GASEOSA-1500", 2))));

        assertThatThrownBy(() -> history.publish(invalid))
                .isInstanceOfSatisfying(
                        ConfigInvalidException.class,
                        error ->
                                assertThat(error.violations())
                                        .extracting(ConfigViolation::code)
                                        .containsExactly("COMBO_PRICE_NOT_BELOW_LIST"));
        assertThat(history.currentNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Rechaza publicar una versión de otro tenant")
    void rejectsVersionOfAnotherTenant() {
        var history = new TenantConfigHistory(CO_MARCA_A, 0);
        var otherTenant =
                new ConfigVersion("CO-MARCA-B", 1, PIPELINE, coMarcaAProducts(), List.of());

        assertThatThrownBy(() -> history.publish(otherTenant))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza un historial con versión vigente negativa")
    void rejectsNegativeCurrentNumber() {
        assertThatThrownBy(() -> new TenantConfigHistory(CO_MARCA_A, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
