package com.marketplace.adapters.web;

import com.marketplace.domain.order.OrderSummary;

record OrderSummaryResponse(
        String orderId,
        String orderNumber,
        String distributorId,
        String confirmedAt,
        String total,
        String status) {

    static OrderSummaryResponse of(OrderSummary order) {
        return new OrderSummaryResponse(
                order.orderId(),
                order.orderNumber(),
                order.distributorId(),
                order.confirmedAt().toString(),
                order.total().amount().toPlainString(),
                order.status().name());
    }
}
