# sizbiz Documentation

Дата актуализации: 2026-07-30.

## Новая двуязычная документация

- [Русская версия](ru/README.md)
- [English version](en/README.md)
- [Для sales и нетехнических сотрудников](ru/business/README.md)
- [For sales and non-technical employees](en/business/README.md)

Обе папки содержат зеркальные технические разделы, бизнес-документацию, ADR и
C4/PlantUML-диаграммы на срез 2026-07-30. Материалы ниже сохранены и остаются
полезными предметными/QA/operational источниками, но при расхождении приоритет
имеют код, конфигурация и новый gap analysis.

Публичный бренд продукта — **sizbiz**. Имя `topdim` остаётся в документации
только как реальный технический идентификатор или историческая ссылка.

Эта папка теперь разделена по ролям, чтобы разработчик или тестировщик сразу попадал в нужный контекст. Текущая рабочая документация лежит в `product`, `frontend`, `backend` и `qa`. Старые планы, промпты для ИИ и уже реализованные backlog-файлы перенесены в `archive`.

## Быстрый вход

| Для кого | С чего начать | Что внутри |
|---|---|---|
| Product / owner | [product/README.md](product/README.md) | PRD, роли, бизнес-флоу купонов и партнёров |
| Frontend developer | [frontend/README.md](frontend/README.md) | web-app, admin-app, partner-app, API client, routing, формы |
| Backend developer | [backend/README.md](backend/README.md) | сервисы, API contract, база, события, бизнес-инварианты |
| QA / tester | [qa/README.md](qa/README.md) | ручной тест-план, стратегия, purchase-flow чеклисты |
| AI / agent | [superpowers/plans](superpowers/plans) | рабочие планы и исторический контекст; перед исполнением сверять с кодом |
| История | [archive/README.md](archive/README.md) | реализованные планы, старые статусы, промпты и reference-файлы |
| Аудит docs | [documentation-audit.md](documentation-audit.md) | что сверено с текущим кодом и где остались риски рассинхрона |

## Главные правила документации

1. Текущим источником правды считаются только `product`, `frontend`, `backend` и `qa`.
2. `archive` нельзя использовать как актуальное ТЗ без повторной проверки кода.
3. Если меняется API, обновляй `backend/api-contract.md`, frontend README, product roles и QA сценарии.
4. Если меняется бизнес-логика купона, обновляй `product/flows/coupon-flow.md` и QA regression checklist.
5. Если задача уже реализована, её план переносится в `archive/implemented` или `archive/superpowers`.

## Текущий MVP фокус

sizbiz сейчас фокусируется на купонах, партнёрах и полном локальном buyer flow:

- партнёр оставляет заявку;
- админ создаёт/проверяет мерчанта;
- партнёр подаёт заявку на купон;
- админ готовит купон и отправляет партнёру на согласование;
- купон публикуется после подтверждения;
- покупатель покупает купон через demo/payment flow;
- покупатель видит купон в профиле, QR/PIN, заказы, возвраты, жалобы, отзывы и уведомления;
- партнёр/кассир гасит купон по PIN/QR через partner app.

Bazaar/directory остаётся отдельным контуром и не должен мешать MVP купонов и партнёров. Production/server setup сейчас не является активной задачей: разработка и проверка ведутся локально.

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
