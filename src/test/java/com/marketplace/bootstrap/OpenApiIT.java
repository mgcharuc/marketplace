package com.marketplace.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class OpenApiIT {

    @Autowired MockMvc mvc;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("La confirmación documenta sus cabeceras, sus respuestas y un ejemplo de orden")
    void documentsOrderConfirmation() throws Exception {
        JsonNode confirm = docs().at("/paths/~1v1~1orders/post");

        assertThat(headers(confirm))
                .contains("X-Tenant-Id*", "X-Customer-Id*", "Idempotency-Key*", "X-Correlation-Id");
        assertThat(confirm.get("responses").propertyNames())
                .contains("201", "400", "404", "409", "422");
        assertThat(
                        confirm.at("/responses/201/content/application~1json/example/orderNumber")
                                .asString())
                .isEqualTo("CO-A-00000001");
        assertThat(
                        confirm.at("/responses/409/content/application~1problem+json/schema/$ref")
                                .asString())
                .isEqualTo("#/components/schemas/Problem");
    }

    @Test
    @DisplayName(
            "Los errores remiten a un esquema publicado con el código y los datos de cada error")
    void publishesProblemSchema() throws Exception {
        JsonNode problem = docs().at("/components/schemas/Problem/properties");

        assertThat(problem.propertyNames())
                .contains("code", "detail", "correlationId", "errors", "orderId", "eventStatus");
    }

    @Test
    @DisplayName("Solo la confirmación pide la clave de idempotencia")
    void onlyConfirmationAsksForIdempotencyKey() throws Exception {
        JsonNode paths = docs().get("paths");
        List<String> withKey = new ArrayList<>();
        for (String path : paths.propertyNames()) {
            for (String method : paths.get(path).propertyNames()) {
                if (names(paths.get(path).get(method)).contains("Idempotency-Key")) {
                    withKey.add(method + " " + path);
                }
            }
        }

        assertThat(withKey).containsExactly("post /v1/orders");
    }

    @Test
    @DisplayName("El reencolado no pide las cabeceras del tendero")
    void requeueHasNoIdentityHeaders() throws Exception {
        JsonNode retry = docs().at("/paths/~1v1~1admin~1outbox~1{eventId}~1retry/post");

        assertThat(names(retry)).doesNotContain("X-Tenant-Id", "X-Customer-Id");
        assertThat(retry.get("responses").propertyNames()).contains("202", "404", "409");
    }

    @Test
    @DisplayName("La raíz lleva a la documentación de la API")
    void rootRedirectsToSwaggerUi() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui.html"));
    }

    @Test
    @DisplayName("La interfaz de Swagger responde")
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private JsonNode docs() throws Exception {
        String body =
                mvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readTree(body);
    }

    private static List<String> names(JsonNode operation) {
        return headers(operation).stream().map(name -> name.replace("*", "")).toList();
    }

    // Una cabecera obligatoria lleva un asterisco al final.
    private static List<String> headers(JsonNode operation) {
        return StreamSupport.stream(operation.path("parameters").spliterator(), false)
                .filter(parameter -> parameter.get("in").asString().equals("header"))
                .map(
                        parameter ->
                                parameter.get("name").asString()
                                        + (parameter.path("required").asBoolean() ? "*" : ""))
                .toList();
    }
}
