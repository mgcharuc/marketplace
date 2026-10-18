package com.marketplace.application.port;

import com.marketplace.domain.config.ConfigVersion;

/** Convierte una versión comercial a texto para guardarla y la reconstruye al leerla. */
public interface ConfigCodec {

    String encode(ConfigVersion version);

    /**
     * Falla con {@code ConfigInvalidException} si el texto no tiene la forma esperada. Informa los
     * errores de forma juntos y se detiene en el primer valor que el dominio rechaza.
     */
    ConfigVersion decode(String text);
}
