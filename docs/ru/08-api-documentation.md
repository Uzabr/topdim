# 08. API

## Общие правила

Base URL local: `http://localhost:8080`. Большинство ответов:

```json
{"success":true,"message":"optional","data":{}}
```

Protected endpoints принимают `Authorization: Bearer <access-token>`. Gateway удаляет клиентские `X-User-*` и генерирует их из JWT. Validation обычно даёт 400, отсутствующий ресурс — 404, конфликт бизнес-состояния — 409, отсутствие/невалидный token — 401, роль — 403; exception mapping не полностью унифицирован.

Swagger UI доступен напрямую на портах сервисов: `/swagger-ui.html`; OpenAPI JSON — `/v3/api-docs`. Gateway не маршрутизирует Swagger.

Обозначения: `B:` body DTO; `Q:` query; `P:` path; `R:` основной response DTO.

## Public/auth API

| Method/path | Назначение и контракт | Auth | Успех |
|---|---|---|---|
| POST `/api/v1/auth/register` | B `RegisterRequest`; R `AuthResponse` | public | 201 |
| POST `/api/v1/auth/login` | B `LoginRequest`; R `AuthResponse` | public | 200 |
| POST `/api/v1/auth/refresh` | refresh token в body/header contract контроллера; R `AuthResponse` | public | 200 |
| POST `/api/v1/auth/logout` | access + refresh token; revoke | token optional at gateway, service contract | 200 |
| PUT `/api/v1/auth/change-password` | B `ChangePasswordRequest` | USER+ | 200 |
| POST `/api/v1/auth/guest` | B `GuestAuthRequest`; R `AuthResponse` | public | 200 |
| POST `/api/v1/auth/telegram` | B `TelegramAuthRequest`; R `AuthResponse` | public | 200 |
| POST `/api/v1/auth/password-reset/request` | B `PasswordResetRequest` | public | 200 |
| POST `/api/v1/auth/password-reset/confirm` | B `PasswordResetConfirmRequest` | public | 200 |
| POST `/api/v1/auth/confirm/request` | send confirmation | USER+ | 200 |
| POST `/api/v1/auth/confirm/email` | B `EmailConfirmRequest` | public | 200 |
| GET `/api/v1/coupons` | Q page,size,categoryId,search,sortBy; R page offer | public | 200 |
| GET `/api/v1/coupons/{id}` | P id; R offer | public | 200 |
| GET `/api/v1/coupons/top-selling` | Q limit; R offers | public | 200 |
| GET `/api/v1/categories` | R categories | public | 200 |
| GET `/api/v1/situations` | R active curated situations | public | 200 |
| GET `/api/v1/reviews/coupon/{id}` | Q page,size; approved reviews | public | 200 |
| GET `/api/v1/questions/coupon/{id}` | Q page,size; published Q&A | public | 200 |
| POST `/api/v1/partners/applications` | B `PartnerApplicationRequest` | public | 200 |
| GET `/api/v1/media/{fileName}` | object stream/content type | public | 200/404 |

## Buyer/profile API

| Method/path | Contract | Auth |
|---|---|---|
| GET/PUT `/api/v1/users/me` | R profile / B `UpdateProfileRequest` | USER+ |
| GET/POST `/api/v1/users/me/favorites` | list / B `{couponOfferId}` | USER+ |
| DELETE `/api/v1/users/me/favorites/{couponOfferId}` | remove favorite | USER+ |
| GET `/api/v1/cart` | R cart | USER+ |
| POST `/api/v1/cart/items` | B option/quantity/gift data | USER+ |
| PATCH/DELETE `/api/v1/cart/items/{itemId}` | B quantity / remove | USER+ |
| DELETE `/api/v1/cart` | clear | USER+ |
| POST `/api/v1/orders` | B checkout email/phone; R order | USER+ |
| GET `/api/v1/orders` | Q page,size; R own orders | USER+ |
| GET `/api/v1/orders/{id}` | owned order | USER+ |
| GET `/api/v1/orders/my-coupons` | Q status; R purchased coupons | USER+ |
| GET `/api/v1/orders/{orderId}/coupons` | purchased coupons in owned order | USER+ |
| POST `/api/v1/refunds` | B purchasedCouponId,reason | USER+ |
| GET `/api/v1/refunds/my` | own refund requests | USER+ |
| POST `/api/v1/complaints` | B purchasedCouponId,reason/description | USER+ |
| GET `/api/v1/complaints/my` | Q page,size | USER+ |
| POST `/api/v1/reviews` | B `CreateReviewRequest`; 409 if duplicate/ineligible | USER+ |
| GET `/api/v1/reviews/my` | Q page,size | USER+ |
| GET `/api/v1/reviews/coupon/{id}/eligibility` | R eligibility | USER+ |
| POST `/api/v1/questions` | B `CreateQuestionRequest` | USER+ |
| GET `/api/v1/questions/my` | Q page,size | USER+ |
| GET `/api/v1/notifications` | Q unreadOnly,page,size | USER+ |
| PATCH `/api/v1/notifications/{id}/read` | ownership checked | USER+ |
| POST `/api/v1/media/upload` | multipart `file`, max 20 MB and allowed types | USER+ |
| DELETE `/api/v1/media/{fileName}` | delete object | authenticated |

## Payment API

| Method/path | Contract | Auth/notes |
|---|---|---|
| POST `/api/v1/payments/create` | B orderId,amount,provider; R payment | authenticated |
| GET `/api/v1/payments/{id}/status` | R payment | authenticated |
| GET `/api/v1/payments/order/{orderId}` | R payment | authenticated |
| POST `/api/v1/payments/callback` | provider callback payload | routed; provider verification incomplete |
| POST `/api/v1/payments/order/{orderId}/demo-complete` | complete demo payment | authenticated; available only when mode=demo |

## Partner API

| Method/path | Contract |
|---|---|
| GET `/api/v1/partner/staff/me` | current owner/cashier access context |
| GET/POST `/api/v1/partner/staff` | list / B `CreateStaffRequest` |
| DELETE `/api/v1/partner/staff/{id}` | deactivate/remove owned staff |
| GET `/api/v1/partner/merchant` | own merchant |
| GET `/api/v1/partner/merchant/locations` | own active locations |
| GET/POST `/api/v1/partner/coupons` | Q status,page,size / B `CreatePartnerCouponRequest` |
| GET/PUT `/api/v1/partner/coupons/{id}` | owned offer / update mutable state |
| POST `/api/v1/partner/coupons/{id}/approve` | `WAITING_FOR_MERCHANT → ACTIVE` |
| POST `/api/v1/partner/coupons/{id}/request-revision` | B comment |
| POST `/api/v1/partner/redemptions` | B couponCode; R redemption |
| POST `/api/v1/partner/redemptions/qr` | B qrToken; R redemption |
| GET `/api/v1/partner/redemptions` | Q page,size, filters; history |
| GET `/api/v1/partner/stats` | aggregate totals |
| GET `/api/v1/partner/dashboard` | dashboard aggregate |
| GET/GET/PUT `/api/v1/partner/shops[/{id}]` | list/detail/update owned shop; bazaar-service |

Все partner endpoints требуют `PARTNER`, `ADMIN` или `SUPER_ADMIN`, кроме legacy `/api/v1/orders/redeem`, который явно требует `PARTNER`.

## Moderator/admin API

| Surface | Endpoints |
|---|---|
| Coupons | GET/POST `/api/v1/admin/coupons`; GET/PUT/DELETE `/{id}`; PATCH `/{id}/status`, `/{id}/take-to-work`; POST `/{id}/archive`, `send-to-approval`, `reject-request` |
| Moderation | GET `/api/v1/mod/coupons`, PATCH `/{id}/review`; GET `/api/v1/mod/reviews`, PATCH `/{id}/review` |
| Q&A | GET `/api/v1/mod/questions`; PATCH `/{id}/answer`, `/{id}/reject` |
| Merchants | GET `/api/v1/admin/merchants`, `/page`, `/{id}`, `/{id}/coupons`; POST base; PUT `/{id}`; PATCH `/{id}/active` |
| Categories | POST `/api/v1/admin/categories`; POST `/upload` (Excel) |
| Situations | GET/POST `/api/v1/admin/situations`; GET/PUT/DELETE `/{id}`; PUT/DELETE `/{id}/coupons` |
| Promo codes | GET/POST `/api/v1/admin/promocodes` |
| Dashboard | GET `/api/v1/admin/dashboard` — заказы/выручка сегодня, ожидающие жалобы, продажи за 7 дней, последние заказы |
| Orders | GET `/api/v1/admin/orders`, `/{id}`, `/purchased-coupons/lookup` |
| Refunds | GET `/api/v1/admin/refunds`; PATCH `/{id}/approve`, `reject`, `complete`; legacy PATCH `/{id}` |
| Complaints | GET `/api/v1/mod/complaints`; PATCH `/{id}/resolve` |
| Users/applications | GET `/api/v1/admin/users[/{id}]`; PATCH `/{id}/block`; GET applications; PATCH approve/reject |
| Super | GET `/api/v1/super/staff`, `/audit-logs`; POST `/admins`; DELETE `/admins/{id}`; PATCH `/users/{id}/role|block` |

DTO/validation details являются частью generated OpenAPI и классов `dto`. Роли дополнительно проверяются `@PreAuthorize`.

## Internal/integration API

| Endpoint | Caller | Protection |
|---|---|---|
| GET `/api/v1/internal/coupons/{couponId}/options/{optionId}/purchase-snapshot` | order | internal gateway secret header when configured |
| POST `/api/v1/internal/coupons/{couponId}/options/{optionId}/sales` | order | same; idempotent by order |
| GET `/api/v1/internal/merchants/by-user/{userId}` | order/identity | same |
| GET `/api/v1/internal/merchants/by-user/{userId}/locations` | identity | same |
| POST `/api/v1/internal/merchants/onboarding` | identity | same |
| GET `/api/v1/internal/partner-access/{userId}` | order | same |
| GET `/api/v1/internal/reviews/eligibility` | coupon | same |
| POST `/api/v1/bot/coupons/{id}/approve|reject` | Telegram bot | `X-Bot-Api-Key` |
| POST `/api/v1/bot/coupons/leads` | bot | `X-Bot-Api-Key` |
| GET `/api/v1/bot/coupons/merchants/{chatId}` | bot | `X-Bot-Api-Key` |
| GET `/api/v1/bot/coupons/{id}/stats` | bot | `X-Bot-Api-Key` |

Internal paths не маршрутизируются публичным gateway, но downstream `permitAll` означает, что network isolation и непустой `INTERNAL_AUTH_SECRET` обязательны.

## Directory and bazaar API

coupon-service имеет `/api/v1/directory/bazaars`, `/{id}`, `/{id}/shops`, `/shops`, `/shops/{id}`, `/area`; admin создаёт/обновляет `/api/v1/admin/bazaars|shops`. Bazaar-service параллельно имеет `/api/v1/bazaars`, `/shops`, maps и admin routes.

**Текущий дефект интеграции:** gateway не содержит `/api/v1/directory/**`, хотя `web-app` вызывает именно этот contract. Через public gateway эти запросы не достигают coupon-service.

## Точный реестр сгруппированных путей

Ниже раскрыты exact paths, сокращённые в предыдущих таблицах. Request/response DTO, роли и назначение наследуются от соответствующей поверхности выше.

| Поверхность | Method + exact path |
|---|---|
| Bazaar public | GET `/api/v1/bazaars/{id}`; GET `/api/v1/bazaars/{id}/map`; GET `/api/v1/bazaars/{id}/shops`; GET `/api/v1/shops/{id}`; GET `/api/v1/shops/search`; GET `/api/v1/shops/categories` |
| Bazaar admin | POST `/api/v1/admin/bazaars`; PUT `/api/v1/admin/bazaars/{id}`; POST `/api/v1/admin/shops`; PUT `/api/v1/admin/shops/{id}`; POST `/api/v1/admin/bazaars/{id}/maps` |
| Bazaar partner | GET `/api/v1/partner/shops/{id}`; PUT `/api/v1/partner/shops/{id}` |
| Category import | POST `/api/v1/admin/categories/upload` |
| Admin coupons | GET `/api/v1/admin/coupons/{id}`; PUT `/api/v1/admin/coupons/{id}`; PATCH `/api/v1/admin/coupons/{id}/status`; DELETE `/api/v1/admin/coupons/{id}`; POST `/api/v1/admin/coupons/{id}/archive`; POST `/api/v1/admin/coupons/{id}/send-to-approval`; POST `/api/v1/admin/coupons/{id}/reject-request`; PATCH `/api/v1/admin/coupons/{id}/take-to-work` |
| Admin merchants | GET `/api/v1/admin/merchants`; GET `/api/v1/admin/merchants/page`; GET `/api/v1/admin/merchants/{id}`; POST `/api/v1/admin/merchants`; PUT `/api/v1/admin/merchants/{id}`; PATCH `/api/v1/admin/merchants/{id}/active`; GET `/api/v1/admin/merchants/{id}/coupons` |
| Coupon directory admin | POST `/api/v1/admin/bazaars`; PUT `/api/v1/admin/bazaars/{id}`; POST `/api/v1/admin/shops`; PUT `/api/v1/admin/shops/{id}` |
| Situations admin | GET `/api/v1/admin/situations`; GET `/api/v1/admin/situations/{id}`; POST `/api/v1/admin/situations`; PUT `/api/v1/admin/situations/{id}`; DELETE `/api/v1/admin/situations/{id}`; PUT `/api/v1/admin/situations/{id}/coupons`; DELETE `/api/v1/admin/situations/{id}/coupons` |
| Bot | POST `/api/v1/bot/coupons/{id}/reject`; GET `/api/v1/bot/coupons/merchants/{chatId}`; GET `/api/v1/bot/coupons/{id}/stats` |
| Coupon directory public | GET `/api/v1/directory/bazaars/{id}`; GET `/api/v1/directory/bazaars/{id}/shops`; GET `/api/v1/directory/shops`; GET `/api/v1/directory/shops/{id}`; GET `/api/v1/directory/area` |
| Moderation exact | PATCH `/api/v1/mod/coupons/{id}/review`; PATCH `/api/v1/mod/reviews/{id}/review`; PATCH `/api/v1/mod/questions/{id}/answer`; PATCH `/api/v1/mod/questions/{id}/reject`; PATCH `/api/v1/mod/complaints/{id}/resolve` |
| UGC exact | GET `/api/v1/questions/coupon/{offerId}`; GET `/api/v1/reviews/coupon/{couponId}`; GET `/api/v1/reviews/coupon/{couponId}/eligibility` |
| Partner applications | GET `/api/v1/admin/partner-applications`; GET `/api/v1/admin/partner-applications/{id}`; PATCH `/api/v1/admin/partner-applications/{id}/approve`; PATCH `/api/v1/admin/partner-applications/{id}/reject` |
| Admin users | GET `/api/v1/admin/users`; GET `/api/v1/admin/users/{id}`; PATCH `/api/v1/admin/users/{id}/block` |
| Super admin | GET `/api/v1/super/staff`; POST `/api/v1/super/admins`; DELETE `/api/v1/super/admins/{id}`; PATCH `/api/v1/super/users/{id}/role`; PATCH `/api/v1/super/users/{id}/block`; GET `/api/v1/super/audit-logs` |
| Legacy refunds | POST `/api/v1/orders/{orderId}/refund`; GET `/api/v1/orders/refunds` |
| Admin orders/refunds | GET `/api/v1/admin/orders/{id}`; GET `/api/v1/admin/refunds`; PATCH `/api/v1/admin/refunds/{id}/approve`; PATCH `/api/v1/admin/refunds/{id}/reject`; PATCH `/api/v1/admin/refunds/{id}/complete`; PATCH `/api/v1/admin/refunds/{id}`; GET `/api/v1/admin/purchased-coupons/lookup` |

Важно: одинаковые admin bazaar/shop paths реализованы и coupon-service, и bazaar-service; gateway направляет их в bazaar-service, поэтому coupon-service реализации недостижимы через gateway.

## Примеры

```bash
curl -sS 'http://localhost:8080/api/v1/coupons?page=0&size=20'
curl -sS -H 'Authorization: Bearer <access-token>' \
  'http://localhost:8080/api/v1/orders/my-coupons'
```

```json
{"success":false,"message":"Business rule violation","data":null}
```

Evidence:
- `services/*/src/main/java/**/controller`
- `services/*/src/main/java/**/dto`
- `infrastructure/api-gateway/src/main/resources/application.yml`
- `infrastructure/api-gateway/src/main/java/uz/topdim/gateway/filter/JwtAuthenticationFilter.java`
