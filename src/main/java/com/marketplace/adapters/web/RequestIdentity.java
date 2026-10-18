package com.marketplace.adapters.web;

record RequestIdentity(String tenantId, String customerId) {

    static final String ATTRIBUTE = "requestIdentity";
}
