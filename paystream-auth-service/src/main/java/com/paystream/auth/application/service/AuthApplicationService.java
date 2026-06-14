package com.paystream.auth.application.service;

import com.paystream.auth.api.dto.response.AuthResponse;
import com.paystream.auth.api.dto.response.UserResponse;
import com.paystream.auth.application.command.LoginCommand;
import com.paystream.auth.application.command.RegisterCommand;
import com.paystream.auth.application.port.in.AuthUseCase;
import com.paystream.auth.application.port.out.RefreshTokenRepository;
import com.paystream.auth.application.port.out.UserRepository;
import com.paystream.auth.domain.model.Role;
import com.paystream.auth.domain.model.User;
import com.paystream.auth.domain.model.UserStatus;
import com.paystream.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import com.paystream.auth.security.jwt.JwtProperties;
import com.paystream.auth.security.jwt.JwtService;
import com.paystream.common.constant.RedisKeys;
import com.paystream.common.exception.AuthException;
import com.paystream.common.exception.DuplicateResourceException;
import com.paystream.common.exception.ResourceNotFoundException;
import com.paystream.common.util.IdGenerator;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Implements all authentication use-cases.
 *
 * Key design decisions:
 *  - Brute-force protection is enforced in Redis AND the database (two layers).
 *  - Refresh tokens are stored as SHA-256 hashes; the raw token only lives in memory during a request.
 *  - Access token blocklist uses the JWT's jti claim so only the specific token is revoked.
 */
@Service
public class AuthApplicationService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthApplicationService.class);

    private static final int    MAX_LOGIN_ATTEMPTS       = 5;
    private static final long   LOCKOUT_TTL_SECONDS      = 900L;

    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService             jwtService;
    private final JwtProperties          jwtProperties;
    private final PasswordEncoder        passwordEncoder;
    private final StringRedisTemplate    redisTemplate;

    public AuthApplicationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            JwtProperties jwtProperties,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redisTemplate) {
        this.userRepository         = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService             = jwtService;
        this.jwtProperties          = jwtProperties;
        this.passwordEncoder        = passwordEncoder;
        this.redisTemplate          = redisTemplate;
    }

    // -------------------------------------------------------------------------
    // Register
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public UserResponse register(RegisterCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            log.warn("Registration rejected — duplicate email correlationId={}", MDC.get("correlationId"));
            throw new DuplicateResourceException("Email already registered: " + command.email());
        }

        String passwordHash = passwordEncoder.encode(command.rawPassword());
        User user = new User(
                IdGenerator.generate(),
                command.email(),
                passwordHash,
                command.fullName(),
                command.role(),
                UserStatus.ACTIVE,
                0,
                null,
                0
        );

        User saved = userRepository.save(user);
        log.info("User registered userId={} role={} correlationId={}",
                saved.getId(), saved.getRole(), MDC.get("correlationId"));

        return toUserResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public AuthResponse login(LoginCommand command) {
        String safeEmail = sanitize(command.email());

        // Check Redis-level lockout (faster than DB query)
        Boolean locked = redisTemplate.hasKey(RedisKeys.loginLocked(command.email()));
        if (locked) {
            log.warn("Login blocked — account locked correlationId={} email={}",
                    MDC.get("correlationId"), safeEmail);
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked");
        }

        User user = userRepository.findByEmail(command.email())
                .orElseThrow(() -> {
                    incrementFailedAttempts(command.email());
                    return AuthException.invalid("Invalid credentials");
                });

        if (user.isLocked()) {
            log.warn("Login rejected — domain-level lock userId={} correlationId={}",
                    user.getId(), MDC.get("correlationId"));
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked");
        }

        if (!user.isActive()) {
            log.warn("Login rejected — account suspended userId={} correlationId={}",
                    user.getId(), MDC.get("correlationId"));
            throw AuthException.invalid("Account is not active");
        }

        if (!passwordEncoder.matches(command.rawPassword(), user.getPasswordHash())) {
            user.recordFailedLogin();
            userRepository.save(user);
            incrementFailedAttempts(command.email());

            if (user.isLocked()) {
                log.warn("Account locked after {} failures userId={} correlationId={}",
                        MAX_LOGIN_ATTEMPTS, user.getId(), MDC.get("correlationId"));
                setRedisLock(command.email());
                throw new ResponseStatusException(HttpStatus.LOCKED, "Account locked after too many failed attempts");
            }

            log.warn("Login failed — wrong password attempt={} userId={} correlationId={}",
                    user.getFailedLoginAttempts(), user.getId(), MDC.get("correlationId"));
            throw AuthException.invalid("Invalid credentials");
        }

        // Successful login — clear failure counters
        user.resetLoginAttempts();
        userRepository.save(user);
        clearRedisAttempts(command.email());

        return issueTokenPair(user);
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);

        RefreshTokenEntity refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh failed — token not found correlationId={}", MDC.get("correlationId"));
                    return AuthException.invalid("Invalid refresh token");
                });

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Refresh failed — token expired userId={} correlationId={}",
                    refreshToken.getUserId(), MDC.get("correlationId"));
            throw AuthException.expired();
        }

        // Revoke the used refresh token (rotation — prevents token reuse)
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", refreshToken.getUserId()));

        log.info("Token refreshed userId={} correlationId={}", user.getId(), MDC.get("correlationId"));
        return issueTokenPair(user);
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void logout(String accessToken) {
        try {
            Claims claims = jwtService.validateToken(accessToken);
            String jti    = claims.getId();
            long remainingTtl = jwtService.getRemainingTtlSeconds(accessToken);

            // Add jti to blocklist — the token is invalid for its remaining TTL
            if (remainingTtl > 0) {
                redisTemplate.opsForValue()
                        .set(RedisKeys.tokenBlocklist(jti), "1", remainingTtl, TimeUnit.SECONDS);
            }

            String userId = claims.getSubject();
            refreshTokenRepository.revokeAllByUserId(userId);

            log.info("User logged out userId={} jti={} correlationId={}",
                    userId, jti, MDC.get("correlationId"));

        } catch (AuthException e) {
            // Token is already invalid — logout is a no-op
            log.debug("Logout with invalid token correlationId={}", MDC.get("correlationId"));
        }
    }

    // -------------------------------------------------------------------------
    // Get current user
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return toUserResponse(user);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AuthResponse issueTokenPair(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String rawRefresh   = jwtService.generateRefreshToken();
        String refreshHash  = hash(rawRefresh);

        RefreshTokenEntity refreshEntity = new RefreshTokenEntity(
                IdGenerator.generate(),
                user.getId(),
                refreshHash,
                Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlSeconds())
        );
        refreshTokenRepository.save(refreshEntity);

        return new AuthResponse(
                accessToken,
                rawRefresh,
                jwtProperties.getAccessTokenTtlSeconds(),
                "Bearer",
                user.getId(),
                user.getRole().name()
        );
    }

    private void incrementFailedAttempts(String email) {
        String key = RedisKeys.loginAttempts(email);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, LOCKOUT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void setRedisLock(String email) {
        redisTemplate.opsForValue()
                .set(RedisKeys.loginLocked(email), "1", LOCKOUT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void clearRedisAttempts(String email) {
        redisTemplate.delete(RedisKeys.loginAttempts(email));
        redisTemplate.delete(RedisKeys.loginLocked(email));
    }

    private String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getStatus());
    }

    /** Strips CR/LF from caller-supplied strings before logging them. */
    private static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]", "_");
    }
}
