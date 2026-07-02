package com.paystream.audit.api.dto.response;

import java.time.Instant;

public record AuditLogResponse(
        String  id,
        String  eventId,
        String  eventType,
        String  entityId,
        String  entityType,
        String  actorId,
        String  actorRole,
        String  action,
        String  oldState,
        String  newState,
        String  correlationId,
        String  sourceService,
        Instant createdAt
) {}
