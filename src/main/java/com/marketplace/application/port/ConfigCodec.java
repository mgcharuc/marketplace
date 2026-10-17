package com.marketplace.application.port;

import com.marketplace.domain.config.ConfigVersion;

/** Convierte una versión comercial a texto para guardarla y la reconstruye al leerla. */
public interface ConfigCodec {

    String encode(ConfigVersion version);

    /** Falla con la lista completa de violaciones si el texto no tiene la forma esperada. */
    ConfigVersion decode(String text);
}
