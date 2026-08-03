# 04. Обзор архитектуры

## Контуры

```text
React SPAs / Telegram
        │ HTTPS
        ▼
Traefik → API Gateway ──Eureka/lb://──► Spring services
                 │                     │
                 │ Redis JWT state     ├─ PostgreSQL per service
                 │                     ├─ RabbitMQ events
                 │                     └─ MinIO (media)
                 └─ X-User-* + optional X-Gateway-Auth
```

### Сервисные границы

| Сервис | Ответственность | Данные |
|---|---|---|
| identity-service :8081 | identity, sessions, users, applications, staff, audit | `topdim_identity`, Redis |
| coupon-service :8083 | coupons, merchants, categories, situations, reviews, Q&A, promo, directory copy | `topdim_coupon`, Redis |
| order-service :8084 | cart, orders, purchased coupons, redemption, refund, complaint | `topdim_order` |
| payment-service :8085 | payment state and callbacks/demo completion | `topdim_payment` |
| bazaar-service :8086 | standalone bazaars, maps and shops | `topdim_bazaar` |
| notification-service :8087 | in-app notifications and delivery adapters | `topdim_notification` |
| media-service :8088 | object upload/download/delete | MinIO |

Discovery server работает на 8761; config-server включён в Gradle, но централизованная конфигурация не подключена к сервисам или production CD.

## Коммуникация

Синхронно:

- order → coupon: purchase snapshot, merchant context, idempotent sale registration;
- order → identity: user and partner access;
- identity → coupon: merchant onboarding/location;
- coupon → order: review eligibility;
- bazaar → coupon: coupon details.

Асинхронно:

| Routing key | Producer | Consumer |
|---|---|---|
| `order.created` | order | payment |
| `payment.completed` | payment | order |
| `coupon.purchased` | order | notification |
| `coupon.redeemed` | order | coupon |
| `notification.sent` | order/coupon | notification |

## Данные и транзакции

Локальные операции используют `@Transactional`. Межсервисная согласованность eventual: база коммитится и событие публикуется отдельно. В коде нет outbox, saga coordinator или exactly-once транспорта. Идемпотентность точечно обеспечена unique constraints/ledgers.

## Кэш и фоновые процессы

Redis используется для JWT blacklist/security version/login attempts, gateway rate limiting и category/situation cache. Отдельный scheduler для expiration не найден; часть expiry применяется лениво. Elasticsearch контейнер есть в local compose, но search bean условный и не интегрирован в основной flow.

## Ошибки и trade-offs

Контроллеры обычно возвращают `ApiResponse<T>`, но directory и bot используют отличающиеся envelopes. Exception mapping неодинаков между сервисами. Микросервисное разделение изолирует данные, но отсутствие надёжной публикации событий создаёт риск «БД обновлена, событие потеряно».

Evidence:
- `infrastructure/api-gateway/src/main/resources/application.yml`
- `services/*/src/main/java/**/config/RabbitMQConfig.java`
- `services/*/src/main/java/**/service/`
- `shared/common-events/src/main/java/`
