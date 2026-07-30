# 13. CI/CD

## CI

`ci.yml` запускается на push/PR в `main`.

```text
detect paths
 ├─ backend → Java 21 → Gradle tests → JaCoCo report/artifact
 └─ frontend matrix → Node 22 → npm ci → lint → build
```

На обычном push оба tier запускаются независимо от path result. Frontend matrix содержит web/admin/partner, но `npm test` не выполняется даже для web-app. JaCoCo minimum line coverage — 10% после исключения DTO/entity/config/exception.

Отдельно:

- `gitleaks.yml` — secret scan на PR/push main;
- `claude-code-review.yml` — AI review;
- `cleanup.yml` — weekly removal of untagged GHCR versions, минимум 5;
- Dependabot обновляет actions/dependencies.

## CD

`cd.yml` запускается на push `main` или вручную. Matrix строит discovery, gateway, identity, coupon, order, payment, notification, media и три SPA. BuildKit cache и pinned actions используются; provenance отключён. Deploy по SSH зависит от успешных обеих build matrices.

Path filtering разворачивает общий backend/frontend tier при shared changes и сохраняет порядок discovery→gateway→services→frontends. Уведомления отправляются в Telegram при настроенных secrets.

## Secrets

Required deploy secrets: `DEPLOY_SSH_KEY`, `DEPLOY_SSH_HOST`, `DEPLOY_SSH_PORT`, `DEPLOY_SSH_USER`, `DEPLOY_KNOWN_HOSTS`. GHCR использует `GITHUB_TOKEN`; Telegram notifications — отдельные token/chat ID. EasyPanel webhook tokens находятся только на сервере.

## Branch/promotion

`CONTRIBUTING.md` требует короткую ветку, PR, зелёный CI, один approve, squash merge. Техническая branch protection на момент документа не подтверждена. Документ описывает ручной deploy, но текущий `cd.yml` автоматически deploys changed services после push main — это рассинхронизация.

## Пробелы

- Нет environment approvals или staging promotion.
- CD не включает bazaar-service/bot.
- CI не запускает buyer Vitest и не имеет admin/partner tests.
- Нет image vulnerability/SBOM/signature/provenance checks.
- Нет deployment smoke/rollback logic в tracked workflow; оно зависит от server-side script/webhooks.
