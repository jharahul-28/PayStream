package com.paystream.settlement.infrastructure.persistence.repository;

import com.paystream.settlement.infrastructure.persistence.entity.ReconciliationAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationAlertJpaRepository extends JpaRepository<ReconciliationAlertEntity, String> {
    List<ReconciliationAlertEntity> findByBatchId(String batchId);
    List<ReconciliationAlertEntity> findByResolved(boolean resolved);
}
