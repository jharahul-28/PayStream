package com.paystream.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end auth security tests.
 * Verifies lockout, token lifecycle, and refresh rotation.
 */
class AuthSecurityE2ETest extends BaseE2ETest {

    private static final String PASSWORD = "Test@12345";
    private static final String WRONG_PASSWORD = "WrongPass@999";

    // -----------------------------------------------------------------------
    // Account lockout after 5 failed attempts
    // -----------------------------------------------------------------------

    @Test
    void lockout_after5FailedAttempts_returns423() {
        String email = "lockout+" + System.currentTimeMillis() + "@paystream.test";
        given().contentType("application/json")
                .body(Map.of("email", email, "password", PASSWORD, "fullName", "Lockout Test", "role", "CUSTOMER"))
                .post("/api/v1/auth/register").then().statusCode(201);

        // 5 wrong attempts
        for (int i = 0; i < 5; i++) {
            given().contentType("application/json")
                    .body(Map.of("email", email, "password", WRONG_PASSWORD))
                    .post("/api/v1/auth/login")
                    .then().statusCode(401);
        }

        // 6th attempt — account should be locked
        given().contentType("application/json")
                .body(Map.of("email", email, "password", WRONG_PASSWORD))
                .post("/api/v1/auth/login")
                .then().statusCode(423);
    }

    // -----------------------------------------------------------------------
    // Token lifecycle: logout blocks the access token
    // -----------------------------------------------------------------------

    @Test
    void tokenLifecycle_afterLogout_accessTokenInvalid() {
        String email = "lifecycle+" + System.currentTimeMillis() + "@paystream.test";
        given().contentType("application/json")
                .body(Map.of("email", email, "password", PASSWORD, "fullName", "Lifecycle Test", "role", "CUSTOMER"))
                .post("/api/v1/auth/register").then().statusCode(201);

        // Login
        var loginResponse = given().contentType("application/json")
                .body(Map.of("email", email, "password", PASSWORD))
                .post("/api/v1/auth/login").then().statusCode(200).extract().response();

        String accessToken  = loginResponse.jsonPath().getString("data.accessToken");
        String refreshToken = loginResponse.jsonPath().getString("data.refreshToken");

        // Verify token works
        given().header("Authorization", "Bearer " + accessToken)
                .get("/api/v1/auth/me").then().statusCode(200);

        // Logout
        given().contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("refreshToken", refreshToken))
                .post("/api/v1/auth/logout").then().statusCode(200);

        // Access token should now be rejected (blocklisted)
        given().header("Authorization", "Bearer " + accessToken)
                .get("/api/v1/auth/me").then().statusCode(401);

        // Refresh token should also be rejected (revoked on logout)
        given().contentType("application/json")
                .body(Map.of("refreshToken", refreshToken))
                .post("/api/v1/auth/refresh").then().statusCode(401);
    }

    // -----------------------------------------------------------------------
    // Refresh rotation: old refresh token rejected after use
    // -----------------------------------------------------------------------

    @Test
    void refreshRotation_oldRefreshTokenRejectedAfterUse() {
        String email = "rotation+" + System.currentTimeMillis() + "@paystream.test";
        given().contentType("application/json")
                .body(Map.of("email", email, "password", PASSWORD, "fullName", "Rotation Test", "role", "CUSTOMER"))
                .post("/api/v1/auth/register").then().statusCode(201);

        var loginResponse = given().contentType("application/json")
                .body(Map.of("email", email, "password", PASSWORD))
                .post("/api/v1/auth/login").then().statusCode(200).extract().response();

        String refreshToken1 = loginResponse.jsonPath().getString("data.refreshToken");

        // Refresh — should rotate token
        var refreshResponse = given().contentType("application/json")
                .body(Map.of("refreshToken", refreshToken1))
                .post("/api/v1/auth/refresh").then().statusCode(200).extract().response();

        String refreshToken2 = refreshResponse.jsonPath().getString("data.refreshToken");
        assertThat(refreshToken2).isNotBlank().isNotEqualTo(refreshToken1);

        // Old refresh token should be revoked
        given().contentType("application/json")
                .body(Map.of("refreshToken", refreshToken1))
                .post("/api/v1/auth/refresh").then().statusCode(401);
    }

    // -----------------------------------------------------------------------
    // Unauthenticated access to protected endpoint
    // -----------------------------------------------------------------------

    @Test
    void unauthenticatedAccess_returns401() {
        given()
                .get("/api/v1/wallets/my")
                .then().statusCode(401);
    }

    // -----------------------------------------------------------------------
    // Invalid JWT returns 401
    // -----------------------------------------------------------------------

    @Test
    void invalidJwt_returns401() {
        given()
                .header("Authorization", "Bearer this.is.not.a.valid.jwt")
                .get("/api/v1/auth/me")
                .then().statusCode(401);
    }

    // -----------------------------------------------------------------------
    // Role enforcement: CUSTOMER cannot access FINANCE_OPS endpoint
    // -----------------------------------------------------------------------

    @Test
    void customerRole_cannotTriggerSettlement_returns403() {
        String email = "customerrole+" + System.currentTimeMillis() + "@paystream.test";
        given().contentType("application/json")
                .body(Map.of("email", email, "password", PASSWORD, "fullName", "Customer", "role", "CUSTOMER"))
                .post("/api/v1/auth/register").then().statusCode(201);

        String token = given().contentType("application/json")
                .body(Map.of("email", email, "password", PASSWORD))
                .post("/api/v1/auth/login").then().statusCode(200)
                .extract().jsonPath().getString("data.accessToken");

        given().contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .post("/api/v1/settlements/trigger")
                .then().statusCode(403);
    }
}
