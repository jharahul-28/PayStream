package com.paystream.notification.infrastructure.persistence.repository;

import com.paystream.notification.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, String> {}
