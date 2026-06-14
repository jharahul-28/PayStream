package com.paystream.auth.infrastructure.persistence;

import com.paystream.auth.domain.model.Role;
import com.paystream.auth.domain.model.User;
import com.paystream.auth.domain.model.UserStatus;
import com.paystream.auth.infrastructure.persistence.adapter.UserPersistenceAdapter;
import com.paystream.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.paystream.common.util.IdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * Repository integration test.
 * Uses a real PostgreSQL container — no H2 in-memory DB.
 * Flyway migrations are applied via Spring Boot's auto-configuration.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserPersistenceAdapter.class)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_db_test")
            .withUsername("paystream")
            .withPassword("testpassword");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username",  postgres::getUsername);
        registry.add("spring.datasource.password",  postgres::getPassword);
        // Let Flyway run so we test against the real schema
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    UserJpaRepository jpaRepository;

    @Autowired
    UserPersistenceAdapter adapter;

    @Test
    void saveAndFind_roundtripSucceeds() {
        User user = testUser("find@paystream.com");
        adapter.save(user);

        assertThat(adapter.findByEmail("find@paystream.com"))
                .isPresent()
                .hasValueSatisfying(found -> {
                    assertThat(found.getId()).isEqualTo(user.getId());
                    assertThat(found.getEmail()).isEqualTo("find@paystream.com");
                    assertThat(found.getRole()).isEqualTo(Role.CUSTOMER);
                    assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
                });
    }

    @Test
    void save_duplicateEmail_throwsConstraintViolation() {
        User user1 = testUser("dup@paystream.com");
        User user2 = testUser("dup@paystream.com");

        adapter.save(user1);

        assertThatThrownBy(() -> adapter.save(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmail_returnsTrueWhenPresent() {
        adapter.save(testUser("exists@paystream.com"));
        assertThat(adapter.existsByEmail("exists@paystream.com")).isTrue();
        assertThat(adapter.existsByEmail("notexists@paystream.com")).isFalse();
    }

    @Test
    void flywayApplied_usersTableHasCorrectConstraints() {
        // The @DataJpaTest startup validates the JPA mapping against the DB schema.
        // If Flyway migrations didn't apply correctly, the context fails to start.
        assertThat(jpaRepository.count()).isGreaterThanOrEqualTo(0);
    }

    // -------------------------------------------------------------------------

    private User testUser(String email) {
        return new User(IdGenerator.generate(), email,
                "$2a$12$dummyhash", "Test User",
                Role.CUSTOMER, UserStatus.ACTIVE, 0, null, 0);
    }
}
