package com.paystream.fraud.application.port.out;

import com.paystream.fraud.domain.model.UserRiskProfile;

import java.util.Optional;

public interface UserRiskProfileRepository {
    Optional<UserRiskProfile> findByUserId(String userId);
    UserRiskProfile save(UserRiskProfile profile);
}
