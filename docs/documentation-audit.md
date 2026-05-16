# Documentation Audit — 2026-05-16

Аудит сделан по текущему коду проекта, без опоры на git log. Цель — быстро показать, какие docs были рассинхронизированы с реальной реализацией и что обновлено в первую очередь.

## Текущая карта проекта

| Зона | Актуальное состояние |
|---|---|
| Backend | Spring Boot microservices: identity, coupon, order, payment, bazaar, notification, media + API Gateway/Eureka |
| Frontend | 3 актуальных Vite apps: `frontend/web-app`, `frontend/admin-app`, `frontend/partner` |
| Backup | `frontend/web-app.bak` — не считать текущим приложением |
| Payments | `payment.mode=demo|provider`; локально используется demo completion, реальные Payme/Click/Uzum ещё не production-ready |
| Notifications | In-app notifications реализованы; email/SMS по умолчанию в stub mode |
| Partner cashier | Нет JWT-роли `PARTNER_CASHIER`; cashier — staff context поверх роли `PARTNER` |

## Что было устаревшим и обновлено

| Документ | Что было неактуально | Что обновлено |
|---|---|---|
| `README.md` | Один frontend app, Node 18+, старое дерево `docs/BACKEND.md` | Добавлены 3 frontend apps, Node 22+, реальная структура docs |
| `docs/README.md` | Дата 2026-04-28, старый MVP scope | Дата 2026-05-16, buyer profile/refunds/complaints/reviews/notifications, локальный фокус |
| `docs/frontend/README.md` | Не были отмечены admin route gaps и backup app | Добавлены gaps, Vite 8, web-app.bak warning |
| `docs/frontend/web-app.md` | Vite 6, `_client.ts`, 2 apps, checkout без auth, guest favorites limit 5 | Исправлено на Vite 8, `client.ts`, 3 apps, payment route, checkout auth, favorites 10/50 |
| `docs/backend/services-overview.md` | Старые Feign calls/events, PARTNER_CASHIER, refund statuses, partner coupon request path, redemption path | Исправлены основные endpoints, статусы, events, media/payment/security notes |
| `docs/backend/api-contract.md` | Coupon Swagger порт `8082`, не хватало refund/payment mode details | Исправлен порт `8083`, добавлены order/payment/bazaar/notification/media Swagger links, refund API, payment mode |
| `docs/backend/database.md` | Elasticsearch как активная зависимость, старые order/refund/purchased coupon поля | Отмечено, что ES не активен; обновлены ключевые статусы и поля |
| `docs/product/roles.md` | Универсальное наследование ролей, `PARTNER_CASHIER`, старые auth/refund/review/redemption endpoints | Исправлена модель ролей, cashier context, новые endpoints и текущий статус ролей |
| `docs/product/flows/coupon-flow.md` | Admin create как `DRAFT`, legacy redemption, старые refund/status gaps | Обновлены state machine, partner approve/revision, PIN/QR redemption, refund flow |
| `docs/product/flows/coupon-creation-flow.md` | Документ утверждал `status=ACTIVE` при создании и ссылался на старую форму | Переписан под текущий LEAD→DRAFT→approval flow |
| `docs/qa/*` | В активных чеклистах оставались `PARTNER_CASHIER` и legacy `X-Merchant-Id` как основной путь | Обновлены smoke/checklist формулировки под cashier staff context и partner redemption endpoints |

## Что ещё требует отдельной проверки

- `docs/qa/*` нужно пройти следующим шагом глубже: базовые stale markers исправлены, но чеклисты profile/refund/complaint/review нужно ещё сверить с UI постранично.
- `docs/backend/database.md` всё ещё остаётся архитектурным документом, а не автогенерированной схемой. Перед миграциями сверять с Flyway и entities.
- `docs/superpowers/plans/*` не переносились и могут содержать уже реализованные или устаревшие шаги. Их использовать только как историю/черновики.
- `frontend/admin-app` меню содержит пункты без маршрутов: `/catalog/bazaars`, `/catalog/shops`, `/orders/promocodes`, `/users/list`.
- Production server setup сейчас не актуализировался, потому что текущий фокус — локальная разработка.

## Рекомендуемый следующий шаг

1. Обновить QA checklist под текущий buyer flow: catalog → cart → checkout → payment → profile coupons → refund/complaint/review → partner redemption.
2. Затем пройти каждый сервис по AGENTS.md: бизнес-риски, недостающие negative/edge tests, регрессионные сценарии.
