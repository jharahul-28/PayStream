package com.paystream.auth.security.jwt;

import com.paystream.auth.domain.model.Role;
import com.paystream.auth.domain.model.User;
import com.paystream.auth.domain.model.UserStatus;
import com.paystream.common.exception.AuthException;
import com.paystream.common.util.IdGenerator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        properties = new JwtProperties();
        properties.setAccessTokenTtlSeconds(900);
        properties.setRefreshTokenTtlSeconds(604800);

        jwtService = new JwtService(keyPair.getPrivate(), keyPair.getPublic(), properties);
    }

    @Test
    void generateAccessToken_producesValidToken() {
        User user = testUser();
        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();
        Claims claims = jwtService.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo(user.getId());
        assertThat(claims.get("email", String.class)).isEqualTo(user.getEmail());
        assertThat(claims.get("role", String.class)).isEqualTo(user.getRole().name());
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void extractUserId_matchesOriginalUserId() {
        User user = testUser();
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
    }

    @Test
    void extractJti_returnsNonBlankId() {
        String token = jwtService.generateAccessToken(testUser());
        assertThat(jwtService.extractJti(token)).isNotBlank();
    }

    @Test
    void validateToken_throwsExpiredOnExpiredToken() throws Exception {
        // Use TTL of -1 to force immediate expiry
        properties.setAccessTokenTtlSeconds(-1);
        String expiredToken = jwtService.generateAccessToken(testUser());

        assertThatThrownBy(() -> jwtService.validateToken(expiredToken))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateToken_throwsOnTamperedToken() {
        String token   = jwtService.generateAccessToken(testUser());
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.validateToken(tampered))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void validateToken_throwsOnMalformedToken() {
        assertThatThrownBy(() -> jwtService.validateToken("not.a.jwt"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void generateRefreshToken_producesUniqueValues() {
        String r1 = jwtService.generateRefreshToken();
        String r2 = jwtService.generateRefreshToken();
        assertThat(r1).isNotEqualTo(r2);
        assertThat(r1).hasSize(43); // 32 bytes base64url no-padding
    }

    @Test
    void getRemainingTtlSeconds_isWithinExpectedRange() {
        String token = jwtService.generateAccessToken(testUser());
        long ttl = jwtService.getRemainingTtlSeconds(token);
        assertThat(ttl).isBetween(890L, 900L);
    }

    // -------------------------------------------------------------------------

    private User testUser() {
        return new User(IdGenerator.generate(), "test@paystream.com",
                "$2a$12$hash", "Test User", Role.CUSTOMER,
                UserStatus.ACTIVE, 0, null, 0);
    }
}
