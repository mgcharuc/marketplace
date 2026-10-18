package com.marketplace.adapters.web;

import com.marketplace.domain.Identifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class IdentityInterceptor implements HandlerInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String CUSTOMER_HEADER = "X-Customer-Id";

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        var errors = new ArrayList<ApiError>();
        String tenantId = header(request, TENANT_HEADER, errors);
        String customerId = header(request, CUSTOMER_HEADER, errors);
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparing(ApiError::field));
            throw new IdentityHeadersException(errors);
        }
        request.setAttribute(RequestIdentity.ATTRIBUTE, new RequestIdentity(tenantId, customerId));
        MDC.put("tenantId", tenantId);
        MDC.put("customerId", customerId);
        return true;
    }

    private static String header(HttpServletRequest request, String name, List<ApiError> errors) {
        String value = Text.normalize(request.getHeader(name));
        if (value == null) {
            errors.add(ApiError.of(name, "REQUIRED"));
        } else if (!Identifier.isValid(value)) {
            errors.add(ApiError.of(name, "INVALID_FORMAT"));
        }
        return value;
    }
}
