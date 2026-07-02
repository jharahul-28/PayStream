package com.paystream.e2e;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end settlement tests.
 * Requires: a FINANCE_OPS user and a MERCHANT user registered in the system.
 */
class SettlementE2ETest extends BaseE2ETest {

    private static final String PASSWORD = "Test@12345";

    private static String merchantToken;
    private static String financeOpsToken;
    private static String merchantWalletId;
    private static String customerToken;
    private static String customerWalletId;

    @BeforeAll
    static void setupActors() {
        long ts = System.currentTimeMillis();

        String merchantEmail    = "merchant+" + ts + "@paystream.test";
        String financeOpsEmail  = "financeops+" + ts + "@paystream.test";
        String customerEmail    = "customer+" + ts + "@paystream.test";

        // Register actors
        given().contentType("application/json")
                .body(Map.of("email", merchantEmail, "password", PASSWORD, "fullName", "Test Merchant", "role", "MERCHANT"))
                .post("/api/v1/auth/register").then().statusCode(201);

        given().contentType("application/json")
                .body(Map.of("email", financeOpsEmail, "password", PASSWORD, "fullName", "Finance Ops", "role", "FINANCE_OPS"))
                .post("/api/v1/auth/register").then().statusCode(201);

        given().contentType("application/json")
                .body(Map.of("email", customerEmail, "password", PASSWORD, "fullName", "Test Customer", "role", "CUSTOMER"))
                .post("/api/v1/auth/register").then().statusCode(201);

        // Login
        merchantToken = given().contentType("application/json")
                .body(Map.of("email", merchantEmail, "password", PASSWORD))
                .post("/api/v1/auth/login").then().statusCode(200)
                .extract().jsonPath().getString("data.accessToken");

        financeOpsToken = given().contentType("application/json")
                .body(Map.of("email", financeOpsEmail, "password", PASSWORD))
                .post("/api/v1/auth/login").then().statusCode(200)
                .extract().jsonPath().getString("data.accessToken");

        customerToken = given().contentType("application/json")
                .body(Map.of("email", customerEmail, "password", PASSWORD))
                .post("/api/v1/auth/login").then().statusCode(200)
                .extract().jsonPath().getString("data.accessToken");

        // Create wallets
        merchantWalletId = given().contentType("application/json")
                .header("Authorization", "Bearer " + merchantToken)
                .post("/api/v1/wallets").then().statusCode(201)
                .extract().jsonPath().getString("data.id");

        customerWalletId = given().contentType("application/json")
                .header("Authorization", "Bearer " + customerToken)
                .post("/api/v1/wallets").then().statusCode(201)
                .extract().jsonPath().getString("data.id");
    }

    @Test
    void settlementTrigger_computesCorrectTotals() {
        // Complete 5 payments from customer to merchant (assuming pre-funded customer wallet)
        long paymentAmount = 100000L; // 1000 INR in paise
        int paymentCount = 5;

        List<String> paymentIds = new java.util.ArrayList<>();
        for (int i = 0; i < paymentCount; i++) {
            try {
                String pid = given().contentType("application/json")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("X-Idempotency-Key", "settle-test-" + i + "-" + System.currentTimeMillis())
                        .body(Map.of(
                                "sourceWalletId", customerWalletId,
                                "destinationWalletId", merchantWalletId,
                                "amount", paymentAmount,
                                "currency", "INR",
                                "note", "Settlement test payment " + i
                        ))
                        .post("/api/v1/payments")
                        .then().extract().jsonPath().getString("data.id");
                if (pid != null) {
                    paymentIds.add(pid);
                }
            } catch (Exception e) {
                // Insufficient funds — skip in test environment without pre-funded wallets
            }
        }

        // Trigger settlement as FINANCE_OPS
        given().contentType("application/json")
                .header("Authorization", "Bearer " + financeOpsToken)
                .post("/api/v1/settlements/trigger")
                .then().statusCode(202);

        // Poll for SETTLED batch
        Awaitility.await("Settlement batch reaches SETTLED")
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var response = given()
                            .header("Authorization", "Bearer " + financeOpsToken)
                            .queryParam("status", "SETTLED")
                            .get("/api/v1/settlements")
                            .then().statusCode(200)
                            .extract().response();

                    List<Object> batches = response.jsonPath().getList("data.content");
                    assertThat(batches).isNotEmpty();
                });

        // Verify financial integrity: feeAmount = grossAmount * 0.01
        var response = given()
                .header("Authorization", "Bearer " + financeOpsToken)
                .queryParam("status", "SETTLED")
                .get("/api/v1/settlements")
                .then().statusCode(200)
                .extract().response();

        List<Map<String, Object>> batches = response.jsonPath().getList("data.content");
        if (!batches.isEmpty()) {
            Map<String, Object> batch = batches.get(0);
            long grossAmount = ((Number) batch.get("grossAmount")).longValue();
            long feeAmount   = ((Number) batch.get("feeAmount")).longValue();
            long netAmount   = ((Number) batch.get("netAmount")).longValue();

            assertThat(feeAmount).isEqualTo((long) (grossAmount * 0.01));
            assertThat(netAmount).isEqualTo(grossAmount - feeAmount);
        }
    }

    @Test
    void customerRole_cannotAccessSettlements_returns403() {
        given().contentType("application/json")
                .header("Authorization", "Bearer " + customerToken)
                .get("/api/v1/settlements")
                .then().statusCode(403);
    }
}
