# 04. Architecture overview

## Runtime shape

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

| Service | Responsibility | Owned state |
|---|---|---|
| identity :8081 | identity, sessions, users, applications, staff, audit | `topdim_identity`, Redis |
| coupon :8083 | coupons, merchants, categories, situations, reviews, Q&A, promo, directory copy | `topdim_coupon`, Redis |
| order :8084 | cart, orders, purchased coupons, redemption, refund, complaint | `topdim_order` |
| payment :8085 | payment state and callbacks/demo completion | `topdim_payment` |
| bazaar :8086 | standalone bazaars, maps, shops | `topdim_bazaar` |
| notification :8087 | in-app notifications and delivery adapters | `topdim_notification` |
| media :8088 | object upload/read/delete | MinIO |

Discovery runs on 8761. Config Server is included in Gradle but not consumed by services or the production CD path.

## Communication

Synchronous OpenFeign calls cover order→coupon, order→identity, identity→coupon, coupon→order, and bazaar→coupon contracts. RabbitMQ carries `order.created`, `payment.completed`, `coupon.purchased`, `coupon.redeemed`, and `notification.sent`.

Local operations use `@Transactional`; cross-service consistency is eventual. Database commits and event publishing are separate, with no outbox or saga coordinator. Unique constraints and ledgers provide operation-specific idempotency.

Redis supports JWT blacklist/security version/login attempts, rate limiting, and selected category/situation caches. Expiration is partly lazy; no dedicated expiry scheduler was found. Elasticsearch is optional and disconnected from the primary catalog flow.

Controllers usually return `ApiResponse<T>`, but directory and bot endpoints have different envelopes. Exception mapping is not uniform.

Evidence:
- `infrastructure/api-gateway/src/main/resources/application.yml`
- `services/*/src/main/java/**/config/RabbitMQConfig.java`
- `services/*/src/main/java/**/service/`
- `shared/common-events/src/main/java/`
