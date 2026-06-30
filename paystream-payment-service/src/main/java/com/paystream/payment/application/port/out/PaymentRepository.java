package com.paystream.payment.application.port.out;

import com.paystream.payment.domain.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/** Output port — payment persistence contract. */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(String id);

    Optional<Payment> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    Page<Payment> findByUserId(String userId, Pageable pageable);

    Page<Payment> findAll(Pageable pageable);
}
