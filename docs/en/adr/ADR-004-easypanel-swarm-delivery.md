# ADR-004: GHCR and EasyPanel/Docker Swarm

## Status
Accepted.

## Context
Production delivery needs private immutable artifacts, TLS ingress, and restricted administration.

## Decision
Actions builds GHCR `latest`/`sha-*`; restricted SSH invokes server-side backup and EasyPanel webhooks; Traefik exposes the gateway and SPAs.

## Alternatives considered
Building manually on the VPS; Kubernetes; plain Docker Compose.

## Consequences
Native CI builds and controlled networking, but part of desired state remains outside git and bazaar/bot are not covered by CD.

## Evidence
`.github/workflows/cd.yml`, `scripts/deploy/deploy.sh`, `docs/PROJECT_STATE.md`.
