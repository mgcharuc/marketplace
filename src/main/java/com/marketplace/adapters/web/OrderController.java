package com.marketplace.adapters.web;

import com.marketplace.application.order.ConfirmOrder;
import com.marketplace.application.order.ConfirmOrderCommand;
import com.marketplace.application.order.GetOrder;
import com.marketplace.application.order.GetOrderHistory;
import com.marketplace.application.order.OrderConfirmation;
import com.marketplace.domain.order.OrderKey;
import com.marketplace.domain.order.OrderNotFoundException;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/orders")
class OrderController {

    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final ConfirmOrder confirmOrder;
    private final GetOrder getOrder;
    private final GetOrderHistory getOrderHistory;
    private final RequestValidator validator;

    OrderController(
            ConfirmOrder confirmOrder,
            GetOrder getOrder,
            GetOrderHistory getOrderHistory,
            RequestValidator validator) {
        this.confirmOrder = confirmOrder;
        this.getOrder = getOrder;
        this.getOrderHistory = getOrderHistory;
        this.validator = validator;
    }

    @GetMapping
    OrderHistoryResponse history(
            @RequestAttribute(RequestIdentity.ATTRIBUTE) RequestIdentity identity,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        HistoryRequest valid = validator.require(HistoryRequest.parse(page, size));
        var result =
                getOrderHistory.handle(identity.tenantId(), identity.customerId(), valid.toQuery());
        return OrderHistoryResponse.of(result.map(OrderSummaryResponse::of));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> confirm(
            @RequestAttribute(RequestIdentity.ATTRIBUTE) RequestIdentity identity,
            @RequestAttribute(IdempotencyKeyInterceptor.ATTRIBUTE) String idempotencyKey,
            @RequestBody OrderRequest request) {
        OrderRequest valid = validator.require(request.normalized());
        OrderConfirmation confirmation =
                confirmOrder.handle(
                        new ConfirmOrderCommand(
                                identity.tenantId(),
                                identity.customerId(),
                                valid.quoteId(),
                                idempotencyKey,
                                RequestHash.of(valid)));
        var response =
                ResponseEntity.created(URI.create("/v1/orders/" + confirmation.orderId()))
                        .contentType(MediaType.APPLICATION_JSON);
        if (confirmation.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(confirmation.body());
    }

    @GetMapping("/{orderId}")
    ResponseEntity<String> get(
            @RequestAttribute(RequestIdentity.ATTRIBUTE) RequestIdentity identity,
            @PathVariable String orderId) {
        String id = orderId.trim();
        if (!IdFormat.isOrderId(id)) {
            throw new OrderNotFoundException();
        }
        String body = getOrder.handle(new OrderKey(identity.tenantId(), identity.customerId(), id));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
