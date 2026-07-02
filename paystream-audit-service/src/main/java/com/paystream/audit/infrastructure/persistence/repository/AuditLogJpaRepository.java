package com.paystream.audit.infrastructure.persistence.repository;

import com.paystream.audit.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, String> {

    Optional<AuditLogEntity> findByEventId(String eventId);

    Page<AuditLogEntity> findByEntityId(String entityId, Pageable pageable);

    Page<AuditLogEntity> findByActorId(String actorId, Pageable pageable);

    Page<AuditLogEntity> findByEventType(String eventType, Pageable pageable);

    Page<AuditLogEntity> findBySourceService(String sourceService, Pageable pageable);
}
