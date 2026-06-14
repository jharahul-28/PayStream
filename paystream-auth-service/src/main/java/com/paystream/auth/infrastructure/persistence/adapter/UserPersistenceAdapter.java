package com.paystream.auth.infrastructure.persistence.adapter;

import com.paystream.auth.application.port.out.UserRepository;
import com.paystream.auth.domain.model.User;
import com.paystream.auth.domain.model.UserStatus;
import com.paystream.auth.infrastructure.persistence.entity.UserEntity;
import com.paystream.auth.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Translates between the domain {@link User} and the JPA {@link UserEntity}.
 * Domain objects never touch the JPA layer; this adapter is the only bridge.
 */
@Component
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserPersistenceAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity = jpaRepository.findById(user.getId())
                .orElseGet(() -> new UserEntity(
                        user.getId(), user.getEmail(), user.getPasswordHash(),
                        user.getFullName(), user.getRole(), UserStatus.ACTIVE));

        // Sync mutable state from domain object to entity
        entity.setStatus(user.getStatus());
        entity.setFailedLoginAttempts(user.getFailedLoginAttempts());
        entity.setLockedUntil(user.getLockedUntil());

        UserEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    private User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.getRole(),
                entity.getStatus(),
                entity.getFailedLoginAttempts(),
                entity.getLockedUntil(),
                entity.getVersion() != null ? entity.getVersion() : 0
        );
    }
}
