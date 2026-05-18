---
description: Design CI/CD pipelines with approval gates, deployment strategies, and rollback automation. Use when setting up or improving deployment workflows.
---

# Deployment Pipeline Design

$ARGUMENTS

## Standard Pipeline Flow

```
Build → Test → Security Scan → Staging Deploy → Integration Tests → Approve → Production → Verify
```

## GitHub Actions Example

```yaml
name: Production Pipeline

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build & push image
        run: |
          docker build -t $IMAGE:${{ github.sha }} .
          docker push $IMAGE:${{ github.sha }}

  test:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Unit + integration tests
        run: ./mvnw test
      - name: Security scan
        run: trivy image $IMAGE:${{ github.sha }}

  deploy-staging:
    needs: test
    environment: staging
    runs-on: ubuntu-latest
    steps:
      - run: kubectl set image deployment/app app=$IMAGE:${{ github.sha }}

  integration-test:
    needs: deploy-staging
    runs-on: ubuntu-latest
    steps:
      - run: npm run test:e2e

  deploy-production:
    needs: integration-test
    environment: production   # ← triggers manual approval gate
    runs-on: ubuntu-latest
    steps:
      - run: kubectl set image deployment/app app=$IMAGE:${{ github.sha }}
      - name: Verify health
        run: curl -f https://app.example.com/actuator/health

  rollback-on-failure:
    needs: deploy-production
    if: failure()
    runs-on: ubuntu-latest
    steps:
      - run: kubectl rollout undo deployment/app
```

## Deployment Strategies

| Strategy | When | Trade-off |
|----------|------|-----------|
| Rolling | Most apps | Zero downtime, gradual |
| Blue-Green | High-risk | Instant switchover, 2x cost |
| Canary | Large traffic | Real user testing, complex |

```yaml
# Rolling (K8s default)
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 2
    maxUnavailable: 1

# Canary (Argo Rollouts)
strategy:
  canary:
    steps:
      - setWeight: 10
      - pause: { duration: 5m }
      - setWeight: 50
      - pause: { duration: 5m }
```

## Rollback

```bash
kubectl rollout undo deployment/<app>
kubectl rollout undo deployment/<app> --to-revision=3
```

## Pipeline Best Practices

- Fail fast: run quick tests first
- Cache dependencies between runs
- Never put secrets in code — use GitHub Secrets / Vault
- Schedule production deploys (avoid Friday evening)
- Monitor error rate after deploy, auto-rollback if >1% error rate
- DORA metrics: deployment frequency, lead time, change failure rate, MTTR
