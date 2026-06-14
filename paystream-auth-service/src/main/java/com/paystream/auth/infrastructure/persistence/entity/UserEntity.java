package com.paystream.auth.infrastructure.persistence.entity;

import com.paystream.auth.domain.model.Role;
import com.paystream.auth.domain.model.UserStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity for the {@code users} table.
 * This class is intentionally separate from the domain {@link com.paystream.auth.domain.model.User}.
 * Persistence annotations must NOT appear on domain objects.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    // JPA requires a no-arg constructor
    protected UserEntity() {}

    public UserEntity(String id, String email, String passwordHash, String fullName,
                      Role role, UserStatus status) {
        this.id                  = id;
        this.email               = email;
        this.passwordHash        = passwordHash;
        this.fullName            = fullName;
        this.role                = role;
        this.status              = status;
        this.failedLoginAttempts = 0;
        this.createdAt           = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getId()                  { return id; }
    public String getEmail()               { return email; }
    public String getPasswordHash()        { return passwordHash; }
    public String getFullName()            { return fullName; }
    public Role getRole()                  { return role; }
    public UserStatus getStatus()          { return status; }
    public int getFailedLoginAttempts()    { return failedLoginAttempts; }
    public Instant getLockedUntil()        { return lockedUntil; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }
    public Integer getVersion()            { return version; }

    // Mutators — only called by the persistence adapter when syncing domain state
    public void setStatus(UserStatus status)                  { this.status = status; }
    public void setFailedLoginAttempts(int attempts)          { this.failedLoginAttempts = attempts; }
    public void setLockedUntil(Instant lockedUntil)           { this.lockedUntil = lockedUntil; }
}
