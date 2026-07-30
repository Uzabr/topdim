# 17. Правила разработки

## Подтверждённые правила

- Короткие `feature/`, `fix/`, `chore/`, `docs/` ветки от `main`.
- Conventional Commits, PR, зелёный CI, минимум один approve, squash merge.
- Java package по service/domain layer; DTO не передаются как JPA entities.
- Schema changes — только новые versioned Flyway migrations; applied files неизменяемы.
- API responses преимущественно `ApiResponse<T>`; validation через Jakarta Bean Validation.
- Role checks выполняются gateway и downstream `@PreAuthorize`; ownership всегда проверяется в service.
- События определяются в `shared/common-events`; producer/consumer меняются совместимо.
- Secrets не коммитятся и не логируются.

## Требования к изменению

1. Описать business rule и failure cases.
2. Сначала добавить regression test для дефекта.
3. Избегать широкого refactor/public contract change.
4. Для cross-service изменения обновить обе стороны, gateway route, frontend client, docs и tests.
5. Для migration предусмотреть rolling compatibility и rollback/forward fix.
6. Проверить retry/idempotency для повторного HTTP/event вызова.
7. Не смешивать manual server config с source-controlled deployment.

## Рекомендации

- Ввести единый error schema и correlation ID.
- Добавить architectural tests для запрещённых cross-service imports.
- Версионировать event payloads и использовать outbox.
- Require `INTERNAL_AUTH_SECRET` в prod profile.
- Добавить CODEOWNERS для security, migrations, workflows.

## Definition of Done

- business logic и negative/edge/duplicate/forbidden cases проанализированы;
- unit/integration/contract tests добавлены и проходят;
- lint/build/`git diff --check` проходят;
- API/data/event/security/deployment docs обновлены;
- нет secrets, unrelated changes или undocumented assumptions;
- rollout/monitoring/rollback impact описан.

Evidence: `CONTRIBUTING.md`, `AGENTS.md`, `.github/workflows/ci.yml`, текущий test style.
