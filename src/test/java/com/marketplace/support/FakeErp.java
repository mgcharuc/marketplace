package com.marketplace.support;

import com.marketplace.application.port.DeliveryException;
import com.marketplace.application.port.ErpClient;
import java.util.LinkedHashMap;
import java.util.Map;

public class FakeErp implements ErpClient {

    private final Map<String, String> received = new LinkedHashMap<>();
    private boolean available = true;
    private RuntimeException failure;
    private int calls;

    @Override
    public synchronized void send(String payload, String idempotencyKey) throws DeliveryException {
        calls++;
        if (failure != null) {
            throw failure;
        }
        if (!available) {
            throw new DeliveryException("ERP no disponible");
        }
        received.putIfAbsent(idempotencyKey, payload);
    }

    public synchronized Map<String, String> received() {
        return Map.copyOf(received);
    }

    public synchronized int calls() {
        return calls;
    }

    public synchronized void setAvailable(boolean available) {
        this.available = available;
    }

    public synchronized void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    synchronized void reset() {
        received.clear();
        available = true;
        failure = null;
        calls = 0;
    }
}
