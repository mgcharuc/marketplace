package com.marketplace.adapters.json;

import com.marketplace.application.port.OrderResponseRenderer;
import com.marketplace.domain.order.OrderHeader;
import com.marketplace.domain.order.OrderStatus;
import com.marketplace.domain.quote.QuoteHeader;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class JsonOrderResponseRenderer implements OrderResponseRenderer {

    private static final List<String> SNAPSHOT_FIELDS =
            List.of(
                    "configVersion",
                    "currency",
                    "paymentMethod",
                    "lines",
                    "discounts",
                    "gifts",
                    "totals");

    private final JsonMapper mapper;

    public JsonOrderResponseRenderer() {
        this(JsonMapper.builder().build());
    }

    public JsonOrderResponseRenderer(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String render(OrderHeader header, String snapshot) {
        JsonNode quoted = mapper.readTree(snapshot);
        QuoteHeader quote = header.quote();
        ObjectNode root = mapper.createObjectNode();
        root.put("orderId", header.orderId());
        root.put("orderNumber", header.orderNumber());
        root.put("quoteId", quote.quoteId());
        root.put("tenantId", quote.tenantId());
        root.put("customerId", quote.customerId());
        root.put("distributorId", quote.distributorId());
        root.put("status", OrderStatus.CONFIRMED.name());
        root.put("confirmedAt", header.confirmedAt().toString());
        SNAPSHOT_FIELDS.forEach(field -> root.set(field, quoted.required(field)));
        if (header.availableAfter() == null) {
            root.putNull("credit");
        } else {
            root.putObject("credit")
                    .put("consumed", header.total().amount().toPlainString())
                    .put("availableAfter", header.availableAfter().amount().toPlainString());
        }
        return mapper.writeValueAsString(root);
    }
}
