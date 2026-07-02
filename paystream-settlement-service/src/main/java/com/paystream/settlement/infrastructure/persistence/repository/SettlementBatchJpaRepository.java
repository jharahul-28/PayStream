package com.paystream.settlement.infrastructure.persistence.repository;

import com.paystream.settlement.infrastructure.persistence.entity.SettlementBatchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SettlementBatchJpaRepository extends JpaRepository<SettlementBatchEntity, String> {

    @Query("SELECT b FROM SettlementBatchEntity b WHERE b.status = 'PENDING' AND b.settlementDate <= :today")
    List<SettlementBatchEntity> findPendingBatchesDue(@Param("today") LocalDate today);

    Page<SettlementBatchEntity> findByMerchantId(String merchantId, Pageable pageable);

    @Query("SELECT b FROM SettlementBatchEntity b WHERE b.status = 'SETTLED' AND b.reconciled = FALSE AND b.settledAt >= :since")
    List<SettlementBatchEntity> findSettledNotReconciled(
            @Param("since") java.time.Instant since);
}
