package com.marketplace.adapters.web;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

final class JsonReadErrors {

    private JsonReadErrors() {}

    static ApiError of(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return error(unknown.getPath(), "UNKNOWN_FIELD");
        }
        if (cause instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            return error(mismatch.getPath(), "INVALID_TYPE");
        }
        if (cause instanceof InputCoercionException overflow) {
            return error(overflow.getPath(), "INVALID_TYPE");
        }
        return new ApiError(null, null, null, "MALFORMED_JSON");
    }

    private static ApiError error(List<JacksonException.Reference> path, String code) {
        Integer index =
                path.stream()
                        .filter(reference -> reference.getIndex() >= 0)
                        .map(JacksonException.Reference::getIndex)
                        .findFirst()
                        .orElse(null);
        String field =
                path.stream()
                        .map(JacksonException.Reference::getPropertyName)
                        .filter(name -> name != null)
                        .collect(Collectors.joining("."));
        return new ApiError(index, field.isEmpty() ? null : field, null, code);
    }
}
