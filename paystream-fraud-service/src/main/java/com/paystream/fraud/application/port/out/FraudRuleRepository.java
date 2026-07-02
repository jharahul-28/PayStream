package com.paystream.fraud.application.port.out;

import com.paystream.fraud.domain.model.FraudRule;

import java.util.List;

public interface FraudRuleRepository {
    List<FraudRule> findAllEnabled();
}
