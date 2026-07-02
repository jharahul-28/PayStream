package com.paystream.audit.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Append-only entity. No updated_at, no @Version.
 * INSERT only. Never UPDATE. Never DELETE.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @Column(length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "event_id", length = 26, nullable = false, unique = true, updatable = false)
    private String eventId;

    @Column(name = "event_type", length = 100, nullable = false, updatable = false)
    private String eventType;

    @Column(name = "entity_id", length = 26, nullable = false, updatable = false)
    private String entityId;

    @Column(name = "entity_type", length = 50, nullable = false, updatable = false)
    private String entityType;

    @Column(name = "actor_id", length = 26, updatable = false)
    private String actorId;

    @Column(name = "actor_role", length = 50, updatable = false)
    private String actorRole;

    @Column(name = "action", length = 100, nullable = false, updatable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_state", columnDefinition = "jsonb", updatable = false)
    private String oldState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_state", columnDefinition = "jsonb", updatable = false)
    private String newState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", updatable = false)
    private String metadata;

    @Column(name = "correlation_id", length = 255, updatable = false)
    private String correlationId;

    @Column(name = "source_service", length = 50, nullable = false, updatable = false)
    private String sourceService;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLogEntity() {}

    public AuditLogEntity(String id, String eventId, String eventType,
                          String entityId, String entityType,
                          String actorId, String actorRole, String action,
                          String oldState, String newState, String metadata,
                          String correlationId, String sourceService,
                          String ipAddress, Instant createdAt) {
        this.id            = id;
        this.eventId       = eventId;
        this.eventType     = eventType;
        this.entityId      = entityId;
        this.entityType    = entityType;
        this.actorId       = actorId;
        this.actorRole     = actorRole;
        this.action        = action;
        this.oldState      = oldState;
        this.newState      = newState;
        this.metadata      = metadata;
        this.correlationId = correlationId;
        this.sourceService = sourceService;
        this.ipAddress     = ipAddress;
        this.createdAt     = createdAt;
    }

    // Read-only accessors
    public String  getId()            { return id; }
    public String  getEventId()       { return eventId; }
    public String  getEventType()     { return eventType; }
    public String  getEntityId()      { return entityId; }
    public String  getEntityType()    { return entityType; }
    public String  getActorId()       { return actorId; }
    public String  getActorRole()     { return actorRole; }
    public String  getAction()        { return action; }
    public String  getOldState()      { return oldState; }
    public String  getNewState()      { return newState; }
    public String  getMetadata()      { return metadata; }
    public String  getCorrelationId() { return correlationId; }
    public String  getSourceService() { return sourceService; }
    public String  getIpAddress()     { return ipAddress; }
    public Instant getCreatedAt()     { return createdAt; }
}
