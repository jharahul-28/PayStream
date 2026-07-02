# PayStream — Kubernetes Manifests

## Prerequisites

- Kubernetes 1.28+
- kubectl configured against your cluster
- Helm 3 (for stateful dependencies)
- Ingress-NGINX controller installed

## Structure

```
k8s/
├── shared/                    # Cluster-wide resources
│   ├── namespace.yml
│   ├── configmap.yml
│   ├── secret.yml             # PLACEHOLDER ONLY — use External Secrets in prod
│   └── ingress.yml
├── api-gateway/               # One folder per service
│   ├── deployment.yml
│   ├── service.yml
│   ├── hpa.yml
│   └── pdb.yml
├── auth-service/
├── payment-service/
├── wallet-service/
├── ledger-service/
├── fraud-service/
├── settlement-service/
├── notification-service/
├── webhook-service/
├── audit-service/
└── eureka-server/
```

## Deploying

### 1. Create namespace and shared resources

```bash
kubectl apply -f k8s/shared/namespace.yml
kubectl apply -f k8s/shared/configmap.yml
kubectl apply -f k8s/shared/secret.yml     # Replace placeholders first!
```

### 2. Install stateful services via Bitnami Helm charts

Postgres (one per bounded context):
```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
# Example for auth DB:
helm install postgres-auth bitnami/postgresql \
  --namespace paystream \
  --set auth.database=auth_db \
  --set auth.username=paystream \
  --set auth.existingSecret=paystream-secrets \
  --set auth.secretKeys.userPasswordKey=DB_PASSWORD
```

Redis:
```bash
helm install redis bitnami/redis \
  --namespace paystream \
  --set auth.existingSecret=paystream-secrets \
  --set auth.existingSecretPasswordKey=REDIS_PASSWORD
```

Kafka (KRaft mode via Bitnami):
```bash
helm install kafka bitnami/kafka \
  --namespace paystream \
  --set kraft.enabled=true \
  --set replicaCount=3
```

> In production, prefer **AWS RDS** (Postgres), **ElastiCache** (Redis), **MSK** (Kafka) over self-hosted Helm charts.

### 3. Deploy application services

```bash
# Apply all services at once
kubectl apply -f k8s/eureka-server/
kubectl apply -f k8s/api-gateway/
kubectl apply -f k8s/auth-service/
kubectl apply -f k8s/payment-service/
kubectl apply -f k8s/wallet-service/
kubectl apply -f k8s/ledger-service/
kubectl apply -f k8s/fraud-service/
kubectl apply -f k8s/settlement-service/
kubectl apply -f k8s/notification-service/
kubectl apply -f k8s/webhook-service/
kubectl apply -f k8s/audit-service/
```

### 4. Apply ingress

```bash
kubectl apply -f k8s/shared/ingress.yml
```

## Secrets Management in Production

Never commit real secrets. Use one of:

1. **External Secrets Operator** → AWS Secrets Manager / Vault / GCP Secret Manager
2. **Sealed Secrets** → `kubeseal --cert pub-cert.pem -o yaml < secret.yml > sealed-secret.yml`
3. **Vault Agent Injector** → Sidecar injects secrets as files

## Image Tags

Change `ghcr.io/your-org/paystream/{service}:latest` to your actual registry.

In production, always pin to a specific SHA or semantic version tag — never `:latest`.

## Health Checks

All services expose:
- `/actuator/health/liveness`  → K8s liveness probe
- `/actuator/health/readiness` → K8s readiness probe
- `/actuator/prometheus`       → Prometheus scrape target
