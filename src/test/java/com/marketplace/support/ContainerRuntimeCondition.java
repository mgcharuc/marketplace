package com.marketplace.support;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

public final class ContainerRuntimeCondition implements ExecutionCondition {

    static final String REQUIRED_PROPERTY = "postgres.required";

    private static final Logger log = LoggerFactory.getLogger(ContainerRuntimeCondition.class);
    private static final String MISSING_RUNTIME =
            "No hay Docker ni Podman disponible. Revise DOCKER_HOST, docker.host en"
                    + " ~/.testcontainers.properties o el socket";

    private final BooleanSupplier runtimeAvailable;
    private final BooleanSupplier required;
    private final Consumer<String> warning;

    public ContainerRuntimeCondition() {
        this(
                () -> DockerClientFactory.instance().isDockerAvailable(),
                () -> Boolean.getBoolean(REQUIRED_PROPERTY),
                log::warn);
    }

    ContainerRuntimeCondition(
            BooleanSupplier runtimeAvailable, BooleanSupplier required, Consumer<String> warning) {
        this.runtimeAvailable = runtimeAvailable;
        this.required = required;
        this.warning = warning;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (runtimeAvailable.getAsBoolean()) {
            return ConditionEvaluationResult.enabled("Motor de contenedores disponible");
        }
        if (required.getAsBoolean()) {
            throw new IllegalStateException(MISSING_RUNTIME + " (" + REQUIRED_PROPERTY + "=true)");
        }
        warning.accept("Se omiten las pruebas de integración. " + MISSING_RUNTIME);
        return ConditionEvaluationResult.disabled(MISSING_RUNTIME);
    }
}
