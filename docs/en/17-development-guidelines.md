# 17. Development guidelines

## Confirmed conventions

- Short `feature/`, `fix/`, `chore/`, or `docs/` branches from `main`.
- Conventional Commits, PR, green CI, at least one approval, squash merge.
- Service/domain-layer Java packages; DTOs remain separate from persistence entities.
- New versioned Flyway migrations only; applied scripts are immutable.
- `ApiResponse<T>` is the preferred response envelope and Jakarta validation is used.
- Gateway and downstream roles plus service-level ownership checks.
- Shared events live in `shared/common-events` and must evolve compatibly.
- Secrets are neither committed nor logged.

## Change checklist

1. State the business rule and failure cases.
2. Add a regression test before fixing a defect.
3. Avoid unrelated refactors and public-contract changes.
4. For cross-service work, update both sides, gateway routes, clients, docs, and tests.
5. Make migrations rolling-compatible and define rollback/forward fix.
6. Analyze repeated HTTP/event delivery and idempotency.
7. Keep runtime desired state under the documented delivery mechanism.

Recommended additions are a uniform error schema/correlation ID, architecture dependency tests, event versions/outbox, required production internal secret, and CODEOWNERS for security/migrations/workflows.

Definition of Done: business and negative/edge/duplicate/forbidden cases analyzed; appropriate tests green; lint/build/diff checks pass; API/data/event/security/deploy docs updated; no secrets/unrelated changes/hidden assumptions; rollout/monitoring/rollback documented.

Evidence: `CONTRIBUTING.md`, `AGENTS.md`, `.github/workflows/ci.yml`, current tests.
