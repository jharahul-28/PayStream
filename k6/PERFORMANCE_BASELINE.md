# PayStream Performance Baseline

## How to Run

### Prerequisites
- k6 installed: `brew install k6` or `snap install k6`
- Full docker-compose stack running

### Payment Load Test
```bash
k6 run \
  --env GATEWAY_URL=http://localhost:8080 \
  --out json=k6/results/payment-load-$(date +%Y%m%d-%H%M%S).json \
  k6/scripts/payment-load-test.js
```

### Fraud Rules Load Test
```bash
k6 run \
  --env GATEWAY_URL=http://localhost:8080 \
  --env INTERNAL_KEY=dev-only-local-key \
  --out json=k6/results/fraud-load-$(date +%Y%m%d-%H%M%S).json \
  k6/scripts/fraud-rules-load-test.js
```

---

## Baseline Results (Template — Fill After First Run)

| Metric                         | Target        | Actual  | Pass? |
|-------------------------------|---------------|---------|-------|
| **Payment Load Test**          |               |         |       |
| Date                           | —             | TBD     | —     |
| Environment                    | —             | Docker local (M3 MacBook Pro 16GB) | — |
| VUs                            | 500           | 500     | —     |
| Duration                       | 5 min         | 5 min   | —     |
| Total requests                 | —             | TBD     | —     |
| TPS (throughput)               | —             | TBD     | —     |
| p50 latency                    | —             | TBD ms  | —     |
| p95 latency                    | < 250ms       | TBD ms  | —     |
| p99 latency                    | < 500ms       | TBD ms  | —     |
| Error rate                     | < 1%          | TBD%    | —     |
| Check success rate             | > 99%         | TBD%    | —     |
| Kafka outbox lag during test   | —             | TBD     | —     |
|                                |               |         |       |
| **Fraud Rules Load Test**      |               |         |       |
| Date                           | —             | TBD     | —     |
| VUs                            | 100           | 100     | —     |
| Duration                       | 2 min         | 2 min   | —     |
| p50 latency                    | —             | TBD ms  | —     |
| p95 latency                    | < 5ms         | TBD ms  | —     |
| p99 latency                    | < 10ms        | TBD ms  | —     |
| Error rate                     | < 0.1%        | TBD%    | —     |

---

## Notes

- Results stored in `k6/results/` (gitignored — don't commit raw output)
- Re-run after each major release and store a dated snapshot
- Regression threshold: any metric worsening > 20% from baseline requires investigation
- Kafka consumer lag monitored via Prometheus `paystream.kafka.consumer.lag` gauge
- Connection pool exhaustion visible via Hikari metrics: `hikaricp.connections.timeout`

## Scaling Observations

- payment-service is CPU-bound during fraud check (synchronous)
- wallet-service is IO-bound (DB + optimistic lock retries)
- ledger-service is Kafka-consumer-bound — scale by adding partitions
- Rate limit Redis calls add ~1-2ms to payment latency — acceptable

## Known Bottlenecks

1. Single Redis instance for rate limiting — use Redis Cluster in production
2. Kafka single-broker in local — 3 brokers in production with replication factor 3
3. Eureka service discovery round-trips add ~2ms — acceptable, cached in Ribbon
