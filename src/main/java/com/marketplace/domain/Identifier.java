package com.marketplace.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Formato común de los identificadores de negocio: letras, dígitos, guion y guion bajo. */
public final class Identifier {

    public static final String REGEX = "[A-Za-z0-9_-]{1,64}";

    private static final Pattern FORMAT = Pattern.compile(REGEX);

    private Identifier() {}

    public static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!isValid(value)) {
            throw new IllegalArgumentException(field + " con formato inválido: " + value);
        }
        return value;
    }

    public static boolean isValid(String value) {
        return FORMAT.matcher(value).matches();
    }
}
