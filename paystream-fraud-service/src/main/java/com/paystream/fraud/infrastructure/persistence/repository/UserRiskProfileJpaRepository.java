package com.paystream.fraud.infrastructure.persistence.repository;

import com.paystream.fraud.infrastructure.persistence.entity.UserRiskProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRiskProfileJpaRepository extends JpaRepository<UserRiskProfileEntity, String> {}
