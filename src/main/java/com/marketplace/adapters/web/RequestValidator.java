package com.marketplace.adapters.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class RequestValidator {

    private final Validator validator;

    RequestValidator(Validator validator) {
        this.validator = validator;
    }

    // Cada restricción lleva su código de error en el mensaje.
    <T> T require(T request) {
        List<ApiError> errors =
                validator.validate(request).stream()
                        .map(RequestValidator::error)
                        .sorted(Comparator.comparing(ApiError::field).thenComparing(ApiError::code))
                        .toList();
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return request;
    }

    private static ApiError error(ConstraintViolation<?> violation) {
        return ApiError.of(violation.getPropertyPath().toString(), violation.getMessage());
    }
}
