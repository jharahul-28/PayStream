package com.paystream.wallet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guards internal debit/credit endpoints with a shared service key.
 * The key is injected from the environment variable {@code INTERNAL_SERVICE_KEY}
 * and must be set identically on the gateway and calling services.
 *
 * Requests lacking the correct {@code X-Internal-Service-Key} header are rejected with 403.
 */
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Internal-Service-Key";

    private final String expectedKey;

    public InternalServiceAuthFilter(String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String provided = request.getHeader(HEADER_NAME);
        if (expectedKey == null || !expectedKey.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"success\":false,\"errorCode\":\"PS-4003\",\"errorMessage\":\"Missing or invalid internal service key\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Only apply to internal debit/credit paths
        return !path.contains("/debit") && !path.contains("/credit");
    }
}
