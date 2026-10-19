package com.marketplace.adapters.json;

import com.marketplace.application.port.EventPayloadRenderer;
import com.marketplace.domain.order.OrderHeader;
import com.marketplace.domain.outbox.OutboxEvent;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class JsonEventPayloadRenderer implements EventPayloadRenderer {

    private static final int SCHEMA_VERSION = 1;

    private final JsonMapper mapper;

    public JsonEventPayloadRenderer() {
        this(JsonMapper.builder().build());
    }

    public JsonEventPayloadRenderer(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String erp(UUID eventId, OrderHeader header, String orderBody) {
        ObjectNode root = event(eventId, header);
        root.put("distributorId", header.quote().distributorId());
        root.put("orderId", header.orderId());
        root.put("orderNumber", header.orderNumber());
        root.set("order", mapper.readTree(orderBody));
        return mapper.writeValueAsString(root);
    }

    @Override
    public String push(UUID eventId, OrderHeader header) {
        ObjectNode root = event(eventId, header);
        root.put("customerId", header.quote().customerId());
        root.put("orderId", header.orderId());
        root.put("orderNumber", header.orderNumber());
        root.put("total", header.total().amount().toPlainString());
        root.put("currency", header.total().currency().code());
        root.put("collapseKey", header.orderId());
        return mapper.writeValueAsString(root);
    }

    private ObjectNode event(UUID eventId, OrderHeader header) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("eventId", eventId.toString());
        root.put("eventType", OutboxEvent.ORDER_CONFIRMED);
        root.put("tenantId", header.quote().tenantId());
        return root;
    }
}
