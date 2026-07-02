package com.paystream.notification.infrastructure.persistence.repository;

import com.paystream.notification.infrastructure.persistence.entity.NotificationProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationProcessedEventJpaRepository
        extends JpaRepository<NotificationProcessedEventEntity, String> {}
