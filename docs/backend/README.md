# Backend Documentation

Этот раздел для backend-разработчиков TopDim. Основной фокус: бизнес-инварианты, безопасные статусные переходы, роли, транзакционность и тесты.

## Документы

| Документ | Назначение |
|---|---|
| [services-overview.md](services-overview.md) | Обзор сервисов, endpoints и межсервисных событий |
| [api-contract.md](api-contract.md) | Единый контракт Frontend ↔ Backend |
| [database.md](database.md) | Базы данных, таблицы, индексы, миграции |
| [../product/flows/coupon-flow.md](../product/flows/coupon-flow.md) | Бизнес-флоу купона |
| [../product/roles.md](../product/roles.md) | Роли и права |

## Сервисы

| Сервис | Порт | Ответственность |
|---|---:|---|
| API Gateway | `8080` | входная точка, JWT validation, routing, headers |
| identity-service | `8081` | auth, users, roles, partner applications, staff |
| coupon-service | `8083` | merchants, coupons, coupon options, moderation |
| order-service | `8084` | cart, orders, purchased coupons, redemption |
| payment-service | `8085` | payments, callbacks, demo complete |
| bazaar-service | `8086` | bazaar/directory контур |
| notification-service | `8087` | in-app notifications, events, email/SMS stub/real mode |
| media-service | `8088` | uploads, MinIO, media metadata |

## Локальные skills

При backend-задачах используй инструкции из:

- `services/.agent/skills/api-design-principles/SKILL.md`
- `services/.agent/skills/spring-boot-crud-patterns/SKILL.md`
- `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
- `services/.agent/skills/database-schema-designer/SKILL.md`
- `services/.agent/skills/e2e-testing-patterns/SKILL.md`

## Архитектурные правила

- Public API должен быть ресурсным и понятным: nouns в URL, HTTP methods по смыслу.
- Все ответы идут через `ApiResponse<T>`.
- Сервис не должен доверять frontend-данным для критичных операций: цена, статус, лимиты и merchant context проверяются на backend.
- Для protected endpoints API Gateway пробрасывает trusted headers вроде `X-User-Id`.
- Партнёрский merchant context должен резолвиться по текущему пользователю/staff, а не из request body.
- События через RabbitMQ должны быть идемпотентны, потому что retry возможен.

## Критичные бизнес-инварианты

- `createOrder` обязан повторно валидировать корзину через coupon-service.
- `PaymentCompletedEvent` не должен создавать дубли purchased coupons.
- `PurchasedCoupon` можно погасить только в статусе `ACTIVE`.
- Повторное погашение одного PIN/QR должно возвращать бизнес-ошибку.
- Купон чужого мерчанта нельзя погасить.
- Истёкший купон должен стать `EXPIRED` и не должен быть погашен.
- ACTIVE coupon offer нельзя менять так, чтобы сломать уже купленные купоны.

## Команды проверки

```bash
./gradlew :services:identity-service:test
./gradlew :services:coupon-service:test
./gradlew :services:order-service:test
./gradlew :services:payment-service:test
```

Для полной проверки:

```bash
./gradlew test
```

## Что добавлять в тесты

По `AGENTS.md` каждый важный бизнес-сценарий должен иметь не только happy path. Минимальный набор:

- invalid input;
- not found;
- forbidden action;
- conflict/status transition;
- duplicate/retry;
- boundary values;
- external dependency failure;
- transaction/idempotency risk.
