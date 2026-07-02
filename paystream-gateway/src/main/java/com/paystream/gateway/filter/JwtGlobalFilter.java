package com.paystream.gateway.filter;

import com.paystream.gateway.security.JwksCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gateway-wide JWT validation filter.
 *
 * For every request it:
 *  1. Skips permit-all paths (auth endpoints, JWKS, actuator health).
 *  2. Extracts and validates the Bearer token using the cached JWKS public key.
 *  3. Injects X-User-Id and X-User-Role headers for downstream services.
 *  4. Strips the Authorization header before forwarding (internal services
 *     authenticate via X-Internal-Service-Key, not user JWTs).
 */
@Component
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtGlobalFilter.class);

    private static final List<String> PERMIT_ALL_PREFIXES = List.of(
            "/api/v1/auth/",
            "/.well-known/",
            "/actuator/health"
    );

    private final JwksCache jwksCache;

    public JwtGlobalFilter(JwksCache jwksCache) {
        this.jwksCache = jwksCache;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPermitAll(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or malformed Authorization header path={}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwksCache.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String role   = claims.get("role", String.class);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id",   userId)
                    .header("X-User-Role", role)
                    .headers(h -> h.remove("Authorization")) // strip before forwarding
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            log.warn("JWT validation failed path={} reason={}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        // Run before route predicates
        return -100;
    }

    private boolean isPermitAll(String path) {
        return PERMIT_ALL_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
