package com.marketplace.adapters.web;

import com.marketplace.application.order.ConfirmOrder;
import com.marketplace.application.order.GetOrder;
import com.marketplace.domain.order.Order;
import com.marketplace.domain.order.OrderKey;
import com.marketplace.domain.order.OrderNotFoundException;
import com.marketplace.domain.quote.QuoteKey;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/orders")
class OrderController {

    private final ConfirmOrder confirmOrder;
    private final GetOrder getOrder;
    private final RequestValidator validator;

    OrderController(ConfirmOrder confirmOrder, GetOrder getOrder, RequestValidator validator) {
        this.confirmOrder = confirmOrder;
        this.getOrder = getOrder;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> confirm(
            @RequestAttribute(RequestIdentity.ATTRIBUTE) RequestIdentity identity,
            @RequestBody OrderRequest request) {
        OrderRequest valid = validator.require(request.normalized());
        Order order =
                confirmOrder.handle(
                        new QuoteKey(identity.tenantId(), identity.customerId(), valid.quoteId()));
        return ResponseEntity.created(URI.create("/v1/orders/" + order.header().orderId()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(order.body());
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
