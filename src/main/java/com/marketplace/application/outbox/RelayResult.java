package com.marketplace.application.outbox;

public record RelayResult(int delivered, int retried, int failed, int discarded) {}
