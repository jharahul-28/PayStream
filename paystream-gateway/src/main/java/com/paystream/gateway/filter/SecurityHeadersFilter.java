package com.paystream.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds OWASP-recommended HTTP security headers to every response.
 * Runs last (highest order value) so headers are always set regardless of route.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            var headers = response.getHeaders();
            headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            headers.set("X-Content-Type-Options",    "nosniff");
            headers.set("X-Frame-Options",           "DENY");
            headers.set("Referrer-Policy",           "no-referrer");
            headers.set("Cache-Control",             "no-store, no-cache");
            headers.set("Content-Security-Policy",   "default-src 'none'");
        }));
    }

    @Override
    public int getOrder() {
        // Runs after routing — ensures every routed response gets headers
        return Ordered.LOWEST_PRECEDENCE;
    }
}
