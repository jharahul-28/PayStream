package com.paystream.auth.security.jwt;

import com.paystream.auth.domain.model.User;
import com.paystream.common.exception.AuthException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates RS256 JWTs.
 *
 * Access tokens carry: sub=userId, email, role, jti=UUID.
 * Refresh tokens are opaque random bytes — only the SHA-256 hash is stored in DB.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PrivateKey privateKey;
    private final PublicKey  publicKey;
    private final JwtProperties properties;

    public JwtService(PrivateKey privateKey, PublicKey publicKey, JwtProperties properties) {
        this.privateKey = privateKey;
        this.publicKey  = publicKey;
        this.properties = properties;
    }

    /**
     * Generates a signed RS256 JWT access token for the given user.
     * Claims: sub=userId, email, role, jti=UUID (for blocklist lookups).
     */
    public String generateAccessToken(User user) {
        long nowMs      = System.currentTimeMillis();
        long expiryMs   = nowMs + (properties.getAccessTokenTtlSeconds() * 1_000L);

        return Jwts.builder()
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("role",  user.getRole().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(nowMs))
                .expiration(new Date(expiryMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Generates a 256-bit cryptographically random opaque refresh token.
     * The caller is responsible for hashing it before storing.
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Validates the token signature and expiry.
     *
     * @throws AuthException on invalid signature, malformed token, or expiry.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired jti={}", extractJtiSafe(token));
            throw AuthException.expired();
        } catch (SignatureException | MalformedJwtException | UnsupportedJwtException e) {
            log.warn("JWT invalid reason={}", e.getMessage());
            throw AuthException.invalid(e.getMessage());
        }
    }

    public String extractUserId(String token) {
        return parseUnsafe(token).getSubject();
    }

    public String extractJti(String token) {
        return parseUnsafe(token).getId();
    }

    /**
     * Returns the number of seconds until the token expires.
     * Returns 0 if already expired (prevents negative TTL being passed to Redis).
     */
    public long getRemainingTtlSeconds(String token) {
        Date expiry = parseUnsafe(token).getExpiration();
        long remaining = (expiry.getTime() - System.currentTimeMillis()) / 1_000L;
        return Math.max(remaining, 0L);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Claims parseUnsafe(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String extractJtiSafe(String token) {
        try {
            return Jwts.parser().verifyWith(publicKey).build()
                    .parseSignedClaims(token).getPayload().getId();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
