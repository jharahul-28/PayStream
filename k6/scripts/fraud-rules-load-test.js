/**
 * PayStream Fraud Rules Engine Load Test
 *
 * Verifies the Stage 1 rules engine stays under 10ms p(99) under load.
 * 100 VUs, 2 minutes.
 *
 * Run:
 *   k6 run --env GATEWAY_URL=http://localhost:8080 \
 *          --env INTERNAL_KEY=dev-only-local-key \
 *          k6/scripts/fraud-rules-load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const GATEWAY_URL  = __ENV.GATEWAY_URL  || 'http://localhost:8080';
const INTERNAL_KEY = __ENV.INTERNAL_KEY || 'dev-only-local-key';

const fraudLatency         = new Trend('paystream_fraud_check_latency_ms', true);
const fraudBlockedCounter  = new Counter('paystream_fraud_blocked_total');
const fraudAllowedCounter  = new Counter('paystream_fraud_allowed_total');

export const options = {
    scenarios: {
        fraud_load: {
            executor: 'constant-vus',
            vus: 100,
            duration: '2m',
        },
    },
    thresholds: {
        'http_req_duration{scenario:fraud_load}': [
            'p(99)<10',   // Fraud Stage 1 SLA: < 10ms p99
            'p(95)<5',
        ],
        'http_req_failed{scenario:fraud_load}': ['rate<0.001'],
        'paystream_fraud_check_latency_ms': ['p(99)<10'],
    },
};

export default function () {
    const paymentId = uuidv4();
    const userId    = uuidv4();

    const payload = {
        paymentId:            paymentId,
        userId:               userId,
        amount:               Math.floor(Math.random() * 1000000),
        currency:             'INR',
        sourceWalletId:       uuidv4(),
        destinationWalletId:  uuidv4(),
        deviceId:             Math.random() > 0.5 ? 'known-device' : 'new-device-' + Math.random(),
        ipAddress:            '10.0.' + Math.floor(Math.random() * 255) + '.' + Math.floor(Math.random() * 255),
    };

    const start = new Date().getTime();

    const res = http.post(
        `${GATEWAY_URL}/api/v1/fraud/check`,
        JSON.stringify(payload),
        {
            headers: {
                'Content-Type':       'application/json',
                'X-Internal-Service-Key': INTERNAL_KEY,
            },
            timeout: '5s',
        }
    );

    const elapsed = new Date().getTime() - start;
    fraudLatency.add(elapsed);

    const ok = check(res, {
        'status is 200': (r) => r.status === 200,
        'decision present': (r) => {
            try {
                const body = JSON.parse(r.body);
                return ['ALLOW', 'BLOCK', 'REVIEW'].includes(body?.data?.decision);
            } catch {
                return false;
            }
        },
    });

    if (res.status === 200) {
        try {
            const body = JSON.parse(res.body);
            if (body?.data?.decision === 'BLOCK') {
                fraudBlockedCounter.add(1);
            } else {
                fraudAllowedCounter.add(1);
            }
        } catch (_) {}
    }

    sleep(0.1); // 10 req/s per VU = 1000 req/s total
}
