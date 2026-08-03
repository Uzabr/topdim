# Документация sizbiz

Актуальность среза: **2026-07-30**. Язык: русский.

Это описание фактически реализованной платформы sizbiz. Код и конфигурация
имеют приоритет над этим текстом; исторические материалы в `docs/archive/` не
считаются текущей спецификацией. `topdim` — внутреннее инженерное имя,
сохраняемое в технических идентификаторах.

## Навигация

| Раздел | Документ |
|---|---|
| Для бизнеса и сотрудников | [Миссия, sales, onboarding и ролевые пути](business/README.md) |
| Продукт и требования | [01 — Обзор](01-project-overview.md), [02 — Бизнес-контекст](02-business-context.md), [03 — Требования](03-system-requirements.md) |
| Архитектура | [04 — Архитектура](04-architecture-overview.md), [05 — C4](05-c4-model.md), [06 — Компоненты](06-components-and-modules.md) |
| Контракты | [07 — Данные](07-data-architecture.md), [08 — API](08-api-documentation.md), [09 — Безопасность](09-security.md) |
| Эксплуатация | [10 — Локальная разработка](10-local-development.md), [11 — Конфигурация](11-configuration.md), [12 — Деплой](12-deployment.md), [13 — CI/CD](13-ci-cd.md) |
| Качество и сопровождение | [14 — Тестирование](14-testing-strategy.md), [15 — Наблюдаемость](15-observability.md), [16 — Troubleshooting](16-troubleshooting.md), [17 — Правила разработки](17-development-guidelines.md) |
| Справка | [18 — Глоссарий](18-glossary.md), [19 — Ограничения](19-known-limitations.md), [20 — Пробелы](20-documentation-gaps.md), [ADR](adr/README.md) |

Диаграммы PlantUML находятся в [diagrams](diagrams/README.md). Английская версия: [../en/README.md](../en/README.md).

## Источники

- `settings.gradle`, `build.gradle` — состав и базовые версии.
- `services/*`, `infrastructure/*`, `shared/*` — backend.
- `frontend/web-app`, `frontend/admin-app`, `frontend/partner` — текущие SPA; `frontend/web-app.bak` исключён.
- `docker-compose*.yml`, `docker/*`, `.github/workflows/*`, `scripts/*` — эксплуатация.
- `docs/PROJECT_STATE.md`, `docs/SECURITY_HARDENING.md` — текущий operational/security context, проверяемый по коду.
