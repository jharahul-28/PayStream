package com.paystream.fraud.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates X-Internal-Service-Key for the /api/v1/fraud/check endpoint.
 * All other endpoints rely on the JWT forwarded by the gateway.
 */
@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthFilter.class);
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Service-Key";
    private static final String FRAUD_CHECK_PATH    = "/api/v1/fraud/check";

    @Value("${paystream.internal.service-key:dev-only-local-key}")
    private String expectedKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (FRAUD_CHECK_PATH.equals(path)) {
            String providedKey = request.getHeader(INTERNAL_KEY_HEADER);
            if (!expectedKey.equals(providedKey)) {
                log.warn("Rejected fraud check — invalid internal service key path={}", path);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
