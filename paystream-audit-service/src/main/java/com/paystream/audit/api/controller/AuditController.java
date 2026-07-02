package com.paystream.audit.api.controller;

import com.paystream.audit.api.dto.response.AuditLogResponse;
import com.paystream.audit.infrastructure.persistence.entity.AuditLogEntity;
import com.paystream.audit.infrastructure.persistence.repository.AuditLogJpaRepository;
import com.paystream.common.api.ApiResponse;
import com.paystream.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OPS', 'FRAUD_ANALYST')")
public class AuditController {

    private final AuditLogJpaRepository repository;

    public AuditController(AuditLogJpaRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getLogs(
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String sourceService,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditLogEntity> results;
        if (entityId != null)      results = repository.findByEntityId(entityId, pageable);
        else if (actorId != null)  results = repository.findByActorId(actorId, pageable);
        else if (eventType != null) results = repository.findByEventType(eventType, pageable);
        else if (sourceService != null) results = repository.findBySourceService(sourceService, pageable);
        else results = repository.findAll(pageable);

        return ResponseEntity.ok(ApiResponse.success(results.map(this::toResponse)));
    }

    @GetMapping("/logs/{eventId}")
    public ResponseEntity<ApiResponse<AuditLogResponse>> getByEventId(@PathVariable String eventId) {
        AuditLogEntity entity = repository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog", eventId));
        return ResponseEntity.ok(ApiResponse.success(toResponse(entity)));
    }

    private AuditLogResponse toResponse(AuditLogEntity e) {
        return new AuditLogResponse(
                e.getId(), e.getEventId(), e.getEventType(),
                e.getEntityId(), e.getEntityType(),
                e.getActorId(), e.getActorRole(), e.getAction(),
                e.getOldState(), e.getNewState(),
                e.getCorrelationId(), e.getSourceService(), e.getCreatedAt()
        );
    }
}
