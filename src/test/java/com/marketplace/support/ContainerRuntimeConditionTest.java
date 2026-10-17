package com.marketplace.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContainerRuntimeConditionTest {

    private final List<String> warnings = new ArrayList<>();

    @Test
    @DisplayName("Con motor de contenedores, las pruebas de integración se ejecutan")
    void enablesWhenRuntimeIsAvailable() {
        var condition = new ContainerRuntimeCondition(() -> true, () -> true, warnings::add);

        assertThat(condition.evaluateExecutionCondition(null).isDisabled()).isFalse();
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("Sin motor de contenedores, se omiten con un aviso que indica la causa")
    void disablesWithWarningWhenRuntimeIsMissing() {
        var condition = new ContainerRuntimeCondition(() -> false, () -> false, warnings::add);

        var result = condition.evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(warnings).singleElement().asString().contains("DOCKER_HOST");
    }

    @Test
    @DisplayName("Con postgres.required=true y sin motor de contenedores, la ejecución falla")
    void failsWhenRuntimeIsMissingAndRequired() {
        var condition = new ContainerRuntimeCondition(() -> false, () -> true, warnings::add);

        assertThatThrownBy(() -> condition.evaluateExecutionCondition(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postgres.required");
    }
}
