package com.paystream.settlement.infrastructure.persistence.repository;

import com.paystream.settlement.infrastructure.persistence.entity.SettlementItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemJpaRepository extends JpaRepository<SettlementItemEntity, String> {

    Page<SettlementItemEntity> findByBatchIdAndStatus(String batchId, String status, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(i.amount), 0) FROM SettlementItemEntity i WHERE i.batch.id = :batchId")
    long sumAmountByBatchId(@org.springframework.data.repository.query.Param("batchId") String batchId);
}
