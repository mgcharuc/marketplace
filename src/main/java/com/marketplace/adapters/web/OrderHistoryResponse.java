package com.marketplace.adapters.web;

import com.marketplace.application.port.PageResult;
import java.util.List;

record OrderHistoryResponse(
        List<OrderSummaryResponse> items, Integer page, Integer size, Long totalItems) {

    static OrderHistoryResponse of(PageResult<OrderSummaryResponse> result) {
        return new OrderHistoryResponse(
                result.items(), result.page(), result.size(), result.totalItems());
    }
}
