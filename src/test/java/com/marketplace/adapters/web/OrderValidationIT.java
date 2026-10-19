package com.marketplace.adapters.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class OrderValidationIT {

    private static final String UNKNOWN_QUOTE = "{\"quoteId\": \"q_00000000000000000000000099\"}";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName(
            "Sin Idempotency-Key, vacía, con espacios o de más de 128 caracteres se responde 400")
    void rejectsMissingOrInvalidIdempotencyKey() throws Exception {
        mvc.perform(withIdentity(post("/v1/orders")).content(UNKNOWN_QUOTE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"))
                .andExpect(jsonPath("$.errors").doesNotExist());

        for (String key : new String[] {"", "   ", " key-1", "key 1", "k".repeat(129), "clave-ñ"}) {
            confirm(key, UNKNOWN_QUOTE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        }
    }

    @Test
    @DisplayName("Una Idempotency-Key que llega dos veces responde 400")
    void rejectsRepeatedIdempotencyKey() throws Exception {
        mvc.perform(
                        withIdentity(post("/v1/orders"))
                                .header("Idempotency-Key", "key-1", "key-2")
                                .content(UNKNOWN_QUOTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("Una Idempotency-Key de 128 caracteres visibles se acepta")
    void acceptsLongestValidKey() throws Exception {
        confirm("!~" + "k".repeat(126), UNKNOWN_QUOTE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
    }

    @Test
    @DisplayName(
            "Las cabeceras de identidad se validan antes que la clave y la clave antes que el cuerpo")
    void validatesIdentityThenKeyThenBody() throws Exception {
        mvc.perform(
                        post("/v1/orders")
                                .header("X-Customer-Id", "CLI-CONTADO")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_HEADERS_REQUIRED"));
        mvc.perform(withIdentity(post("/v1/orders")).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("Sin quoteId o con un formato inválido se responde 400 con el campo")
    void rejectsMissingOrMalformedQuoteId() throws Exception {
        for (String body : new String[] {"{}", "{\"quoteId\": null}", "{\"quoteId\": \"  \"}"}) {
            confirm("key-1", body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("quoteId"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        }
        confirm("key-1", "{\"quoteId\": \"q_123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quoteId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    @DisplayName("El quoteId se acepta con espacios alrededor")
    void trimsQuoteId() throws Exception {
        confirm("key-1", "{\"quoteId\": \" q_00000000000000000000000099 \"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("JSON mal formado, un campo desconocido o un tipo incorrecto responden 400")
    void rejectsMalformedBodies() throws Exception {
        confirm("key-1", "{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MALFORMED_JSON"));
        confirm("key-1", "{\"quoteId\": \"q_00000000000000000000000099\", \"total\": \"1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("total"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
        confirm("key-1", "{\"quoteId\": 123}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quoteId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TYPE"));
    }

    @Test
    @DisplayName("Un tenant o un tendero inexistente responde 404 antes de buscar la cotización")
    void unknownIdentityIsNotFound() throws Exception {
        mvc.perform(
                        post("/v1/orders")
                                .header("X-Tenant-Id", "XX-MARCA")
                                .header("X-Customer-Id", "CLI-CONTADO")
                                .header("Idempotency-Key", "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(UNKNOWN_QUOTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
        mvc.perform(
                        post("/v1/orders")
                                .header("X-Tenant-Id", "CO-MARCA-A")
                                .header("X-Customer-Id", "CLI-NADIE")
                                .header("Idempotency-Key", "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(UNKNOWN_QUOTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Un quoteId mal formado responde 400 aunque el tenant no exista")
    void formatIsCheckedBeforeIdentityExists() throws Exception {
        mvc.perform(
                        post("/v1/orders")
                                .header("X-Tenant-Id", "XX-MARCA")
                                .header("X-Customer-Id", "CLI-CONTADO")
                                .header("Idempotency-Key", "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quoteId\": \"q_123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private ResultActions confirm(String key, String body) throws Exception {
        return mvc.perform(
                withIdentity(post("/v1/orders")).header("Idempotency-Key", key).content(body));
    }

    private static MockHttpServletRequestBuilder withIdentity(
            MockHttpServletRequestBuilder request) {
        return request.header("X-Tenant-Id", "CO-MARCA-A")
                .header("X-Customer-Id", "CLI-CONTADO")
                .contentType(MediaType.APPLICATION_JSON);
    }
}
