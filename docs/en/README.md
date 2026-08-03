# sizbiz Documentation

Snapshot date: **2026-07-30**. Language: English.

This documents the implemented sizbiz platform. Source code and configuration
take precedence; historical material under `docs/archive/` is not a current
specification. `topdim` is the internal engineering name retained in technical
identifiers.

## Navigation

| Area | Document |
|---|---|
| Business and employees | [Mission, sales, onboarding, and role journeys](business/README.md) |
| Product and requirements | [01 — Overview](01-project-overview.md), [02 — Business context](02-business-context.md), [03 — Requirements](03-system-requirements.md) |
| Architecture | [04 — Architecture](04-architecture-overview.md), [05 — C4](05-c4-model.md), [06 — Components](06-components-and-modules.md) |
| Contracts | [07 — Data](07-data-architecture.md), [08 — API](08-api-documentation.md), [09 — Security](09-security.md) |
| Operations | [10 — Local development](10-local-development.md), [11 — Configuration](11-configuration.md), [12 — Deployment](12-deployment.md), [13 — CI/CD](13-ci-cd.md) |
| Quality and maintenance | [14 — Testing](14-testing-strategy.md), [15 — Observability](15-observability.md), [16 — Troubleshooting](16-troubleshooting.md), [17 — Development guidelines](17-development-guidelines.md) |
| Reference | [18 — Glossary](18-glossary.md), [19 — Limitations](19-known-limitations.md), [20 — Gaps](20-documentation-gaps.md), [ADR](adr/README.md) |

PlantUML sources are in [diagrams](diagrams/README.md). Russian version: [../ru/README.md](../ru/README.md).

## Evidence base

- `settings.gradle`, `build.gradle` — modules and baseline versions.
- `services/*`, `infrastructure/*`, `shared/*` — backend.
- `frontend/web-app`, `frontend/admin-app`, `frontend/partner` — current SPAs; `frontend/web-app.bak` is excluded.
- `docker-compose*.yml`, `docker/*`, `.github/workflows/*`, `scripts/*` — operations.
- `docs/PROJECT_STATE.md`, `docs/SECURITY_HARDENING.md` — operational and security context, cross-checked against code.
