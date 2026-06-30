package com.paystream.ledger.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guards POST /api/v1/ledger/entries with a shared internal service key.
 * All other endpoints are unrestricted at this layer (JWT validated by gateway).
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
            response.getWriter().write(
                    "{\"success\":false,\"errorCode\":\"PS-4003\",\"errorMessage\":\"Missing or invalid internal service key\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only protect POST /api/v1/ledger/entries
        return !(request.getMethod().equals("POST") && request.getServletPath().endsWith("/entries"));
    }
}
