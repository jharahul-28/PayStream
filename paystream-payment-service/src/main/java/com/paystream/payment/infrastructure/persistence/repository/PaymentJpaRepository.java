package com.paystream.payment.infrastructure.persistence.repository;

import com.paystream.payment.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA interface for payment persistence. */
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {

    Optional<PaymentEntity> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    Page<PaymentEntity> findByUserId(String userId, Pageable pageable);
}
