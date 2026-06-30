package com.paystream.wallet.infrastructure;

import com.paystream.wallet.domain.model.WalletStatus;
import com.paystream.wallet.infrastructure.persistence.entity.WalletEntity;
import com.paystream.wallet.infrastructure.persistence.repository.WalletJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test — uses a real PostgreSQL via Testcontainers.
 * Validates Flyway migrations run correctly and JPA mappings are correct.
 */
@DataJpaTest
@Testcontainers
@DisplayName("WalletJpaRepository Integration")
class WalletRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wallet_db")
            .withUsername("paystream")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private WalletJpaRepository walletJpaRepository;

    @Test
    @DisplayName("Save and retrieve wallet by userId and currency")
    void saveAndFind() {
        WalletEntity entity = new WalletEntity("W01", "U01", 0L, "INR", WalletStatus.ACTIVE);
        walletJpaRepository.save(entity);

        var found = walletJpaRepository.findByUserIdAndCurrency("U01", "INR");
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo("U01");
        assertThat(found.get().getCurrency()).isEqualTo("INR");
        assertThat(found.get().getBalance()).isZero();
    }

    @Test
    @DisplayName("Duplicate user+currency combination is rejected by DB constraint")
    void duplicate_userCurrency_rejected() {
        walletJpaRepository.save(new WalletEntity("W02", "U02", 0L, "INR", WalletStatus.ACTIVE));
        walletJpaRepository.flush();

        WalletEntity duplicate = new WalletEntity("W03", "U02", 0L, "INR", WalletStatus.ACTIVE);
        assertThatThrownBy(() -> {
            walletJpaRepository.save(duplicate);
            walletJpaRepository.flush();
        }).isInstanceOf(Exception.class);
    }
}
