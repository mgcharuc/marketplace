package com.marketplace.domain.order;

import com.marketplace.domain.Identifier;
import java.util.Objects;

/** Resultado de reservar una clave de idempotencia. */
public sealed interface Reservation {

    /** La clave es nueva o estaba vencida, así que la petición se procesa. */
    record Reserved() implements Reservation {}

    /** Con la clave y el mismo cuerpo ya se creó una orden, y se repite su respuesta. */
    record Replay(String orderId, String body) implements Reservation {

        public Replay {
            Identifier.require(orderId, "orderId");
            Objects.requireNonNull(body, "body");
        }
    }

    /** La clave ya se usó con otro cuerpo. */
    record Conflict() implements Reservation {}
}
