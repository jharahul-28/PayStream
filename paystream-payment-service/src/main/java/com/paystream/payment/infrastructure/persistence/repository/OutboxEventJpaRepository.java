package com.paystream.payment.infrastructure.persistence.repository;

import com.paystream.payment.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, String> {

    /**
     * FOR UPDATE SKIP LOCKED — prevents two relay pods from picking the same row.
     * LIMIT is passed as a parameter so it can be tuned without code changes.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published = FALSE
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> findUnpublishedForUpdate(@Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM outbox_events WHERE published = FALSE", nativeQuery = true)
    long countUnpublished();
}
