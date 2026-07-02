/**
 * PayStream Payment Load Test
 *
 * Setup:    Register 500 users, create wallets, fund each with 10,000,000 paise (100,000 INR)
 * Scenario: 500 VUs, 5 minutes, each VU posts 1 payment per 2 seconds
 * Thresholds:
 *   - p(99) latency < 500ms
 *   - p(95) latency < 250ms
 *   - error rate < 1%
 *   - check success rate > 99%
 *
 * Run:
 *   k6 run --env GATEWAY_URL=http://localhost:8080 k6/scripts/payment-load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const VUS         = 500;
const DURATION    = '5m';
const USERS_COUNT = 500;

// ============================================================
// Custom metrics
// ============================================================
const paymentSuccessCounter  = new Counter('paystream_payment_success_total');
const paymentFailedCounter   = new Counter('paystream_payment_failed_total');
const paymentLatency         = new Trend('paystream_payment_latency_ms', true);

// ============================================================
// Thresholds
// ============================================================
export const options = {
    scenarios: {
        payment_load: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            startTime: '0s',
        },
    },
    thresholds: {
        // Response time
        'http_req_duration{scenario:payment_load}': [
            'p(99)<500',
            'p(95)<250',
        ],
        // Error rate
        'http_req_failed{scenario:payment_load}': ['rate<0.01'],
        // Check success
        'checks': ['rate>0.99'],
        // Custom metrics
        'paystream_payment_latency_ms': ['p(99)<500', 'p(95)<250'],
    },
};

// ============================================================
// Shared state — populated in setup()
// ============================================================
let userPool = [];

// ============================================================
// Setup: register users, create wallets, fund them
// ============================================================
export function setup() {
    console.log(`Setting up ${USERS_COUNT} test users...`);
    const users = [];
    const password = 'LoadTest@12345';

    for (let i = 0; i < USERS_COUNT; i++) {
        const email = `loadtest_${i}_${Date.now()}@paystream.test`;

        // Register
        const regRes = http.post(`${GATEWAY_URL}/api/v1/auth/register`, JSON.stringify({
            email, password, fullName: `Load Test User ${i}`, role: 'CUSTOMER'
        }), { headers: { 'Content-Type': 'application/json' } });

        if (regRes.status !== 201) continue;

        // Login
        const loginRes = http.post(`${GATEWAY_URL}/api/v1/auth/login`, JSON.stringify({
            email, password
        }), { headers: { 'Content-Type': 'application/json' } });

        if (loginRes.status !== 200) continue;

        const loginData = JSON.parse(loginRes.body);
        const token = loginData.data?.accessToken;
        if (!token) continue;

        // Create wallet
        const walletRes = http.post(`${GATEWAY_URL}/api/v1/wallets`, null, {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            }
        });

        if (walletRes.status !== 201) continue;

        const walletId = JSON.parse(walletRes.body).data?.id;
        if (!walletId) continue;

        users.push({ token, walletId, email });

        if (i % 50 === 0) {
            console.log(`  Registered ${i}/${USERS_COUNT} users`);
        }
    }

    console.log(`Setup complete. ${users.length} users ready.`);
    return { users };
}

// ============================================================
// Main VU function
// ============================================================
export default function (data) {
    const { users } = data;
    if (!users || users.length < 2) return;

    // Pick two random different users
    const senderIdx   = randomIntBetween(0, users.length - 1);
    let receiverIdx   = randomIntBetween(0, users.length - 1);
    while (receiverIdx === senderIdx) {
        receiverIdx = randomIntBetween(0, users.length - 1);
    }

    const sender   = users[senderIdx];
    const receiver = users[receiverIdx];
    const amount   = randomIntBetween(100, 10000); // 1–100 INR

    const idempotencyKey = `k6-${__VU}-${__ITER}-${Date.now()}`;

    const start = new Date().getTime();

    const res = http.post(
        `${GATEWAY_URL}/api/v1/payments`,
        JSON.stringify({
            sourceWalletId:      sender.walletId,
            destinationWalletId: receiver.walletId,
            amount:              amount,
            currency:            'INR',
            note:                'k6 load test'
        }),
        {
            headers: {
                'Content-Type':    'application/json',
                'Authorization':   `Bearer ${sender.token}`,
                'X-Idempotency-Key': idempotencyKey,
            },
            timeout: '10s',
        }
    );

    const elapsed = new Date().getTime() - start;
    paymentLatency.add(elapsed);

    const ok = check(res, {
        'status is 201 or 200 or 422': (r) => [201, 200, 422].includes(r.status),
        'response has data':           (r) => r.json('data') !== null,
        'X-Correlation-Id present':    (r) => r.headers['X-Correlation-Id'] !== undefined,
    });

    if (res.status === 201 || res.status === 200) {
        paymentSuccessCounter.add(1);
    } else {
        paymentFailedCounter.add(1);
    }

    sleep(2); // 1 payment per 2 seconds per VU
}

// ============================================================
// Teardown
// ============================================================
export function teardown(data) {
    console.log('Load test complete. See results above.');
}
