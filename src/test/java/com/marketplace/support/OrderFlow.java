package com.marketplace.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

public class OrderFlow {

    private final MockMvc mvc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public OrderFlow(MockMvc mvc) {
        this.mvc = mvc;
    }

    public ResultActions quote(
            String tenantId,
            String customerId,
            String distributorId,
            String method,
            String sku,
            int quantity)
            throws Exception {
        var root = mapper.createObjectNode();
        root.put("distributorId", distributorId);
        root.put("paymentMethod", method);
        root.putArray("items").addObject().put("sku", sku).put("quantity", quantity);
        return mvc.perform(
                        post("/v1/quotes")
                                .header("X-Tenant-Id", tenantId)
                                .header("X-Customer-Id", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(root)))
                .andExpect(status().isCreated());
    }

    public String quoteId(ResultActions quote) throws Exception {
        String body = quote.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body).get("quoteId").asString();
    }

    public ResultActions confirm(String tenantId, String customerId, String key, String quoteId)
            throws Exception {
        return confirmBody(tenantId, customerId, key, "{\"quoteId\": \"" + quoteId + "\"}");
    }

    public ResultActions confirmBody(String tenantId, String customerId, String key, String body)
            throws Exception {
        return mvc.perform(
                post("/v1/orders")
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Customer-Id", customerId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }
}
