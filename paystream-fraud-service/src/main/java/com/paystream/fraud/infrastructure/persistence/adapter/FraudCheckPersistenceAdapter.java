package com.paystream.fraud.infrastructure.persistence.adapter;

import com.paystream.fraud.application.port.out.FraudCheckRepository;
import com.paystream.fraud.domain.model.FraudCheck;
import com.paystream.fraud.domain.model.FraudDecision;
import com.paystream.fraud.infrastructure.persistence.entity.FraudCheckEntity;
import com.paystream.fraud.infrastructure.persistence.repository.FraudCheckJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class FraudCheckPersistenceAdapter implements FraudCheckRepository {

    private final FraudCheckJpaRepository jpaRepository;

    public FraudCheckPersistenceAdapter(FraudCheckJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public FraudCheck save(FraudCheck fraudCheck) {
        FraudCheckEntity entity = toEntity(fraudCheck);
        jpaRepository.save(entity);
        return fraudCheck;
    }

    @Override
    public Optional<FraudCheck> findByPaymentId(String paymentId) {
        return jpaRepository.findByPaymentId(paymentId).map(this::toDomain);
    }

    @Override
    public List<FraudCheck> findByUserId(String userId, int limit) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<FraudCheck> findAiPendingChecks(int limit) {
        return jpaRepository.findAiPendingOrderByCreatedAt(PageRequest.of(0, limit))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void updateAiEnrichment(FraudCheck fraudCheck) {
        jpaRepository.findById(fraudCheck.getId()).ifPresent(entity -> {
            entity.setAiNarrative(fraudCheck.getAiNarrative());
            entity.setAiRiskScore(fraudCheck.getAiRiskScore() == null ? null : fraudCheck.getAiRiskScore().shortValue());
            entity.setAiConfidence(fraudCheck.getAiConfidence() == null ? null
                    : BigDecimal.valueOf(fraudCheck.getAiConfidence()));
            entity.setAiProcessed(fraudCheck.isAiProcessed());
            entity.setAiProcessingError(fraudCheck.getAiProcessingError());
            jpaRepository.save(entity);
        });
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private FraudCheckEntity toEntity(FraudCheck d) {
        return new FraudCheckEntity(
                d.getId(), d.getPaymentId(), d.getUserId(),
                (short) d.getRiskScore(), d.getDecision().name(),
                d.getFlags(), d.getRuleVersion(), d.getProcessingTimeMs(), d.getCreatedAt()
        );
    }

    private FraudCheck toDomain(FraudCheckEntity e) {
        FraudCheck fc = new FraudCheck(
                e.getId(), e.getPaymentId(), e.getUserId(),
                e.getRiskScore(), FraudDecision.valueOf(e.getDecision()),
                e.getFlags(), e.getRuleVersion(), e.getProcessingTimeMs(), e.getCreatedAt()
        );
        if (e.isAiProcessed() && e.getAiNarrative() != null) {
            fc.enrichWithAi(
                    e.getAiNarrative(),
                    e.getAiRiskScore() == null ? 0 : e.getAiRiskScore(),
                    e.getAiConfidence() == null ? 0.0 : e.getAiConfidence().doubleValue()
            );
        } else if (e.getAiProcessingError() != null) {
            fc.markAiError(e.getAiProcessingError());
        }
        return fc;
    }
}
