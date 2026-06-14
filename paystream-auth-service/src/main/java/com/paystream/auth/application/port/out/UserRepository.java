package com.paystream.auth.application.port.out;

import com.paystream.auth.domain.model.User;

import java.util.Optional;

/** Output port — user persistence. Implemented by the JPA adapter. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(String id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
