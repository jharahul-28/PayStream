# PayStream — Security Reference

## Authentication Flow

```
Client -> API Gateway (JwtGlobalFilter)
             |— validates RS256 JWT (public key from JwksCache)
             |— checks Redis blocklist: token:blocklist:{jti}
             |— injects X-User-Id, X-User-Role headers
             |— strips Authorization header before forwarding
             v
       Downstream Service
             |— reads X-User-Id, X-User-Role (trusted internal headers)
             |— enforces @PreAuthorize based on X-User-Role
```

JWT properties:
- Algorithm: RS256 (asymmetric — private key in auth-service only)
- Access token TTL: 15 minutes (900s)
- Refresh token TTL: 7 days (604800s) — rotated on each use
- Revocation: jti added to Redis blocklist on logout

## RBAC Matrix

| Endpoint                                    | CUSTOMER | MERCHANT | FRAUD_ANALYST | FINANCE_OPS | ADMIN |
|---------------------------------------------|----------|----------|---------------|-------------|-------|
| POST /auth/register                         | ✓        | ✓        | ✓             | ✓           | ✓     |
| POST /auth/login                            | ✓        | ✓        | ✓             | ✓           | ✓     |
| POST /auth/refresh                          | ✓        | ✓        | ✓             | ✓           | ✓     |
| POST /auth/logout                           | ✓        | ✓        | ✓             | ✓           | ✓     |
| GET  /auth/me                               | ✓        | ✓        | ✓             | ✓           | ✓     |
| POST /wallets                               | ✓        | ✓        | —             | —           | ✓     |
| GET  /wallets/my                            | ✓        | ✓        | —             | —           | ✓     |
| GET  /wallets/{id}/statement                | ✓        | ✓        | —             | —           | ✓     |
| POST /wallets/{id}/debit [INTERNAL]         | internal | internal | internal      | internal    | internal |
| POST /wallets/{id}/credit [INTERNAL]        | internal | internal | internal      | internal    | internal |
| POST /payments                              | ✓        | ✓        | —             | —           | ✓     |
| GET  /payments/{id}                         | own only | own only | —             | —           | ✓     |
| GET  /payments                              | own only | own only | —             | —           | ✓     |
| POST /payments/{id}/refund                  | own only | own only | —             | —           | ✓     |
| GET  /payments/admin/all                    | —        | —        | —             | —           | ✓     |
| GET  /ledger/accounts/{id}/balance          | own only | own only | ✓             | ✓           | ✓     |
| GET  /ledger/accounts/{id}/entries          | own only | own only | ✓             | ✓           | ✓     |
| GET  /ledger/transactions/{id}              | own only | own only | ✓             | ✓           | ✓     |
| POST /fraud/check [INTERNAL]                | internal | internal | internal      | internal    | internal |
| GET  /fraud/payments/{id}                   | —        | —        | ✓             | —           | ✓     |
| GET  /fraud/users/{id}/history              | —        | —        | ✓             | —           | ✓     |
| PUT  /fraud/users/{id}/block                | —        | —        | ✓             | —           | ✓     |
| DELETE /fraud/users/{id}/block              | —        | —        | —             | —           | ✓     |
| GET  /settlements/{id}                      | —        | —        | —             | ✓           | ✓     |
| GET  /settlements                           | —        | —        | —             | ✓           | ✓     |
| POST /settlements/trigger                   | —        | —        | —             | ✓           | ✓     |
| POST /webhooks/endpoints                    | —        | ✓        | —             | —           | ✓     |
| GET  /webhooks/endpoints                    | —        | ✓        | —             | —           | ✓     |
| DELETE /webhooks/endpoints/{id}             | —        | ✓        | —             | —           | ✓     |
| GET  /webhooks/deliveries                   | —        | ✓        | —             | —           | ✓     |
| GET  /audit/logs                            | —        | —        | —             | ✓           | ✓     |
| GET  /audit/logs/{id}                       | —        | —        | —             | ✓           | ✓     |

Legend: ✓ = allowed, — = forbidden, own only = only own resources, internal = X-Internal-Service-Key required

## Webhook Security

Every webhook delivery carries:
- `X-PayStream-Signature: sha256={HMAC-SHA256(secret, timestamp + "." + body)}`
- `X-PayStream-Timestamp: {unix_epoch_seconds}`

Merchants verify: recompute HMAC with their registered secret; reject if timestamp > 300s old (replay protection).

## Internal Service Authentication

Services calling wallet-service or fraud-service internal endpoints must include:
```
X-Internal-Service-Key: {INTERNAL_SERVICE_KEY env var}
```
The key is validated by `InternalServiceAuthFilter` in each service. It is never logged.

## Rate Limiting (API Gateway)

| Path                    | Method | Limit          | Key     |
|-------------------------|--------|----------------|---------|
| /api/v1/auth/login      | POST   | 5 req/min      | IP      |
| /api/v1/auth/register   | POST   | 3 req/min      | IP      |
| /api/v1/payments        | POST   | 50 req/min     | userId  |
| /api/v1/fraud/check     | ANY    | 200 req/min    | global  |
| All other               | ANY    | 100 req/min    | userId  |

All limits return `429 Too Many Requests` with `Retry-After: 60`.

## HTTP Security Headers

Applied by `SecurityHeadersFilter` to every response:

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Cache-Control: no-store, no-cache
Content-Security-Policy: default-src 'none'
```

## Sensitive Data Handling

- Passwords: BCrypt strength 12, never stored in plaintext
- JWT private key: RSA-2048, only in auth-service, loaded from PEM file via env var path
- Database passwords: loaded from env vars, never hardcoded
- OpenAI API key: loaded from env var, never logged
- Internal service key: loaded from env var, never logged
- All user-supplied strings sanitized (newlines stripped) before logging

## Account Lockout Policy

- 5 consecutive failed logins → account locked for 15 minutes
- Lockout tracked in both Redis (`login:locked:{email}`) and DB (`locked_until` column)
- After lockout expires, successful login resets `failed_login_attempts` to 0

## Fraud Decision Boundary

The AI (Stage 2) NEVER influences payment allow/block decisions.
Only Stage 1 deterministic rule engine outputs are acted upon.
See [ADR-004](ADR/ADR-004-fraud-determinism.md) for rationale.

## Secret Management in Production

Use [External Secrets Operator](https://external-secrets.io/) to sync secrets from:
- AWS Secrets Manager / Parameter Store
- HashiCorp Vault
- GCP Secret Manager

Kubernetes Secrets in this repo are base64-encoded placeholders only — rotate before production use.
