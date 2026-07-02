package com.paystream.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for all E2E tests.
 * Tests call the live API Gateway — no mocking of downstream services.
 */
public abstract class BaseE2ETest {

    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static String GATEWAY_URL;

    @BeforeAll
    static void configureRestAssured() {
        GATEWAY_URL = System.getProperty("test.gateway.url", "http://localhost:8080");
        RestAssured.baseURI = GATEWAY_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        Awaitility.setDefaultTimeout(30, TimeUnit.SECONDS);
        Awaitility.setDefaultPollInterval(500, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------------------
    // Fluent helpers
    // -------------------------------------------------------------------------

    protected RequestSpecification authenticated(String accessToken) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken);
    }

    protected String register(String email, String password, String fullName, String role) {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", email,
                        "password", password,
                        "fullName", fullName,
                        "role", role
                ))
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .extract().response();

        return extractData(response, "id");
    }

    protected String login(String email, String password) {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract().response();

        return extractData(response, "accessToken");
    }

    protected String createWallet(String accessToken) {
        Response response = authenticated(accessToken)
                .post("/api/v1/wallets")
                .then()
                .statusCode(201)
                .extract().response();

        return extractData(response, "id");
    }

    protected String initiatePayment(String accessToken, String sourceWalletId, String destWalletId,
                                     long amount, String idempotencyKey) {
        Response response = authenticated(accessToken)
                .header("X-Idempotency-Key", idempotencyKey)
                .body(Map.of(
                        "sourceWalletId", sourceWalletId,
                        "destinationWalletId", destWalletId,
                        "amount", amount,
                        "currency", "INR",
                        "note", "E2E test payment"
                ))
                .post("/api/v1/payments")
                .then()
                .statusCode(201)
                .extract().response();

        return extractData(response, "id");
    }

    protected void waitForPaymentStatus(String accessToken, String paymentId, String expectedStatus) {
        Awaitility.await("Payment " + paymentId + " reaches " + expectedStatus)
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    String status = authenticated(accessToken)
                            .get("/api/v1/payments/" + paymentId)
                            .then()
                            .statusCode(200)
                            .extract()
                            .jsonPath()
                            .getString("data.status");
                    assertThat(status).isEqualTo(expectedStatus);
                });
    }

    protected long getWalletBalance(String accessToken) {
        return authenticated(accessToken)
                .get("/api/v1/wallets/my")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getLong("data.balance");
    }

    protected String uniqueEmail() {
        return "e2e+" + UUID.randomUUID().toString().substring(0, 8) + "@paystream.test";
    }

    protected String uniqueKey() {
        return "e2e-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private String extractData(Response response, String field) {
        try {
            JsonNode root = MAPPER.readTree(response.getBody().asString());
            JsonNode data = root.path("data");
            return data.path(field).asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract field '" + field + "' from response: " + response.getBody().asString(), e);
        }
    }
}
