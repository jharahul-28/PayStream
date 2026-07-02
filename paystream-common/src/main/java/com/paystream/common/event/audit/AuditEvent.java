package com.paystream.common.event.audit;

public record AuditEvent(
        String eventId,
        String eventType,
        String entityId,
        String entityType,
        String actorId,
        String actorRole,
        String action,
        String oldStateJson,
        String newStateJson,
        String metadataJson,
        String correlationId,
        String sourceService,
        String ipAddress
) {}
