package com.marketplace.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.support.IntegrationTest;
import com.marketplace.support.Resources;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class QuoteApiIT {

    private static final String COMBO_FIESTA =
            """
            {"distributorId": "DIST-NORTE", "paymentMethod": "CASH",
             "items": [{"sku": "GASEOSA-1500", "quantity": 3}, {"sku": "SNACK-PAPAS", "quantity": 3}]}
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("Cotizar responde 201 con el desglose completo y la ubicación de la cotización")
    void quotesCart() throws Exception {
        quote("CO-MARCA-A", "CLI-CONTADO", COMBO_FIESTA)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/quotes/q_00000000000000000000000001"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(Resources.read("api/quote-combo-fiesta.json"), true));
    }

    @Test
    @DisplayName("Consultar la cotización devuelve el mismo cuerpo, byte a byte, que al crearla")
    void getReturnsTheSameBytes() throws Exception {
        byte[] created =
                quote("CO-MARCA-A", "CLI-CONTADO", COMBO_FIESTA)
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        byte[] read =
                getQuote("CO-MARCA-A", "CLI-CONTADO", "q_00000000000000000000000001")
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        assertThat(read).isEqualTo(created);
    }

    @Test
    @DisplayName("Otro tendero, otro tenant o un id mal formado no encuentran la cotización")
    void quoteIsVisibleOnlyToItsCustomer() throws Exception {
        quote("CO-MARCA-A", "CLI-CONTADO", COMBO_FIESTA).andExpect(status().isCreated());
        String id = "q_00000000000000000000000001";

        getQuote("CO-MARCA-A", "CLI-CREDITO-ALTO", id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        getQuote("CO-MARCA-B", "CLI-B-01", id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        getQuote("CO-MARCA-A", "CLI-CONTADO", "q_123")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Un cambio de tasa en el país aplica a las cotizaciones nuevas de sus dos marcas")
    void countryRateChangeAppliesToNewQuotesOnly() throws Exception {
        String co = cart("DIST-NORTE", "CASH", "GASEOSA-350", 1);
        byte[] before =
                quote("CO-MARCA-A", "CLI-CONTADO", co)
                        .andExpect(jsonPath("$.lines[0].taxRate").value("0.19"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        jdbc.update(
                "UPDATE country_tax_class SET rate = '0.21'"
                        + " WHERE country_code = 'CO' AND code = 'IVA19'");

        quote("CO-MARCA-A", "CLI-CONTADO", co)
                .andExpect(jsonPath("$.lines[0].taxRate").value("0.21"))
                .andExpect(jsonPath("$.lines[0].taxAmount").value("420"))
                .andExpect(jsonPath("$.totals.total").value("2420"));
        quote("CO-MARCA-B", "CLI-B-01", cart("DIST-B-01", "CASH", "GASEOSA-350", 1))
                .andExpect(jsonPath("$.lines[0].unitPrice").value("2100"))
                .andExpect(jsonPath("$.lines[0].taxRate").value("0.21"));
        assertThat(
                        getQuote("CO-MARCA-A", "CLI-CONTADO", "q_00000000000000000000000001")
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray())
                .isEqualTo(before);
    }

    @Test
    @DisplayName("A crédito, la cotización informa el cupo del tendero con ese distribuidor")
    void creditQuoteReportsAvailableCredit() throws Exception {
        quote("CO-MARCA-A", "CLI-CREDITO-BAJO", cart("DIST-SUR", "CREDIT", "GASEOSA-1500", 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT"))
                .andExpect(jsonPath("$.credit.available").value("100000"))
                .andExpect(jsonPath("$.credit.required").value("28858"))
                .andExpect(jsonPath("$.credit.fits").value(true))
                .andExpect(jsonPath("$.credit.shortfall").value("0"));
    }

    @Test
    @DisplayName(
            "A crédito sin cuenta con ese distribuidor se rechaza, aunque haya cuenta con otro")
    void creditWithoutAccountIsRejected() throws Exception {
        quote("CO-MARCA-A", "CLI-CONTADO", cart("DIST-NORTE", "CREDIT", "GASEOSA-350", 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CREDIT_ACCOUNT_NOT_FOUND"));
        quote("CO-MARCA-A", "CLI-CREDITO-ALTO", cart("DIST-SUR", "CREDIT", "GASEOSA-350", 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CREDIT_ACCOUNT_NOT_FOUND"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM quote", Long.class)).isZero();
    }

    @Test
    @DisplayName(
            "La cotización se guarda con su total y vence según el tiempo de vigencia del tenant")
    void storesQuoteColumns() throws Exception {
        quote("EC-MARCA-A", "CLI-EC-01", cart("DIST-EC-01", "CASH", "AGUA-500", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totals.total").value("0.40"));

        assertThat(
                        jdbc.queryForMap(
                                "SELECT tenant_id, customer_id, distributor_id, config_version,"
                                        + " payment_method, total::text AS total,"
                                        + " extract(epoch FROM expires_at - created_at)::int AS ttl FROM quote"))
                .containsEntry("tenant_id", "EC-MARCA-A")
                .containsEntry("customer_id", "CLI-EC-01")
                .containsEntry("distributor_id", "DIST-EC-01")
                .containsEntry("config_version", 1)
                .containsEntry("payment_method", "CASH")
                .containsEntry("total", "0.4000")
                .containsEntry("ttl", 900);
    }

    private ResultActions quote(String tenantId, String customerId, String body) throws Exception {
        return mvc.perform(
                post("/v1/quotes")
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Customer-Id", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions getQuote(String tenantId, String customerId, String quoteId)
            throws Exception {
        return mvc.perform(
                get("/v1/quotes/{quoteId}", quoteId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Customer-Id", customerId));
    }

    private String cart(String distributorId, String paymentMethod, String sku, int quantity) {
        var root = mapper.createObjectNode();
        root.put("distributorId", distributorId);
        root.put("paymentMethod", paymentMethod);
        root.putArray("items").addObject().put("sku", sku).put("quantity", quantity);
        return mapper.writeValueAsString(root);
    }
}
