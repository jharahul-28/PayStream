package com.paystream.fraud.infrastructure.persistence.adapter;

import com.paystream.fraud.application.port.out.FraudRuleRepository;
import com.paystream.fraud.domain.model.FraudRule;
import com.paystream.fraud.infrastructure.persistence.repository.FraudRuleJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class FraudRulePersistenceAdapter implements FraudRuleRepository {

    private final FraudRuleJpaRepository jpaRepository;

    public FraudRulePersistenceAdapter(FraudRuleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<FraudRule> findAllEnabled() {
        return jpaRepository.findByEnabledTrue().stream()
                .map(e -> new FraudRule(e.getId(), e.getRuleCode(), e.getRuleName(),
                        e.getFlagName(), e.getWeight(), e.isEnabled(), e.getVersion()))
                .collect(Collectors.toList());
    }
}
