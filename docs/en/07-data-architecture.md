# 07. Data architecture

## Ownership

Each stateful service has a separate PostgreSQL database; cross-database IDs are logical references, not foreign keys.

| Database | Main tables |
|---|---|
| `topdim_identity` | users, refresh_tokens, auth_action_tokens, favorites, staff, partner_applications, audit_logs |
| `topdim_coupon` | coupon_offers/options/images, merchants/locations, categories, reviews, promo_codes, situations, questions, sale/redemption ledgers, bazaars, shops |
| `topdim_order` | carts/items, orders/items, purchased_coupons, redemptions, refund_requests, complaints |
| `topdim_payment` | payments |
| `topdim_notification` | notifications |
| `topdim_bazaar` | shop_categories, bazaars, bazaar_maps, shops, shop_product_tags |

Media objects live in MinIO; Redis contains temporary security, rate-limit, and cache state.

## Relationships

- identity: user 1—N refresh/action tokens and favorites; owner 1—N staff; applications keep a logical linked merchant ID;
- coupon: merchant 1—N locations/offers; offer 1—N options/images/reviews/questions/sales/redemptions; situation N—M offer;
- order: user 1—1 active cart; cart/order 1—N items; order 1—N purchased coupons/refunds; purchased coupon 0—1 redemption;
- bazaar: bazaar 1—N maps/shops; category 1—N shops; shop 1—N tags.

See [database-overview.puml](diagrams/database-overview.puml).

## Migrations and constraints

Flyway scripts live under `services/*/src/main/resources/db/migration`. Identity, coupon, order, payment, and bazaar use `ddl-auto=validate`. notification-service combines Flyway with `ddl-auto=update`, which can cause uncontrolled schema drift.

Important constraints include unique identity fields and pending applications, one payment per order, one redemption per purchased coupon, sale/redemption idempotency ledgers, one pending complaint per coupon, soft-delete indexes, and PostgreSQL GIN search indexes.

`V6__partition_orders.sql` prepares a partitioned table but leaves cutover steps commented; the live `orders` table is not evidenced as partitioned.

## Consistency and lifecycle

Transactions are local to one service. Events provide eventual consistency without an outbox, so a commit can succeed while publication fails. Soft delete is not universal. No automated retention was found for tokens, notifications, audit, media, or business records.

Operational docs describe daily S3 dumps for five production databases with a 30-day lifecycle. The server script is outside this repository; MinIO, Redis/RabbitMQ, bazaar, and notification coverage must be verified operationally.

Sensitive data includes email, phone, names, Telegram identifiers, addresses, staff data, audit IP, password/token hashes, and payment amounts/provider references. Card data was not found.

Evidence:
- `docker/init-databases.sql`
- `services/*/src/main/resources/db/migration`
- `services/*/src/main/java/**/entity`
- `docs/PROJECT_STATE.md`
