package com.marketplace.application.port;

import java.util.function.Supplier;

/** Ejecuta el trabajo en una transacción y la revierte completa si algo falla. */
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);

    default void run(Runnable work) {
        execute(
                () -> {
                    work.run();
                    return null;
                });
    }
}
