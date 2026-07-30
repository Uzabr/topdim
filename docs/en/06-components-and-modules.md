# 06. Components and modules

## Inventory

| Component | Responsibility | Technology | Dependencies | Data ownership |
|---|---|---|---|---|
| api-gateway | routing, JWT, header sanitation, CORS, rate limits, headers | WebFlux/Gateway/Redis | Eureka, Redis | none |
| identity-service | identity, session, profile, staff, onboarding, audit | Spring MVC/JPA/Security | PostgreSQL, Redis, SMTP, coupon Feign | identity DB |
| coupon-service | offer, merchant, moderation, category, situation, review, Q&A | Spring MVC/JPA | PostgreSQL, Redis, RabbitMQ, order Feign | coupon DB |
| order-service | cart, order, purchased coupon, redemption, refund, complaint | Spring MVC/JPA | PostgreSQL, RabbitMQ, coupon/identity Feign | order DB |
| payment-service | payment state, demo/callback, events | Spring MVC/JPA | PostgreSQL, RabbitMQ | payment DB |
| notification-service | inbox and email/SMS adapters | Spring MVC/JPA | PostgreSQL, RabbitMQ, SMTP/HTTP | notification DB |
| media-service | object CRUD | Spring MVC, MinIO SDK | MinIO | bucket |
| bazaar-service | bazaars, maps, shops | Spring MVC/JPA | PostgreSQL, coupon Feign | bazaar DB |
| web-app | buyer experience | React/Vite | gateway, 2GIS | browser state |
| admin-app | moderation and administration | React/Ant Design | gateway | browser state |
| partner | merchant owner/cashier cabinet | React/Ant Design | gateway, camera | browser state |
| telegram-bot | concierge onboarding and approval | aiohttp | Telegram API, gateway/backend | in-memory TTL sessions |

## Backend internals

The gateway's `JwtAuthenticationFilter` removes external `X-User-*`, validates HMAC JWT, role whitelist, and Redis blacklist/securityVersion, then adds trusted headers. Rate limiting derives the address behind one trusted proxy hop. Redis rate limiting fails open; privileged token invalidation fails closed.

identity-service controllers cover auth, profile, applications, staff, admin/super, and internal access. Services implement BCrypt passwords, strong-password validation, SHA-256 refresh-token lookup, JTI/securityVersion invalidation, login-attempt counters, and merchant onboarding through coupon-service. A cross-database onboarding failure can leave partial state.

coupon-service separates public, partner, bot, internal, moderator, and admin surfaces. Services enforce the coupon state machine, merchant publication readiness, review eligibility, Q&A moderation, sale/redemption ledgers, and situation/category caching. It produces notification events, consumes redemption events, and calls order-service.

order-service centralizes cart, checkout, purchased-coupon materialization, and legacy redemption in `OrderService`; refund, complaint, partner statistics, and merchant resolution have focused services. It produces order/purchase/redemption/notification events and consumes payment completion.

payment-service consumes `order.created` and emits `payment.completed`; a unique order ID prevents duplicate payment rows. notification-service provides the inbox and two Rabbit listeners, but does not define listener retry/DLQ semantics. media-service validates and streams MinIO objects. bazaar-service owns a separate bazaar model but is not in current CD.

## Frontend

- `web-app`: localized buyer routes, catalog/directory/map, cart/checkout/payment, profile, refunds, complaints, reviews, notifications, and email confirmation. Axios implements single-flight refresh and account isolation.
- `admin-app`: protected moderation, coupon kanban/form, merchants, orders, support, applications, users, staff, and audit.
- `partner`: owner-only coupon/staff pages plus shared dashboard/redemption; QR scanning uses `html5-qrcode`.

## Tests

Backend tests mainly use JUnit 5/Mockito. Coupon repository and order migration tests use Testcontainers/PostgreSQL. The buyer app uses Vitest/Testing Library. Admin and partner packages do not define automated test scripts.

Evidence:
- `services/*/src/main/java`
- `services/*/src/test/java`
- `frontend/*/src`
- `telegram-bot/src`
