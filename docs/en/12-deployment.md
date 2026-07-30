# 12. Deployment

Backend images use a multi-stage Gradle/Temurin 21 build and a non-root JRE runtime with Actuator health checks. Frontends build on Node 22 and run on nginx 1.27. The bot has a separate Dockerfile. GHCR publishes `latest` and immutable `sha-<commit>` tags.

The confirmed production topology is EasyPanel/Docker Swarm on Ubuntu behind Traefik/Let's Encrypt. Only the gateway and three SPAs should be public.

## Sequence

1. PR passes CI and merges to `main`.
2. CD builds and pushes eight backend and three frontend images.
3. A path filter computes changed targets.
4. Actions uses a restricted SSH account and pinned host key.
5. A forced command runs `scripts/deploy/deploy.sh`.
6. The server performs a pre-deploy DB backup and invokes local EasyPanel webhooks.
7. EasyPanel deploys images; replicas and health endpoints are verified operationally.

`bazaar-service` and Telegram bot are not in the CD matrix. `docker-compose.prod.yml` is a production-like reference, not the live Swarm source of truth.

Flyway runs on service startup. Migrations must support rolling compatibility: add, deploy/backfill, then remove in a later release. Back up before destructive data operations.

Rollback uses the previous `sha-*` image/digest. Prefer a forward DB fix; restore a dump only with coordinated writer downtime. After rollback validate TLS, replicas, Eureka, health, migrations, queue depth, storage, and a critical buyer flow.

Staging is not evidenced. Swarm rolling-update policy is not versioned in this repository, and formal RTO/RPO is undefined.

Evidence:
- `.github/workflows/cd.yml`
- `docker/backend/Dockerfile`
- `docker/frontend/Dockerfile`
- `scripts/deploy/deploy.sh`
- `docs/PROJECT_STATE.md`
