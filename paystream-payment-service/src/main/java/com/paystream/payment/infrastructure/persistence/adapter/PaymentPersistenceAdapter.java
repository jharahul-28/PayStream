package com.paystream.payment.infrastructure.persistence.adapter;

import com.paystream.payment.application.port.out.PaymentRepository;
import com.paystream.payment.domain.model.Payment;
import com.paystream.payment.domain.model.PaymentStatus;
import com.paystream.payment.infrastructure.persistence.entity.PaymentEntity;
import com.paystream.payment.infrastructure.persistence.repository.PaymentJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Translates between the domain {@link Payment} and the JPA {@link PaymentEntity}. */
@Component
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentPersistenceAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Payment save(Payment payment) {
        PaymentEntity entity = jpaRepository.findById(payment.getId())
                .orElseGet(() -> new PaymentEntity(
                        payment.getId(), payment.getUserId(), payment.getIdempotencyKey(),
                        payment.getSourceWalletId(), payment.getDestinationWalletId(),
                        payment.getAmount(), payment.getCurrency(), payment.getStatus(), payment.getNote()
                ));

        entity.setStatus(payment.getStatus());
        entity.setFailureReason(payment.getFailureReason());
        entity.setFailureCode(payment.getFailureCode());

        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey) {
        return jpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Payment> findByUserId(String userId, Pageable pageable) {
        return jpaRepository.findByUserId(userId, pageable).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Payment> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    private Payment toDomain(PaymentEntity e) {
        return new Payment(
                e.getId(), e.getUserId(), e.getIdempotencyKey(),
                e.getSourceWalletId(), e.getDestinationWalletId(),
                e.getAmount(), e.getCurrency(), e.getStatus(),
                e.getFailureReason(), e.getFailureCode(), e.getNote(),
                e.getVersion() != null ? e.getVersion() : 0,
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
