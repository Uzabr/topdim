# 07. Архитектура данных

## Владение

Каждый stateful сервис использует отдельную PostgreSQL database; между базами нет FK. Межсервисные ID — логические ссылки.

| База | Основные таблицы |
|---|---|
| `topdim_identity` | users, refresh_tokens, auth_action_tokens, favorites, staff, partner_applications, audit_logs |
| `topdim_coupon` | coupon_offers, coupon_options, coupon_images, merchants, merchant_locations, categories, reviews, promo_codes, situations, situation_coupons, coupon_questions, coupon_sales, coupon_redemption_ledger, bazaars, shops |
| `topdim_order` | carts, cart_items, orders, order_items, purchased_coupons, redemptions, refund_requests, complaints |
| `topdim_payment` | payments |
| `topdim_notification` | notifications |
| `topdim_bazaar` | shop_categories, bazaars, bazaar_maps, shops, shop_product_tags |

Media-service владеет объектами в MinIO bucket, а не SQL. Redis хранит временное security/rate-limit/cache состояние.

## Главные связи

- identity: user 1—N refresh tokens/action tokens/favorites; owner user 1—N staff; application хранит logical linked merchant ID.
- coupon: merchant 1—N locations/offers; offer 1—N options/images/reviews/questions/sales/redemption ledger; situation N—M offer.
- order: user 1—1 active cart; cart 1—N items; order 1—N items/purchased coupons/refunds; purchased coupon 0—1 redemption и N? business support links.
- bazaar: bazaar 1—N maps/shops; category 1—N shops; shop 1—N tags.

См. [database-overview.puml](diagrams/database-overview.puml).

## Миграции и индексы

Flyway migrations находятся в `services/*/src/main/resources/db/migration`. JPA у identity/coupon/order/payment/bazaar настроен на `ddl-auto=validate`. notification-service ошибочно использует `ddl-auto=update` одновременно с Flyway — это риск неконтролируемого schema drift.

Критичные ограничения:

- unique email/phone/Telegram identity и pending partner application;
- unique payment order ID;
- unique purchased coupon redemption;
- unique idempotency ledgers для sale/redemption;
- unique pending complaint per purchased coupon;
- partial indexes для soft-delete и активных состояний;
- PostgreSQL GIN full-text indexes для coupon/bazaar поиска.

`V6__partition_orders.sql` создаёт подготовительную partitioned table, но комментарии оставляют cutover незавершённым; текущая `orders` не подтверждена как partitioned.

## Согласованность и lifecycle

Транзакции ограничены одним сервисом. События обеспечивают eventual consistency, но без outbox возможна потеря публикации после commit. Soft delete применяется не везде. Автоматическая очистка refresh/action tokens, notifications, audit, media и business data не обнаружена.

Backups: operational docs описывают ежедневный `pg_dump` пяти production DB в S3 с 30-дневным lifecycle. Это не покрывает MinIO, Redis/RabbitMQ или bazaar/notification, если их нет в перечисленных пяти dumps; фактический server script не находится в репозитории и требует operational verification.

## Чувствительные данные

PII: email, phone, names, Telegram identifiers, addresses, staff data, IP in audit. Security-sensitive: password hashes, token hashes, JWT invalidation state. Payment rows содержат суммы/provider references, но card data в entity/migrations не обнаружены.

Evidence:
- `docker/init-databases.sql`
- `services/*/src/main/resources/db/migration`
- `services/*/src/main/java/**/entity`
- `docs/PROJECT_STATE.md`
