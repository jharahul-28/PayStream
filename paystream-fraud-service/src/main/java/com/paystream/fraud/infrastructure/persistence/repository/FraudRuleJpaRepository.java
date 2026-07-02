package com.paystream.fraud.infrastructure.persistence.repository;

import com.paystream.fraud.infrastructure.persistence.entity.FraudRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudRuleJpaRepository extends JpaRepository<FraudRuleEntity, String> {
    List<FraudRuleEntity> findByEnabledTrue();
}
