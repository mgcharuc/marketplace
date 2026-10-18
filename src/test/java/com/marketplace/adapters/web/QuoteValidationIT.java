package com.marketplace.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class QuoteValidationIT {

    private static final String VALID =
            """
            {"distributorId": "DIST-NORTE", "paymentMethod": "CASH",
             "items": [{"sku": "GASEOSA-350", "quantity": 1}]}
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName(
            "Sin cabeceras de identidad o con formato inválido se responde 400 por cada cabecera")
    void rejectsMissingOrInvalidIdentityHeaders() throws Exception {
        mvc.perform(post("/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDENTITY_HEADERS_REQUIRED"))
                .andExpect(
                        jsonPath("$.errors")
                                .value(
                                        contains(
                                                error(null, "X-Customer-Id", null, "REQUIRED"),
                                                error(null, "X-Tenant-Id", null, "REQUIRED"))));

        quote("CO MARCA A", "  ", VALID)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("X-Customer-Id"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[1].field").value("X-Tenant-Id"))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_FORMAT"));
    }

    @Test
    @DisplayName("Las cabeceras de identidad se validan antes que el cuerpo")
    void validatesHeadersBeforeBody() throws Exception {
        mvc.perform(
                        post("/v1/quotes")
                                .header("X-Customer-Id", "CLI-CONTADO")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_HEADERS_REQUIRED"));
    }

    @Test
    @DisplayName("Cabeceras, distribuidor y SKU se aceptan con espacios alrededor")
    void trimsIdentifiers() throws Exception {
        String body =
                VALID.replace("\"DIST-NORTE\"", "\" DIST-NORTE \"")
                        .replace("\"GASEOSA-350\"", "\" GASEOSA-350\"");

        quote(" CO-MARCA-A ", " CLI-CONTADO", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.distributorId").value("DIST-NORTE"))
                .andExpect(jsonPath("$.lines[0].sku").value("GASEOSA-350"));
    }

    @Test
    @DisplayName("El método de pago no se recorta ni cambia de mayúsculas")
    void paymentMethodIsTakenAsReceived() throws Exception {
        quote("CO-MARCA-A", "CLI-CONTADO", VALID.replace("\"CASH\"", "\" CASH \""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(
                                        error(
                                                null,
                                                "paymentMethod",
                                                null,
                                                "INVALID_PAYMENT_METHOD")));
    }

    @Test
    @DisplayName("Un campo repetido en el cuerpo se rechaza como JSON mal formado")
    void rejectsDuplicateFields() throws Exception {
        quote(
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        VALID.replace(
                                "\"paymentMethod\": \"CASH\"",
                                "\"paymentMethod\": \"CREDIT\", \"paymentMethod\": \"CASH\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MALFORMED_JSON"));
    }

    @Test
    @DisplayName("Un JSON mal formado o un cuerpo vacío se rechazan con 400")
    void rejectsMalformedJson() throws Exception {
        quote("CO-MARCA-A", "CLI-CONTADO", "{\"distributorId\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.errors[0].field").doesNotExist());
        quote("CO-MARCA-A", "CLI-CONTADO", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MALFORMED_JSON"));
    }

    @Test
    @DisplayName("Un campo desconocido se rechaza con su ruta y la posición del ítem")
    void rejectsUnknownFields() throws Exception {
        quote(
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        VALID.replace("\"paymentMethod\"", "\"discount\": 5, \"paymentMethod\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(error(null, "discount", null, "UNKNOWN_FIELD")));
        quote(
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        VALID.replace("\"quantity\": 1", "\"quantity\": 1, \"price\": \"1\""))
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(error(0, "items.price", null, "UNKNOWN_FIELD")));
    }

    @Test
    @DisplayName("Un tipo incorrecto se rechaza sin convertirlo")
    void rejectsWrongTypes() throws Exception {
        quote("CO-MARCA-A", "CLI-CONTADO", VALID.replace("\"quantity\": 1", "\"quantity\": \"3\""))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(error(0, "items.quantity", null, "INVALID_TYPE")));
        quote("CO-MARCA-A", "CLI-CONTADO", VALID.replace("\"quantity\": 1", "\"quantity\": 1.5"))
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(error(0, "items.quantity", null, "INVALID_TYPE")));
        quote("CO-MARCA-A", "CLI-CONTADO", VALID.replace("\"DIST-NORTE\"", "123"))
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(error(null, "distributorId", null, "INVALID_TYPE")));
        quote("CO-MARCA-A", "CLI-CONTADO", VALID.replace("\"GASEOSA-350\"", "true"))
                .andExpect(
                        jsonPath("$.errors[0]").value(error(0, "items.sku", null, "INVALID_TYPE")));
        quote(
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        VALID.replace("\"quantity\": 1", "\"quantity\": 99999999999"))
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(error(0, "items.quantity", null, "INVALID_TYPE")));
        quote("CO-MARCA-A", "CLI-CONTADO", "{\"items\": [{\"sku\": }]}")
                .andExpect(jsonPath("$.errors[0].code").value("MALFORMED_JSON"));
    }

    @Test
    @DisplayName(
            "Distribuidor y método de pago son obligatorios y el distribuidor debe tener formato válido")
    void rejectsMissingTopLevelFields() throws Exception {
        quote("CO-MARCA-A", "CLI-CONTADO", "{\"distributorId\": \"  \", \"items\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(
                        jsonPath("$.errors")
                                .value(
                                        contains(
                                                error(null, "distributorId", null, "REQUIRED"),
                                                error(null, "paymentMethod", null, "REQUIRED"))));
        quote("CO-MARCA-A", "CLI-CONTADO", VALID.replace("DIST-NORTE", "DIST NORTE"))
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(error(null, "distributorId", null, "INVALID_FORMAT")));
    }

    @Test
    @DisplayName("Un tenant o un tendero inexistentes responden 404")
    void rejectsUnknownIdentity() throws Exception {
        quote("CO-MARCA-Z", "CLI-CONTADO", VALID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
        quote("CO-MARCA-A", "CLI-B-01", VALID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Un carrito con errores responde 422 con todos los errores y sus posiciones")
    void rejectsInvalidCart() throws Exception {
        String body =
                """
                {"distributorId": "DIST-NORTE", "paymentMethod": "CASH",
                 "items": [{"sku": "GASEOSA-350", "quantity": 0}, {"sku": "NO-EXISTE", "quantity": 1},
                           {"sku": "JUGO-1L", "quantity": 10000}, null, {"quantity": 2}]}
                """;

        quote("CO-MARCA-A", "CLI-CONTADO", body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CART"))
                .andExpect(
                        jsonPath("$.errors")
                                .value(
                                        contains(
                                                error(
                                                        0,
                                                        "quantity",
                                                        "GASEOSA-350",
                                                        "INVALID_QUANTITY"),
                                                error(1, "sku", "NO-EXISTE", "UNKNOWN_SKU"),
                                                error(2, "quantity", "JUGO-1L", "INVALID_QUANTITY"),
                                                error(3, null, null, "INVALID_ITEM"),
                                                error(4, "sku", null, "MISSING_SKU"))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quote", Long.class)).isZero();
    }

    @Test
    @DisplayName(
            "Un carrito vacío, uno de más de 200 ítems o un método de pago desconocido responden 422")
    void rejectsCartShape() throws Exception {
        quote(
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        "{\"distributorId\": \"DIST-NORTE\", \"paymentMethod\": \"CASH\", \"items\": []}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0]").value(error(null, "items", null, "EMPTY_CART")));
        quote("CO-MARCA-A", "CLI-CONTADO", VALID.replace("CASH", "TRANSFER"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(
                                        error(
                                                null,
                                                "paymentMethod",
                                                null,
                                                "INVALID_PAYMENT_METHOD")));
        String items =
                IntStream.range(0, 201)
                        .mapToObj(i -> "{\"sku\": \"GASEOSA-350\", \"quantity\": 1}")
                        .collect(Collectors.joining(","));
        quote(
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        "{\"distributorId\": \"DIST-NORTE\", \"paymentMethod\": \"CASH\", \"items\": ["
                                + items
                                + "]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(
                        jsonPath("$.errors[0]")
                                .value(error(null, "items", null, "TOO_MANY_ITEMS")));
    }

    @Test
    @DisplayName(
            "Un distribuidor inexistente o de otro tenant responde 422 después de validar el carrito")
    void rejectsUnknownDistributor() throws Exception {
        quote("CO-MARCA-A", "CLI-CONTADO", VALID.replace("DIST-NORTE", "DIST-X"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DISTRIBUTOR_NOT_FOUND"));
        quote("CO-MARCA-A", "CLI-CONTADO", VALID.replace("DIST-NORTE", "DIST-B-01"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DISTRIBUTOR_NOT_FOUND"));
        quote(
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        VALID.replace("DIST-NORTE", "DIST-B-01").replace("GASEOSA-350", "X"))
                .andExpect(jsonPath("$.code").value("INVALID_CART"));
    }

    private ResultActions quote(String tenantId, String customerId, String body) throws Exception {
        MockHttpServletRequestBuilder request =
                post("/v1/quotes")
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Customer-Id", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8));
        return mvc.perform(request);
    }

    private static Map<String, Object> error(Integer index, String field, String sku, String code) {
        var error = new LinkedHashMap<String, Object>();
        error.put("index", index);
        error.put("field", field);
        error.put("sku", sku);
        error.put("code", code);
        return error;
    }
}
