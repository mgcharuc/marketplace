package com.marketplace.domain.config;

/**
 * Cómo aplica una escala sus porcentajes. En {@code MARGINAL} cada tramo descuenta solo las
 * unidades que caen dentro de él. En {@code ALL_UNITS} el tramo más alto alcanzado descuenta todas.
 */
public enum ScaleMode {
    MARGINAL,
    ALL_UNITS
}
