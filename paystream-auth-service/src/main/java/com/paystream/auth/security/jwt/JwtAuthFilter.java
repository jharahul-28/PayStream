package com.paystream.auth.security.jwt;

import com.paystream.common.constant.RedisKeys;
import com.paystream.common.exception.AuthException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter for the auth-service itself.
 *
 * Steps:
 *  1. Extract Bearer token from Authorization header.
 *  2. Validate signature and expiry via {@link JwtService}.
 *  3. Check Redis blocklist — a logged-out token is refused even within its TTL.
 *  4. Populate {@link SecurityContextHolder} with the authenticated principal.
 *
 * If any step fails the request is rejected with 401 and processing stops.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService            jwtService;
    private final StringRedisTemplate   redisTemplate;

    public JwtAuthFilter(JwtService jwtService, StringRedisTemplate redisTemplate) {
        this.jwtService    = jwtService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.validateToken(token);

            String jti    = claims.getId();
            String userId = claims.getSubject();
            String role   = claims.get("role", String.class);

            // Blocklist check — token may be valid but already revoked via logout
            Boolean blocked = redisTemplate.hasKey(RedisKeys.tokenBlocklist(jti));
            if (Boolean.TRUE.equals(blocked)) {
                log.warn("Blocklisted token used jti={} correlationId={}", jti, MDC.get("correlationId"));
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (AuthException e) {
            log.warn("JWT auth failed correlationId={} reason={}", MDC.get("correlationId"), e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
