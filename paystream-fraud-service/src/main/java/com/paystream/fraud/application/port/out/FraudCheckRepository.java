package com.paystream.fraud.application.port.out;

import com.paystream.fraud.domain.model.FraudCheck;

import java.util.List;
import java.util.Optional;

public interface FraudCheckRepository {
    FraudCheck save(FraudCheck fraudCheck);
    Optional<FraudCheck> findByPaymentId(String paymentId);
    List<FraudCheck> findByUserId(String userId, int limit);
    List<FraudCheck> findAiPendingChecks(int limit);
    void updateAiEnrichment(FraudCheck fraudCheck);
}
