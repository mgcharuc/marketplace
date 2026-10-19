package com.marketplace.adapters.web;

import com.marketplace.application.port.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.regex.Pattern;

// Los parámetros llegan como texto para distinguir un tipo incorrecto de un número fuera de rango.
record HistoryRequest(
        @Min(value = 0, message = "OUT_OF_RANGE")
                @Max(value = Integer.MAX_VALUE, message = "OUT_OF_RANGE")
                BigInteger page,
        @Min(value = 1, message = "OUT_OF_RANGE")
                @Max(value = PageQuery.MAX_SIZE, message = "OUT_OF_RANGE")
                BigInteger size) {

    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final BigInteger DEFAULT_PAGE = BigInteger.ZERO;
    private static final BigInteger DEFAULT_SIZE = BigInteger.valueOf(20);

    static HistoryRequest parse(String page, String size) {
        var errors = new ArrayList<ApiError>();
        var request =
                new HistoryRequest(
                        number(page, "page", DEFAULT_PAGE, errors),
                        number(size, "size", DEFAULT_SIZE, errors));
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return request;
    }

    PageQuery toQuery() {
        return new PageQuery(page.intValueExact(), size.intValueExact());
    }

    private static BigInteger number(
            String raw, String field, BigInteger fallback, ArrayList<ApiError> errors) {
        String text = Text.normalize(raw);
        if (text == null) {
            return fallback;
        }
        if (!INTEGER.matcher(text).matches()) {
            errors.add(ApiError.of(field, "INVALID_TYPE"));
            return fallback;
        }
        return new BigInteger(text);
    }
}
