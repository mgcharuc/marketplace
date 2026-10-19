package com.marketplace.adapters.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import com.marketplace.support.OrderFlow;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class OrderHistoryApiIT {

    @Autowired MockMvc mvc;
    @Autowired FixedTimeSource time;

    private OrderFlow flow;

    @BeforeEach
    void createFlow() {
        flow = new OrderFlow(mvc);
    }

    @Test
    @DisplayName("La orden aparece en el historial apenas se confirma")
    void confirmedOrderIsListedRightAway() throws Exception {
        String orderNumber = confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", 25, "key-1");

        history("CO-MARCA-A", "CLI-CONTADO", "")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        """
                        {"items": [{"orderId": "o_00000000000000000000000001",
                                    "orderNumber": "%s", "distributorId": "DIST-NORTE",
                                    "confirmedAt": "2026-10-16T15:00:00Z", "total": "56882",
                                    "status": "CONFIRMED"}],
                         "page": 0, "size": 20, "totalItems": 1}
                        """
                                                .formatted(orderNumber),
                                        true));
    }

    @Test
    @DisplayName("Por API las órdenes salen por fecha de confirmación y no por orden de llegada")
    void listsByConfirmationTime() throws Exception {
        String latest = quote("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", 1);
        String earlier = quote("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", 2);
        String sameInstant = quote("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", 3);
        time.set(FixedTimeSource.START.plus(Duration.ofMinutes(5)));
        flow.confirm("CO-MARCA-A", "CLI-CONTADO", "key-1", latest).andExpect(status().isCreated());
        time.set(FixedTimeSource.START.plus(Duration.ofMinutes(1)));
        flow.confirm("CO-MARCA-A", "CLI-CONTADO", "key-2", earlier).andExpect(status().isCreated());
        flow.confirm("CO-MARCA-A", "CLI-CONTADO", "key-3", sameInstant)
                .andExpect(status().isCreated());

        history("CO-MARCA-A", "CLI-CONTADO", "")
                .andExpect(
                        jsonPath("$.items[*].orderId")
                                .value(
                                        contains(
                                                "o_00000000000000000000000001",
                                                "o_00000000000000000000000003",
                                                "o_00000000000000000000000002")));
    }

    @Test
    @DisplayName("El historial se pagina con page y size y cuenta todas las órdenes")
    void pagesHistory() throws Exception {
        for (int n = 1; n <= 3; n++) {
            confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", n, "key-" + n);
            time.advance(Duration.ofSeconds(1));
        }

        history("CO-MARCA-A", "CLI-CONTADO", "?page=0&size=2")
                .andExpect(
                        jsonPath("$.items[*].orderId")
                                .value(
                                        contains(
                                                "o_00000000000000000000000003",
                                                "o_00000000000000000000000002")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(3));
        history("CO-MARCA-A", "CLI-CONTADO", "?page=1&size=2")
                .andExpect(
                        jsonPath("$.items[*].orderId")
                                .value(contains("o_00000000000000000000000001")))
                .andExpect(jsonPath("$.totalItems").value(3));
        history("CO-MARCA-A", "CLI-CONTADO", "?page=2&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("Cada tendero ve solo sus órdenes y solo dentro de su tenant")
    void historyIsScopedToTenantAndCustomer() throws Exception {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", 1, "key-1");
        String inB =
                flow.quoteId(
                        flow.quote(
                                "CO-MARCA-B", "CLI-B-01", "DIST-B-01", "CASH", "GASEOSA-350", 1));
        flow.confirm("CO-MARCA-B", "CLI-B-01", "key-1", inB).andExpect(status().isCreated());

        history("CO-MARCA-A", "CLI-CREDITO-ALTO", "")
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0));
        history("CO-MARCA-B", "CLI-B-01", "")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].distributorId").value("DIST-B-01"));
        history("CO-MARCA-A", "CLI-CONTADO", "")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].distributorId").value("DIST-NORTE"));
    }

    @Test
    @DisplayName("En USD el total del historial sale con dos decimales")
    void usdTotalsHaveTwoDecimals() throws Exception {
        String quoteId =
                flow.quoteId(
                        flow.quote("EC-MARCA-A", "CLI-EC-01", "DIST-EC-01", "CASH", "AGUA-500", 1));
        flow.confirm("EC-MARCA-A", "CLI-EC-01", "key-1", quoteId).andExpect(status().isCreated());

        history("EC-MARCA-A", "CLI-EC-01", "")
                .andExpect(jsonPath("$.items[0].total").value("0.40"));
    }

    @Test
    @DisplayName(
            "Una página negativa o un tamaño fuera de 1 a 100 responden 400 con un error por campo")
    void rejectsOutOfRangePaging() throws Exception {
        history("CO-MARCA-A", "CLI-CONTADO", "?page=-1&size=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[*].field").value(contains("page", "size")))
                .andExpect(
                        jsonPath("$.errors[*].code")
                                .value(contains("OUT_OF_RANGE", "OUT_OF_RANGE")));
        history("CO-MARCA-A", "CLI-CONTADO", "?size=101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        history("CO-MARCA-A", "CLI-CONTADO", "?size=100").andExpect(status().isOk());
        history("CO-MARCA-A", "CLI-CONTADO", "?page=3000000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("Una página válida pero muy lejana responde 200 vacía con el total")
    void farPageIsEmpty() throws Exception {
        confirm("CO-MARCA-A", "CLI-CONTADO", "DIST-NORTE", 1, "key-1");

        history("CO-MARCA-A", "CLI-CONTADO", "?page=21474837&size=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("Una página o un tamaño vacíos o con solo espacios toman el valor por defecto")
    void blankPagingUsesDefaults() throws Exception {
        mvc.perform(withIdentity(get("/v1/orders")).param("page", "").param("size", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(withIdentity(get("/v1/orders")).param("page", " 1 ").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    @DisplayName("Una página o un tamaño que no son enteros responden 400 con INVALID_TYPE")
    void rejectsNonIntegerPaging() throws Exception {
        history("CO-MARCA-A", "CLI-CONTADO", "?page=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TYPE"));
        history("CO-MARCA-A", "CLI-CONTADO", "?size=1.5")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TYPE"));
        history("CO-MARCA-A", "CLI-CONTADO", "?page=abc&size=0x10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("page", "size")))
                .andExpect(
                        jsonPath("$.errors[*].code")
                                .value(contains("INVALID_TYPE", "INVALID_TYPE")));
    }

    @Test
    @DisplayName("Sin cabeceras de identidad responde 400, y un tenant o tendero inexistente 404")
    void validatesIdentity() throws Exception {
        mvc.perform(get("/v1/orders?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_HEADERS_REQUIRED"));
        history("XX-MARCA", "CLI-CONTADO", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
        history("CO-MARCA-A", "CLI-NADIE", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("La paginación inválida responde 400 antes de buscar el tenant")
    void pagingIsCheckedBeforeIdentityExists() throws Exception {
        history("XX-MARCA", "CLI-CONTADO", "?page=-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private String confirm(
            String tenantId, String customerId, String distributorId, int quantity, String key)
            throws Exception {
        String quoteId = quote(tenantId, customerId, distributorId, quantity);
        String body =
                flow.confirm(tenantId, customerId, key, quoteId)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonMapper.builder().build().readTree(body).get("orderNumber").asString();
    }

    private String quote(String tenantId, String customerId, String distributorId, int quantity)
            throws Exception {
        return flow.quoteId(
                flow.quote(tenantId, customerId, distributorId, "CASH", "GASEOSA-350", quantity));
    }

    private ResultActions history(String tenantId, String customerId, String query)
            throws Exception {
        return mvc.perform(
                get("/v1/orders" + query)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Customer-Id", customerId));
    }

    private static MockHttpServletRequestBuilder withIdentity(
            MockHttpServletRequestBuilder request) {
        return request.header("X-Tenant-Id", "CO-MARCA-A").header("X-Customer-Id", "CLI-CONTADO");
    }
}
