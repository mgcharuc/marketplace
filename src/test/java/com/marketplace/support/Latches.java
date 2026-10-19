package com.marketplace.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Latches {

    private Latches() {}

    public static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("El latch no se liberó en 10 segundos");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
