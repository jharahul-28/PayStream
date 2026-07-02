package com.paystream.fraud.infrastructure.persistence.adapter;

import com.paystream.fraud.application.port.out.UserRiskProfileRepository;
import com.paystream.fraud.domain.model.UserRiskProfile;
import com.paystream.fraud.infrastructure.persistence.entity.UserRiskProfileEntity;
import com.paystream.fraud.infrastructure.persistence.repository.UserRiskProfileJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserRiskProfilePersistenceAdapter implements UserRiskProfileRepository {

    private final UserRiskProfileJpaRepository jpaRepository;

    public UserRiskProfilePersistenceAdapter(UserRiskProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UserRiskProfile> findByUserId(String userId) {
        return jpaRepository.findById(userId).map(this::toDomain);
    }

    @Override
    public UserRiskProfile save(UserRiskProfile profile) {
        UserRiskProfileEntity entity = new UserRiskProfileEntity(
                profile.getUserId(), profile.getAvgTransactionAmount(),
                profile.getTypicalHoursStart(), profile.getTypicalHoursEnd(),
                profile.getKnownDeviceIds(), profile.getKnownIpPrefixes(),
                profile.getChargebackCount30d(), profile.getTransactionCount30d(),
                profile.getLastUpdatedAt()
        );
        jpaRepository.save(entity);
        return profile;
    }

    private UserRiskProfile toDomain(UserRiskProfileEntity e) {
        return new UserRiskProfile(
                e.getUserId(), e.getAvgTransactionAmount(),
                e.getTypicalHoursStart(), e.getTypicalHoursEnd(),
                e.getKnownDeviceIds(), e.getKnownIpPrefixes(),
                e.getChargebackCount30d(), e.getTransactionCount30d(),
                e.getLastUpdatedAt()
        );
    }
}
