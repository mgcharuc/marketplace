package com.marketplace.adapters.web;

import com.marketplace.domain.order.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

// La clave no se recorta: un espacio la vuelve inválida.
public class IdempotencyKeyInterceptor implements HandlerInterceptor {

    static final String HEADER = "Idempotency-Key";
    static final String ATTRIBUTE = "idempotencyKey";

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || !HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        List<String> keys = Collections.list(request.getHeaders(HEADER));
        if (keys.size() != 1 || !IdempotencyKey.isValid(keys.get(0))) {
            throw new IdempotencyKeyException();
        }
        request.setAttribute(ATTRIBUTE, keys.get(0));
        return true;
    }
}
