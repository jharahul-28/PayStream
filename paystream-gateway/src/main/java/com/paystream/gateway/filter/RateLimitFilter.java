package com.paystream.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis sliding-window rate limiter — 100 requests per minute per authenticated user.
 * Unauthenticated requests are rate-limited by IP address.
 * Returns HTTP 429 with a Retry-After header on breach.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int    LIMIT_PER_MINUTE = 100;
    private static final String KEY_PREFIX       = "ratelimit:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String key = KEY_PREFIX + (userId != null
                ? "user:" + userId
                : "ip:" + getClientIp(exchange));

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in the window — set TTL
                        return redisTemplate.expire(key, Duration.ofMinutes(1))
                                .then(chain.filter(exchange));
                    }
                    if (count > LIMIT_PER_MINUTE) {
                        log.warn("Rate limit exceeded key={} count={}", key, count);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().set("Retry-After", "60");
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        // Run after JWT filter (which sets X-User-Id) but before routing
        return -50;
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
