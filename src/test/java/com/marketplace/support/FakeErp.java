package com.marketplace.support;

import com.marketplace.application.port.DeliveryException;
import com.marketplace.application.port.ErpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class FakeErp implements ErpClient {

    private record Hold(CountDownLatch entered, CountDownLatch release) {}

    private final AtomicReference<Hold> hold = new AtomicReference<>();

    private final Map<String, String> received = new LinkedHashMap<>();
    private boolean available = true;
    private RuntimeException failure;
    private int calls;

    // La espera queda fuera del bloque sincronizado para que otros hilos sigan enviando.
    @Override
    public void send(String payload, String idempotencyKey) throws DeliveryException {
        Hold next = hold.getAndSet(null);
        if (next != null) {
            next.entered().countDown();
            Latches.await(next.release());
        }
        receive(payload, idempotencyKey);
    }

    private synchronized void receive(String payload, String idempotencyKey)
            throws DeliveryException {
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

    /** El próximo envío avisa con {@code entered} y espera {@code release} antes de seguir. */
    public void holdNext(CountDownLatch entered, CountDownLatch release) {
        hold.set(new Hold(entered, release));
    }

    public synchronized void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    synchronized void reset() {
        received.clear();
        available = true;
        failure = null;
        calls = 0;
        hold.set(null);
    }
}
