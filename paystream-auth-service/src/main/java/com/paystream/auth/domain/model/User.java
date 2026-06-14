package com.paystream.auth.domain.model;

import java.time.Instant;

/**
 * User domain entity. Pure Java — zero framework or JPA annotations.
 * All state mutation is via explicit behaviour methods; no public setters.
 *
 * Invariant: failedLoginAttempts >= 5 sets lockedUntil 15 minutes in the future.
 */
public class User {

    private static final int  MAX_FAILED_ATTEMPTS  = 5;
    private static final long LOCKOUT_DURATION_SECS = 900L; // 15 minutes

    private final String id;
    private final String email;
    private String passwordHash;
    private final String fullName;
    private final Role role;
    private UserStatus status;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private final int version;

    public User(String id, String email, String passwordHash, String fullName,
                Role role, UserStatus status, int failedLoginAttempts,
                Instant lockedUntil, int version) {
        this.id                   = id;
        this.email                = email;
        this.passwordHash         = passwordHash;
        this.fullName             = fullName;
        this.role                 = role;
        this.status               = status;
        this.failedLoginAttempts  = failedLoginAttempts;
        this.lockedUntil          = lockedUntil;
        this.version              = version;
    }

    // -------------------------------------------------------------------------
    // Behaviour methods — all business logic lives here, not in services
    // -------------------------------------------------------------------------

    /**
     * Increments the failed login counter.
     * On the 5th consecutive failure the account is locked for 15 minutes.
     */
    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now().plusSeconds(LOCKOUT_DURATION_SECS);
            this.status      = UserStatus.LOCKED;
        }
    }

    /** Clears the failed-login counter and removes any lockout. */
    public void resetLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil         = null;
        if (this.status == UserStatus.LOCKED) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /** Returns {@code true} if the account is still within a lockout window. */
    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /** Returns {@code true} if the account is in the ACTIVE state. */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Accessors (read-only; no setters to preserve encapsulation)
    // -------------------------------------------------------------------------

    public String getId()                  { return id; }
    public String getEmail()               { return email; }
    public String getPasswordHash()        { return passwordHash; }
    public String getFullName()            { return fullName; }
    public Role getRole()                  { return role; }
    public UserStatus getStatus()          { return status; }
    public int getFailedLoginAttempts()    { return failedLoginAttempts; }
    public Instant getLockedUntil()        { return lockedUntil; }
    public int getVersion()                { return version; }
}
