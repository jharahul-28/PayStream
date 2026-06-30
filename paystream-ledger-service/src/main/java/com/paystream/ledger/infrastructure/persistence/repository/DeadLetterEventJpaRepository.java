package com.paystream.ledger.infrastructure.persistence.repository;

import com.paystream.ledger.infrastructure.persistence.entity.DeadLetterEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterEventJpaRepository extends JpaRepository<DeadLetterEventEntity, String> {}
