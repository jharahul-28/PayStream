package com.paystream.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Path-specific Redis sliding-window rate limiter.
 *
 * Limits (per spec §SECTION 1: SECURITY HARDENING):
 *   POST /api/v1/auth/login    — 5 req/min per IP
 *   POST /api/v1/auth/register — 3 req/min per IP
 *   POST /api/v1/payments      — 50 req/min per userId
 *   POST /api/v1/fraud/check   — 200 req/min service-wide
 *   All other                  — 100 req/min per userId (existing global limiter)
 *
 * Returns HTTP 429 with Retry-After: 60 on breach.
 */
@Component
public class AdvancedRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdvancedRateLimitFilter.class);

    private static final String KEY_PREFIX = "advratelimit:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public AdvancedRateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path   = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String clientIp = getClientIp(exchange);

        RouteLimit limit = resolveLimit(path, method, userId, clientIp);
        if (limit == null) {
            return chain.filter(exchange);
        }

        return redisTemplate.opsForValue().increment(limit.key())
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(limit.key(), Duration.ofMinutes(1))
                                .then(chain.filter(exchange));
                    }
                    if (count > limit.maxRequests()) {
                        log.warn("Rate limit exceeded key={} count={} limit={}", limit.key(), count, limit.maxRequests());
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().set("Retry-After", "60");
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

    private RouteLimit resolveLimit(String path, HttpMethod method, String userId, String clientIp) {
        if (HttpMethod.POST.equals(method) && path.equals("/api/v1/auth/login")) {
            return new RouteLimit(KEY_PREFIX + "login:ip:" + clientIp, 5);
        }
        if (HttpMethod.POST.equals(method) && path.equals("/api/v1/auth/register")) {
            return new RouteLimit(KEY_PREFIX + "register:ip:" + clientIp, 3);
        }
        if (HttpMethod.POST.equals(method) && path.equals("/api/v1/payments")) {
            String key = userId != null ? "payments:user:" + userId : "payments:ip:" + clientIp;
            return new RouteLimit(KEY_PREFIX + key, 50);
        }
        if (path.startsWith("/api/v1/fraud/check")) {
            return new RouteLimit(KEY_PREFIX + "fraud-check:global", 200);
        }
        return null; // handled by the existing RateLimitFilter
    }

    @Override
    public int getOrder() {
        // After JWT filter (which sets X-User-Id) but before routing; just after RateLimitFilter
        return -49;
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    private record RouteLimit(String key, int maxRequests) {}
}
