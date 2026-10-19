package com.marketplace.adapters.web;

import java.util.regex.Pattern;

final class IdFormat {

    private static final String ULID = "[0-9A-HJKMNP-TV-Z]{26}";

    static final String QUOTE_ID = "q_" + ULID;
    static final String ORDER_ID = "o_" + ULID;

    private static final Pattern QUOTE = Pattern.compile(QUOTE_ID);
    private static final Pattern ORDER = Pattern.compile(ORDER_ID);

    private IdFormat() {}

    static boolean isQuoteId(String value) {
        return QUOTE.matcher(value).matches();
    }

    static boolean isOrderId(String value) {
        return ORDER.matcher(value).matches();
    }
}
