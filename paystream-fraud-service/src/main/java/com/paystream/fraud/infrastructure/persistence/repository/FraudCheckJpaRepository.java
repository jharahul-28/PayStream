package com.paystream.fraud.infrastructure.persistence.repository;

import com.paystream.fraud.infrastructure.persistence.entity.FraudCheckEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FraudCheckJpaRepository extends JpaRepository<FraudCheckEntity, String> {

    Optional<FraudCheckEntity> findByPaymentId(String paymentId);

    @Query("SELECT f FROM FraudCheckEntity f WHERE f.userId = :userId ORDER BY f.createdAt DESC")
    List<FraudCheckEntity> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT f FROM FraudCheckEntity f WHERE f.aiProcessed = FALSE ORDER BY f.createdAt ASC")
    List<FraudCheckEntity> findAiPendingOrderByCreatedAt(Pageable pageable);
}
