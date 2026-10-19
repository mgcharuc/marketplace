package com.marketplace.adapters.web;

import com.marketplace.domain.DomainException;
import com.marketplace.domain.config.CustomerNotFoundException;
import com.marketplace.domain.config.DistributorNotFoundException;
import com.marketplace.domain.config.TenantNotFoundException;
import com.marketplace.domain.credit.CreditAccountNotFoundException;
import com.marketplace.domain.credit.CreditLimitExceededException;
import com.marketplace.domain.money.Money;
import com.marketplace.domain.order.OrderNotFoundException;
import com.marketplace.domain.order.QuoteAlreadyConfirmedException;
import com.marketplace.domain.quote.CartError;
import com.marketplace.domain.quote.InvalidCartException;
import com.marketplace.domain.quote.QuoteExpiredException;
import com.marketplace.domain.quote.QuoteNotFoundException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://marketplace.example/problems/";

    @ExceptionHandler(IdentityHeadersException.class)
    ResponseEntity<ProblemDetail> identity(IdentityHeadersException exception) {
        return rejected(
                HttpStatus.BAD_REQUEST,
                "IDENTITY_HEADERS_REQUIRED",
                "Faltan las cabeceras de identidad o tienen un formato inválido.",
                Map.of("errors", exception.errors()));
    }

    @ExceptionHandler(IdempotencyKeyException.class)
    ResponseEntity<ProblemDetail> idempotencyKey() {
        return rejected(
                HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_REQUIRED",
                "Falta la cabecera Idempotency-Key o tiene un formato inválido.",
                Map.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> invalidRequest(InvalidRequestException exception) {
        return invalidRequest(exception.errors());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException exception) {
        return invalidRequest(List.of(JsonReadErrors.of(exception)));
    }

    @ExceptionHandler(InvalidCartException.class)
    ResponseEntity<ProblemDetail> invalidCart(InvalidCartException exception) {
        List<ApiError> errors =
                exception.errors().stream().map(ApiExceptionHandler::apiError).toList();
        return rejected(HttpStatus.UNPROCESSABLE_CONTENT, exception, Map.of("errors", errors));
    }

    @ExceptionHandler({
        TenantNotFoundException.class,
        CustomerNotFoundException.class,
        QuoteNotFoundException.class,
        OrderNotFoundException.class
    })
    ResponseEntity<ProblemDetail> notFound(DomainException exception) {
        return rejected(HttpStatus.NOT_FOUND, exception, Map.of());
    }

    @ExceptionHandler(QuoteExpiredException.class)
    ResponseEntity<ProblemDetail> quoteExpired(QuoteExpiredException exception) {
        return rejected(
                HttpStatus.CONFLICT,
                exception,
                Map.of("expiresAt", exception.expiresAt().toString()));
    }

    @ExceptionHandler(QuoteAlreadyConfirmedException.class)
    ResponseEntity<ProblemDetail> quoteAlreadyConfirmed(QuoteAlreadyConfirmedException exception) {
        return rejected(HttpStatus.CONFLICT, exception, Map.of("orderId", exception.orderId()));
    }

    @ExceptionHandler({DistributorNotFoundException.class, CreditAccountNotFoundException.class})
    ResponseEntity<ProblemDetail> unprocessable(DomainException exception) {
        return rejected(HttpStatus.UNPROCESSABLE_CONTENT, exception, Map.of());
    }

    @ExceptionHandler(CreditLimitExceededException.class)
    ResponseEntity<ProblemDetail> creditLimitExceeded(CreditLimitExceededException exception) {
        var extensions = new LinkedHashMap<String, Object>();
        extensions.put("available", amount(exception.available()));
        extensions.put("required", amount(exception.required()));
        return rejected(HttpStatus.UNPROCESSABLE_CONTENT, exception, extensions);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> routeNotFound() {
        return rejected(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "La ruta no existe.", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed() {
        return rejected(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "La ruta no admite ese método.",
                Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> unsupportedMediaType() {
        return rejected(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "El cuerpo debe enviarse como application/json.",
                Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Error inesperado al atender la petición", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Ocurrió un error inesperado.",
                Map.of());
    }

    private ResponseEntity<ProblemDetail> invalidRequest(List<ApiError> errors) {
        return rejected(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "La petición no es válida.",
                Map.of("errors", errors));
    }

    private ResponseEntity<ProblemDetail> rejected(
            HttpStatus status, DomainException exception, Map<String, ?> extensions) {
        return rejected(status, exception.code(), exception.getMessage(), extensions);
    }

    private ResponseEntity<ProblemDetail> rejected(
            HttpStatus status, String code, String detail, Map<String, ?> extensions) {
        log.warn("Petición rechazada con {}", code);
        return problem(status, code, detail, extensions);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail, Map<String, ?> extensions) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(title(code));
        problem.setProperty("code", code);
        extensions.forEach(problem::setProperty);
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static String title(String code) {
        String words = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String amount(Money money) {
        return money.amount().toPlainString();
    }

    private static ApiError apiError(CartError error) {
        return new ApiError(error.index(), error.field(), error.sku(), error.code());
    }
}
