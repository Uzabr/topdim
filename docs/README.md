# TopDim Documentation

Дата актуализации: 2026-04-28.

Эта папка теперь разделена по ролям, чтобы разработчик или тестировщик сразу попадал в нужный контекст. Текущая рабочая документация лежит в `product`, `frontend`, `backend` и `qa`. Старые планы, промпты для ИИ и уже реализованные backlog-файлы перенесены в `archive`.

## Быстрый вход

| Для кого | С чего начать | Что внутри |
|---|---|---|
| Product / owner | [product/README.md](product/README.md) | PRD, роли, бизнес-флоу купонов и партнёров |
| Frontend developer | [frontend/README.md](frontend/README.md) | web-app, admin-app, partner-app, API client, routing, формы |
| Backend developer | [backend/README.md](backend/README.md) | сервисы, API contract, база, события, бизнес-инварианты |
| QA / tester | [qa/README.md](qa/README.md) | ручной тест-план, стратегия, purchase-flow чеклисты |
| AI / agent | [superpowers/plans](superpowers/plans) | активные планы, которые ещё могут выполняться |
| История | [archive/README.md](archive/README.md) | реализованные планы, старые статусы, промпты и reference-файлы |

## Главные правила документации

1. Текущим источником правды считаются только `product`, `frontend`, `backend` и `qa`.
2. `archive` нельзя использовать как актуальное ТЗ без повторной проверки кода.
3. Если меняется API, обновляй `backend/api-contract.md`, frontend README и QA сценарии.
4. Если меняется бизнес-логика купона, обновляй `product/flows/coupon-flow.md` и QA regression checklist.
5. Если задача уже реализована, её план переносится в `archive/implemented` или `archive/superpowers`.

## Текущий MVP фокус

TopDim сейчас фокусируется на купонах и партнёрах:

- партнёр оставляет заявку;
- админ создаёт/проверяет мерчанта;
- партнёр подаёт заявку на купон;
- админ готовит купон и отправляет партнёру на согласование;
- купон публикуется после подтверждения;
- покупатель покупает купон;
- партнёр/кассир гасит купон по PIN/QR.

Bazaar/directory остаётся отдельным контуром и не должен мешать MVP купонов и партнёров.

## Навигация по важным документам

- [Product PRD](product/prd.md)
- [Roles and permissions](product/roles.md)
- [Coupon flow](product/flows/coupon-flow.md)
- [Backend services overview](backend/services-overview.md)
- [API contract](backend/api-contract.md)
- [Database architecture](backend/database.md)
- [Frontend web app](frontend/web-app.md)
- [QA manual test plan](qa/manual-test-plan.md)
- [Testing strategy](qa/test-strategy.md)

