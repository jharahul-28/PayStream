package com.paystream.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that guarantees a correlation ID is present in every request.
 *
 * <ul>
 *   <li>Reads {@code X-Correlation-Id} from the inbound request header.</li>
 *   <li>Generates a UUID if absent.</li>
 *   <li>Stores the value in SLF4J MDC under key {@code correlationId}.</li>
 *   <li>Echoes the value back in the response header.</li>
 *   <li>Cleans up MDC on exit to prevent thread-pool leakage.</li>
 * </ul>
 */
public class CorrelationIdFilter implements Filter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY     = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpRequest  = (HttpServletRequest)  request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        httpResponse.setHeader(HEADER_NAME, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Always clean MDC — reused threads in servlet containers will carry stale values otherwise
            MDC.remove(MDC_KEY);
        }
    }
}
