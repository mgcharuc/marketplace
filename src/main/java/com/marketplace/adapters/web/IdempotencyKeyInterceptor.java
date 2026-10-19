package com.marketplace.adapters.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

// La clave no se recorta: un espacio la vuelve inválida.
public class IdempotencyKeyInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Idempotency-Key";
    private static final Pattern FORMAT = Pattern.compile("[\\x21-\\x7E]{1,128}");

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || !HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String key = request.getHeader(HEADER);
        if (key == null || !FORMAT.matcher(key).matches()) {
            throw new IdempotencyKeyException();
        }
        return true;
    }
}
