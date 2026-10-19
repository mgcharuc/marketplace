package com.marketplace.adapters.web;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

// SHA-256 del JSON canónico, con las claves ordenadas, sin espacios y en UTF-8.
final class RequestHash {

    private static final JsonMapper CANONICAL =
            JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    private RequestHash() {}

    static String of(Object normalizedRequest) {
        Map<?, ?> fields = CANONICAL.convertValue(normalizedRequest, Map.class);
        byte[] json = CANONICAL.writeValueAsBytes(fields);
        return HexFormat.of().formatHex(sha256().digest(json));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("El JDK no ofrece SHA-256", e);
        }
    }
}
