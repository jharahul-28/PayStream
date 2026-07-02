package com.paystream.webhook.infrastructure.persistence.repository;

import com.paystream.webhook.infrastructure.persistence.entity.WebhookEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WebhookEndpointJpaRepository extends JpaRepository<WebhookEndpointEntity, String> {

    List<WebhookEndpointEntity> findByMerchantIdAndActive(String merchantId, boolean active);

    @Query(value = """
            SELECT * FROM webhook_endpoints
            WHERE active = TRUE
            AND :eventType = ANY(events)
            """, nativeQuery = true)
    List<WebhookEndpointEntity> findActiveEndpointsForEvent(@Param("eventType") String eventType);
}
