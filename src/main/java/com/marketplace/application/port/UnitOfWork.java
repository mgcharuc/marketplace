package com.marketplace.application.port;

import java.util.function.Supplier;

/** Ejecuta un trabajo en una transacción: se guarda todo o, si algo falla, nada. */
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
