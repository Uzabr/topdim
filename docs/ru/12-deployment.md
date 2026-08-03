# 12. Деплой

## Артефакты

- Backend: multi-stage `docker/backend/Dockerfile`, Gradle `bootJar`, Temurin 21 JRE, non-root user, Actuator health check.
- Frontend: Node 22 build → nginx 1.27 static image.
- Bot: `telegram-bot/Dockerfile`.
- Registry: GHCR tags `latest` и immutable `sha-<commit>`.

## Production topology

Текущий подтверждённый deployment — EasyPanel/Docker Swarm на Ubuntu VPS за Traefik/Let's Encrypt. Публичны gateway и три SPA; stateful/internal services не должны иметь public domains/ports.

## Последовательность

1. PR проходит CI и merge в `main`.
2. CD строит/публикует 8 backend и 3 frontend images.
3. Path filter определяет изменённые deploy targets.
4. GitHub Actions подключается restricted SSH user с pinned host key.
5. Server forced command запускает `scripts/deploy/deploy.sh`.
6. Server выполняет pre-deploy DB backup и вызывает localhost EasyPanel webhooks.
7. EasyPanel разворачивает `latest`; после deploy проверяются replicas и `/actuator/health`.

`bazaar-service` и Telegram bot не входят в CD build/deploy list. `docker-compose.prod.yml` — reference/local production-like stack и не равен текущему Swarm source of truth.

## Миграции

Flyway запускается при старте каждого сервиса. Миграции должны быть backward-compatible при rolling deployment: add-before-use, backfill, затем отдельное удаление. Перед data-destructive migration — проверенный backup.

## Rollback

- application: выбрать прежний `sha-*` image digest в EasyPanel/Swarm;
- database: forward fix предпочтительнее; restore S3 dump только с согласованной остановкой writers;
- frontend: откатить соответствующий image SHA;
- после rollback проверить gateway health, Eureka registration, DB migrations, queue depth и ключевой smoke flow.

## Health и post-deploy

Проверить TLS/domains, replicas, `SERVER_PORT`, Eureka membership, `/actuator/health`, gateway public catalog, auth/login, Rabbit queues, MinIO upload/read, buyer checkout/demo payment и logs. SMTP health должен быть выключен для identity/notification, чтобы внешняя почта не убивала container health.

## Staging и zero downtime

Staging не обнаружен. Swarm может делать rolling update, но параметры update order/parallelism не хранятся в репозитории. Flyway и event/API compatibility поэтому критичны. Формальные RTO/RPO отсутствуют.

Evidence:
- `.github/workflows/cd.yml`
- `docker/backend/Dockerfile`
- `docker/frontend/Dockerfile`
- `scripts/deploy/deploy.sh`
- `docs/PROJECT_STATE.md`
