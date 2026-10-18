package com.marketplace.adapters.web;

record ApiError(Integer index, String field, String sku, String code) {

    static ApiError of(String field, String code) {
        return new ApiError(null, field, null, code);
    }
}
