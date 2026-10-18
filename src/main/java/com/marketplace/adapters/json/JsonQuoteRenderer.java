package com.marketplace.adapters.json;

import com.marketplace.application.port.QuoteRenderer;
import com.marketplace.domain.quote.QuoteHeader;
import com.marketplace.pricing.Breakdown;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class JsonQuoteRenderer implements QuoteRenderer {

    private static final List<String> BREAKDOWN_FIELDS =
            List.of("lines", "discounts", "gifts", "totals", "credit");

    private final JsonMapper mapper;
    private final BreakdownJson breakdownJson;

    public JsonQuoteRenderer() {
        this(JsonMapper.builder().build());
    }

    public JsonQuoteRenderer(JsonMapper mapper) {
        this.mapper = mapper;
        this.breakdownJson = new BreakdownJson(mapper);
    }

    @Override
    public String render(QuoteHeader header, Breakdown breakdown) {
        ObjectNode written = breakdownJson.write(breakdown);
        ObjectNode root = mapper.createObjectNode();
        root.put("quoteId", header.quoteId());
        root.put("tenantId", header.tenantId());
        root.put("customerId", header.customerId());
        root.put("distributorId", header.distributorId());
        root.set("configVersion", written.get("configVersion"));
        root.set("currency", written.get("currency"));
        root.put("paymentMethod", header.paymentMethod().name());
        root.put("createdAt", header.createdAt().toString());
        root.put("expiresAt", header.expiresAt().toString());
        BREAKDOWN_FIELDS.forEach(field -> root.set(field, written.get(field)));
        return mapper.writeValueAsString(root);
    }
}
