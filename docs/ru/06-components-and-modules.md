# 06. Компоненты и модули

## Сводная таблица

| Компонент | Ответственность | Технология | Зависимости | Владение данными |
|---|---|---|---|---|
| api-gateway | routing, JWT, header sanitation, CORS, rate limit, security headers | WebFlux/Gateway/Redis | Eureka, Redis | нет |
| identity-service | identity/session/profile/staff/onboarding/audit | Spring MVC/JPA/Security | PostgreSQL, Redis, SMTP, coupon Feign | identity DB |
| coupon-service | offers, merchant, moderation, category, situation, review, Q&A | Spring MVC/JPA | PostgreSQL, Redis, RabbitMQ, order Feign | coupon DB |
| order-service | cart/order/purchased coupon/redemption/refund/complaint | Spring MVC/JPA | PostgreSQL, RabbitMQ, coupon/identity Feign | order DB |
| payment-service | payment state, demo/callback, payment events | Spring MVC/JPA | PostgreSQL, RabbitMQ | payment DB |
| notification-service | notification inbox, email/SMS adapters | Spring MVC/JPA | PostgreSQL, RabbitMQ, SMTP/HTTP | notification DB |
| media-service | object CRUD | Spring MVC, MinIO SDK | MinIO | bucket |
| bazaar-service | bazaars/maps/shops | Spring MVC/JPA | PostgreSQL, coupon Feign | bazaar DB |
| web-app | buyer experience | React/Vite | gateway, 2GIS | browser state |
| admin-app | moderation/administration | React/Ant Design | gateway | browser state |
| partner | merchant owner/cashier cabinet | React/Ant Design | gateway, camera | browser state |
| telegram-bot | concierge onboarding and approval | aiohttp | Telegram API, gateway/backend | in-memory TTL sessions |

## Backend

### API Gateway

Entry point: `JwtAuthenticationFilter`. Он удаляет внешние `X-User-*`, проверяет HMAC JWT, role whitelist, Redis blacklist/securityVersion, затем добавляет trusted headers. `SecurityHeadersFilter` задаёт HTTP headers, `RateLimitConfig` выбирает IP с одним доверенным proxy hop.

Failure modes: Redis rate limiter fail-open; privileged JWT invalidation fail-closed; отсутствующая route даёт 404. Тесты: gateway filter, token validation, rate limiter.

### identity-service

- Controllers: `AuthController`, `UserController`, `PartnerApplicationController`, admin/super/staff/internal controllers.
- Services: `AuthService`, `UserService`, `PasswordResetService`, `EmailConfirmationService`, `PartnerApplicationService`, `PartnerStaffService`, `SuperAdminService`.
- Security: BCrypt, strong-password validator, SHA-256 refresh-token lookup, JWT JTI + securityVersion, login attempt counters.
- Integration: synchronous merchant onboarding into coupon-service; SMTP with logging fallback.
- Failure modes: duplicate identity, expired/revoked token, deleted/blocked user, mail failure, partial onboarding across two databases.

### coupon-service

- Controllers split public, partner, bot, internal, moderator, and admin surfaces.
- Services enforce coupon state transitions, merchant publication readiness, review eligibility, question moderation, idempotent sale/redemption ledgers, situation caching.
- Repositories own offers/options/images, merchants/locations, categories, reviews, questions, promotions, situations, and a duplicate directory model.
- Produces `notification.sent`; consumes `coupon.redeemed`; calls order-service for review eligibility.
- Failure modes: invalid transition, missing merchant/category, concurrent stock update, Telegram preview failure, cross-service eligibility outage.

### order-service

- `OrderService` implements cart, checkout, purchased-coupon generation, legacy redemption, notification emission.
- `CouponRefundService`, `ComplaintService`, `PartnerService`, `PartnerMerchantResolver` separate support and merchant access rules.
- Produces order, purchase, redemption, notification events; consumes payment completion.
- Failure modes: stale coupon snapshot, empty cart, duplicate payment event, stock registration after order commit, unauthorized merchant/location, duplicate refund/complaint.

### Supporting services

- payment-service: `PaymentController` → `PaymentService` → `PaymentRepository`; consumes `order.created`, publishes `payment.completed`. Unique order ID prevents duplicate payment rows.
- notification-service: REST inbox plus two Rabbit listeners; adapters may run in stub/real mode. Listener exception handling does not define retries/DLQ.
- media-service: single controller validates size/type/name, streams from MinIO; GET is public, mutations authenticated.
- bazaar-service: controllers/services/repositories for bazaar/shop/map and partner-owned shops. It is not built/deployed by current CD.

## Frontend

- `web-app`: localized routes (`/:lang`), catalog, directory/map, cart/checkout/payment, profile, coupons, refunds, complaints, reviews, notifications, email confirmation. Axios performs single-flight refresh and account-isolation cleanup.
- `admin-app`: role-protected moderation, coupon kanban/form, merchants, orders, refunds, complaints, reviews, partner applications, users, staff, audit.
- `partner`: owner-only coupon/staff pages and shared redemption dashboard; QR uses `html5-qrcode`.

## Testing approach

Backend primarily uses JUnit 5/Mockito; coupon repository and order migration tests use Testcontainers/PostgreSQL. Buyer frontend uses Vitest/Testing Library. No automated test script exists in admin/partner packages.

Evidence:
- `services/*/src/main/java`
- `services/*/src/test/java`
- `frontend/*/src`
- `telegram-bot/src`
