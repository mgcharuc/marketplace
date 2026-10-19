package com.marketplace.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestHashTest {

    @Test
    @DisplayName("El hash es el SHA-256 en hexadecimal del cuerpo en JSON compacto")
    void hashesCompactJson() throws Exception {
        assertThat(RequestHash.of(new OrderRequest("q_00000000000000000000000001")))
                .isEqualTo(sha256("{\"quoteId\":\"q_00000000000000000000000001\"}"))
                .isEqualTo("920addf83ad8ce0a961e433161b4a4557a4c739318730bcd73b2df241c43d615");
    }

    @Test
    @DisplayName("Los campos se ordenan por nombre antes de calcular el hash")
    void sortsFields() throws Exception {
        record Body(String zeta, String alpha) {}

        assertThat(RequestHash.of(new Body("z", "a")))
                .isEqualTo(sha256("{\"alpha\":\"a\",\"zeta\":\"z\"}"));
    }

    private static String sha256(String json) throws Exception {
        byte[] digest =
                MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
