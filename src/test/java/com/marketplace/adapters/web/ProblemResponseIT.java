package com.marketplace.adapters.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ProblemResponseIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Un error responde problem+json con code, correlationId y extensiones en la raíz")
    void problemCarriesCodeAndExtensionsAtRoot() throws Exception {
        mvc.perform(
                        get("/v1/quotes/q_00000000000000000000000009")
                                .header("X-Tenant-Id", "CO-MARCA-A")
                                .header("X-Customer-Id", "CLI-CONTADO")
                                .header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(
                        jsonPath("$.type")
                                .value("https://marketplace.example/problems/quote-not-found"))
                .andExpect(jsonPath("$.title").value("Quote not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"))
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @Test
    @DisplayName("Un X-Correlation-Id con caracteres no permitidos se reemplaza por uno generado")
    void replacesInvalidCorrelationId() throws Exception {
        mvc.perform(get("/v1/quotes/x").header("X-Correlation-Id", "abc def!"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    @DisplayName("Una ruta inexistente responde 404 aunque falten las cabeceras de identidad")
    void unknownRouteIsNotFound() throws Exception {
        mvc.perform(get("/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Un método no soportado responde 405 y un tipo de contenido distinto de JSON 415")
    void unsupportedMethodAndMediaType() throws Exception {
        mvc.perform(
                        put("/v1/quotes")
                                .header("X-Tenant-Id", "CO-MARCA-A")
                                .header("X-Customer-Id", "CLI-CONTADO"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(
                        post("/v1/quotes")
                                .header("X-Tenant-Id", "CO-MARCA-A")
                                .header("X-Customer-Id", "CLI-CONTADO")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("Un fallo inesperado responde 500 con un mensaje genérico, sin detalles internos")
    void unexpectedFailureHidesDetails() throws Exception {
        jdbc.update("DELETE FROM config_version WHERE tenant_id = 'CO-MARCA-B'");

        mvc.perform(
                        post("/v1/quotes")
                                .header("X-Tenant-Id", "CO-MARCA-B")
                                .header("X-Customer-Id", "CLI-B-01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"distributorId\": \"DIST-B-01\", \"paymentMethod\": \"CASH\","
                                                + " \"items\": [{\"sku\": \"GASEOSA-350\", \"quantity\": 1}]}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado."))
                .andExpect(content().string(not(containsString("CO-MARCA-B"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
