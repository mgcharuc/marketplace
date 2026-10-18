package com.marketplace.adapters.web;

final class Text {

    private Text() {}

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String blankAsNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
