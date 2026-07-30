# ADR-004: GHCR и EasyPanel/Docker Swarm

## Status
Accepted.

## Context
Production deployment требует приватные immutable artifacts, TLS edge и ограниченный административный доступ.

## Decision
GitHub Actions строит GHCR `latest`/`sha-*`; restricted SSH forced command вызывает server-side backup и EasyPanel webhooks; Traefik публикует gateway/SPAs.

## Alternatives considered
Ручная сборка на VPS; Kubernetes; plain Docker Compose.

## Consequences
Нативная CI build и контролируемая сеть, но часть desired state/webhook tokens живёт вне git; bazaar/bot не покрыты CD.

## Evidence
`.github/workflows/cd.yml`, `scripts/deploy/deploy.sh`, `docs/PROJECT_STATE.md`.
