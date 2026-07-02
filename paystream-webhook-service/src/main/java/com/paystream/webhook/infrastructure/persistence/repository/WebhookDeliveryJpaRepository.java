package com.paystream.webhook.infrastructure.persistence.repository;

import com.paystream.webhook.infrastructure.persistence.entity.WebhookDeliveryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryJpaRepository extends JpaRepository<WebhookDeliveryEntity, String> {

    Page<WebhookDeliveryEntity> findByEndpointId(String endpointId, Pageable pageable);
}
