package com.paystream.common.constant;

/**
 * All Redis key templates used across PayStream services.
 * No service may hard-code Redis keys as raw strings.
 */
public final class RedisKeys {

    private RedisKeys() {}

    // Auth service — JWT blocklist
    public static final String TOKEN_BLOCKLIST_PREFIX = "token:blocklist:";

    // Auth service — brute-force protection
    public static final String LOGIN_ATTEMPTS_PREFIX = "login:attempts:";
    public static final String LOGIN_LOCKED_PREFIX   = "login:locked:";

    // Payment service — idempotency
    public static final String IDEMPOTENCY_PREFIX    = "idempotency:";

    // Fraud service
    public static final String FRAUD_RULES_ACTIVE    = "fraud:rules:active";
    public static final String VELOCITY_PREFIX        = "velocity:";
    public static final String USER_BLOCKED_PREFIX    = "user:blocked:";

    /** Builds the token blocklist key for a given JWT ID. */
    public static String tokenBlocklist(String jti) {
        return TOKEN_BLOCKLIST_PREFIX + jti;
    }

    /** Builds the login-attempts key for a given email address. */
    public static String loginAttempts(String email) {
        return LOGIN_ATTEMPTS_PREFIX + email;
    }

    /** Builds the login-locked key for a given email address. */
    public static String loginLocked(String email) {
        return LOGIN_LOCKED_PREFIX + email;
    }

    /** Builds the idempotency key for payment initiation. */
    public static String idempotency(String userId, String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + userId + ":" + idempotencyKey;
    }

    /** Builds the daily velocity key for a user. */
    public static String velocity(String userId, String date) {
        return VELOCITY_PREFIX + userId + ":" + date;
    }

    /** Builds the user-blocked key for fraud service. */
    public static String userBlocked(String userId) {
        return USER_BLOCKED_PREFIX + userId;
    }
}
