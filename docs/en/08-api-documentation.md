# 08. API documentation

## Conventions

Local base URL is `http://localhost:8080`. Most endpoints return:

```json
{"success":true,"message":"optional","data":{}}
```

Protected calls use `Authorization: Bearer <access-token>`. The gateway removes client-supplied `X-User-*` and derives trusted values from JWT. Validation normally maps to 400, missing resources to 404, state conflicts to 409, invalid/missing authentication to 401, and insufficient role to 403; exception mapping is not fully uniform.

Each service exposes `/swagger-ui.html` and `/v3/api-docs` on its direct port. Swagger is not routed by the gateway.

`B` means body DTO, `Q` query, `P` path, and `R` primary response DTO.

## Public and authentication

| Method/path | Purpose and contract | Auth | Success |
|---|---|---|---|
| POST `/api/v1/auth/register` | B `RegisterRequest`; R `AuthResponse` | public | 201 |
| POST `/api/v1/auth/login` | B `LoginRequest`; R `AuthResponse` | public | 200 |
| POST `/api/v1/auth/refresh` | refresh token contract; R `AuthResponse` | public | 200 |
| POST `/api/v1/auth/logout` | revoke refresh/access session | gateway-open, service contract | 200 |
| PUT `/api/v1/auth/change-password` | B `ChangePasswordRequest` | USER+ | 200 |
| POST `/api/v1/auth/guest` | B `GuestAuthRequest`; R `AuthResponse` | public | 200 |
| POST `/api/v1/auth/telegram` | B `TelegramAuthRequest`; R `AuthResponse` | public | 200 |
| POST `/api/v1/auth/password-reset/request` | B `PasswordResetRequest` | public | 200 |
| POST `/api/v1/auth/password-reset/confirm` | B `PasswordResetConfirmRequest` | public | 200 |
| POST `/api/v1/auth/confirm/request` | send verification token | USER+ | 200 |
| POST `/api/v1/auth/confirm/email` | B `EmailConfirmRequest` | public | 200 |
| GET `/api/v1/coupons` | Q page,size,categoryId,search,sortBy; offer page | public | 200 |
| GET `/api/v1/coupons/{id}` | P id; R offer | public | 200 |
| GET `/api/v1/coupons/top-selling` | Q limit | public | 200 |
| GET `/api/v1/categories` | category list | public | 200 |
| GET `/api/v1/situations` | active curated situations | public | 200 |
| GET `/api/v1/reviews/coupon/{id}` | Q page,size; approved reviews | public | 200 |
| GET `/api/v1/questions/coupon/{id}` | Q page,size; published Q&A | public | 200 |
| POST `/api/v1/partners/applications` | B `PartnerApplicationRequest` | public | 200 |
| GET `/api/v1/media/{fileName}` | object stream/content type | public | 200/404 |

## Buyer and profile

| Method/path | Contract | Auth |
|---|---|---|
| GET/PUT `/api/v1/users/me` | profile / B `UpdateProfileRequest` | USER+ |
| GET/POST `/api/v1/users/me/favorites` | list / B `{couponOfferId}` | USER+ |
| DELETE `/api/v1/users/me/favorites/{couponOfferId}` | remove | USER+ |
| GET `/api/v1/cart` | cart | USER+ |
| POST `/api/v1/cart/items` | B option, quantity, gift data | USER+ |
| PATCH/DELETE `/api/v1/cart/items/{itemId}` | B quantity / remove | USER+ |
| DELETE `/api/v1/cart` | clear | USER+ |
| POST `/api/v1/orders` | B checkout email/phone; R order | USER+ |
| GET `/api/v1/orders` | Q page,size; own orders | USER+ |
| GET `/api/v1/orders/{id}` | owned order | USER+ |
| GET `/api/v1/orders/my-coupons` | Q status | USER+ |
| GET `/api/v1/orders/{orderId}/coupons` | owned purchased coupons | USER+ |
| POST `/api/v1/refunds` | B purchasedCouponId, reason | USER+ |
| GET `/api/v1/refunds/my` | own refunds | USER+ |
| POST `/api/v1/complaints` | B purchasedCouponId and reason/description | USER+ |
| GET `/api/v1/complaints/my` | Q page,size | USER+ |
| POST `/api/v1/reviews` | B `CreateReviewRequest`; 409 duplicate/ineligible | USER+ |
| GET `/api/v1/reviews/my` | Q page,size | USER+ |
| GET `/api/v1/reviews/coupon/{id}/eligibility` | eligibility | USER+ |
| POST `/api/v1/questions` | B `CreateQuestionRequest` | USER+ |
| GET `/api/v1/questions/my` | Q page,size | USER+ |
| GET `/api/v1/notifications` | Q unreadOnly,page,size | USER+ |
| PATCH `/api/v1/notifications/{id}/read` | ownership checked | USER+ |
| POST `/api/v1/media/upload` | multipart `file`, max 20 MB/type validation | USER+ |
| DELETE `/api/v1/media/{fileName}` | delete object | authenticated |

## Payment

| Method/path | Contract | Notes |
|---|---|---|
| POST `/api/v1/payments/create` | B orderId, amount, provider; R payment | authenticated |
| GET `/api/v1/payments/{id}/status` | payment | authenticated |
| GET `/api/v1/payments/order/{orderId}` | payment | authenticated |
| POST `/api/v1/payments/callback` | provider callback | provider verification incomplete |
| POST `/api/v1/payments/order/{orderId}/demo-complete` | complete demo payment | authenticated; demo mode only |

## Partner

| Method/path | Contract |
|---|---|
| GET `/api/v1/partner/staff/me` | owner/cashier access context |
| GET/POST `/api/v1/partner/staff` | list / B `CreateStaffRequest` |
| DELETE `/api/v1/partner/staff/{id}` | deactivate/remove owned staff |
| GET `/api/v1/partner/merchant` | own merchant |
| GET `/api/v1/partner/merchant/locations` | active locations |
| GET/POST `/api/v1/partner/coupons` | Q status,page,size / B `CreatePartnerCouponRequest` |
| GET/PUT `/api/v1/partner/coupons/{id}` | owned offer / update |
| POST `/api/v1/partner/coupons/{id}/approve` | `WAITING_FOR_MERCHANT → ACTIVE` |
| POST `/api/v1/partner/coupons/{id}/request-revision` | B comment |
| POST `/api/v1/partner/redemptions` | B couponCode |
| POST `/api/v1/partner/redemptions/qr` | B qrToken |
| GET `/api/v1/partner/redemptions` | Q page,size and filters |
| GET `/api/v1/partner/stats` | totals |
| GET `/api/v1/partner/dashboard` | dashboard |
| GET/GET/PUT `/api/v1/partner/shops[/{id}]` | bazaar-service owned shops |

Partner endpoints allow `PARTNER`, `ADMIN`, or `SUPER_ADMIN`, except the legacy `/api/v1/orders/redeem`, which explicitly requires `PARTNER`.

## Moderator and administrator

| Surface | Endpoints |
|---|---|
| Coupons | GET/POST `/api/v1/admin/coupons`; GET/PUT/DELETE `/{id}`; PATCH status/take-to-work; POST archive/send-to-approval/reject-request |
| Moderation | GET `/api/v1/mod/coupons`, PATCH review; GET `/api/v1/mod/reviews`, PATCH review |
| Q&A | GET `/api/v1/mod/questions`; PATCH answer/reject |
| Merchants | GET base/page/id/id-coupons; POST base; PUT id; PATCH active |
| Categories | POST `/api/v1/admin/categories`; POST `/upload` |
| Situations | GET/POST base; GET/PUT/DELETE id; PUT/DELETE id/coupons |
| Promo codes | GET/POST `/api/v1/admin/promocodes` |
| Orders | GET `/api/v1/admin/orders`, id, purchased-coupons lookup |
| Refunds | GET base; PATCH approve/reject/complete; legacy PATCH id |
| Complaints | GET `/api/v1/mod/complaints`; PATCH resolve |
| Users/applications | GET users and id; PATCH block; list/detail/approve/reject applications |
| Super | GET staff/audit; POST admins; DELETE admin; PATCH role/block |

DTO validation is defined in `dto` classes and generated OpenAPI. Roles are also enforced by `@PreAuthorize`.

## Internal and integration

| Endpoint | Caller | Protection |
|---|---|---|
| GET `/api/v1/internal/coupons/{couponId}/options/{optionId}/purchase-snapshot` | order | gateway secret when configured |
| POST `/api/v1/internal/coupons/{couponId}/options/{optionId}/sales` | order | same; idempotent |
| GET `/api/v1/internal/merchants/by-user/{userId}` | order/identity | same |
| GET `/api/v1/internal/merchants/by-user/{userId}/locations` | identity | same |
| POST `/api/v1/internal/merchants/onboarding` | identity | same |
| GET `/api/v1/internal/partner-access/{userId}` | order | same |
| GET `/api/v1/internal/reviews/eligibility` | coupon | same |
| POST `/api/v1/bot/coupons/{id}/approve|reject` | bot | `X-Bot-Api-Key` |
| POST `/api/v1/bot/coupons/leads` | bot | `X-Bot-Api-Key` |
| GET bot merchant/stats endpoints | bot | `X-Bot-Api-Key` |

Internal routes are not exposed through the gateway, but downstream `permitAll` makes network isolation and a non-empty `INTERNAL_AUTH_SECRET` mandatory.

## Directory split

coupon-service exposes `/api/v1/directory/bazaars`, bazaar details/shops, `/shops`, shop details, and `/area`, plus admin create/update. bazaar-service independently exposes `/api/v1/bazaars`, `/shops`, maps, and admin routes.

**Current integration defect:** the gateway does not route `/api/v1/directory/**`, while `web-app` calls that contract.

## Exact grouped-path inventory

The following expands exact paths compressed in the preceding tables. Request/response DTOs, roles, and purpose inherit from their surface above.

| Surface | Method and exact path |
|---|---|
| Bazaar public | GET `/api/v1/bazaars/{id}`; GET `/api/v1/bazaars/{id}/map`; GET `/api/v1/bazaars/{id}/shops`; GET `/api/v1/shops/{id}`; GET `/api/v1/shops/search`; GET `/api/v1/shops/categories` |
| Bazaar admin | POST `/api/v1/admin/bazaars`; PUT `/api/v1/admin/bazaars/{id}`; POST `/api/v1/admin/shops`; PUT `/api/v1/admin/shops/{id}`; POST `/api/v1/admin/bazaars/{id}/maps` |
| Bazaar partner | GET `/api/v1/partner/shops/{id}`; PUT `/api/v1/partner/shops/{id}` |
| Category import | POST `/api/v1/admin/categories/upload` |
| Admin coupons | GET `/api/v1/admin/coupons/{id}`; PUT `/api/v1/admin/coupons/{id}`; PATCH `/api/v1/admin/coupons/{id}/status`; DELETE `/api/v1/admin/coupons/{id}`; POST `/api/v1/admin/coupons/{id}/archive`; POST `/api/v1/admin/coupons/{id}/send-to-approval`; POST `/api/v1/admin/coupons/{id}/reject-request`; PATCH `/api/v1/admin/coupons/{id}/take-to-work` |
| Admin merchants | GET `/api/v1/admin/merchants`; GET `/api/v1/admin/merchants/page`; GET `/api/v1/admin/merchants/{id}`; POST `/api/v1/admin/merchants`; PUT `/api/v1/admin/merchants/{id}`; PATCH `/api/v1/admin/merchants/{id}/active`; GET `/api/v1/admin/merchants/{id}/coupons` |
| Coupon directory admin | POST `/api/v1/admin/bazaars`; PUT `/api/v1/admin/bazaars/{id}`; POST `/api/v1/admin/shops`; PUT `/api/v1/admin/shops/{id}` |
| Situation admin | GET `/api/v1/admin/situations`; GET `/api/v1/admin/situations/{id}`; POST `/api/v1/admin/situations`; PUT `/api/v1/admin/situations/{id}`; DELETE `/api/v1/admin/situations/{id}`; PUT `/api/v1/admin/situations/{id}/coupons`; DELETE `/api/v1/admin/situations/{id}/coupons` |
| Bot | POST `/api/v1/bot/coupons/{id}/reject`; GET `/api/v1/bot/coupons/merchants/{chatId}`; GET `/api/v1/bot/coupons/{id}/stats` |
| Coupon directory public | GET `/api/v1/directory/bazaars/{id}`; GET `/api/v1/directory/bazaars/{id}/shops`; GET `/api/v1/directory/shops`; GET `/api/v1/directory/shops/{id}`; GET `/api/v1/directory/area` |
| Moderation exact | PATCH `/api/v1/mod/coupons/{id}/review`; PATCH `/api/v1/mod/reviews/{id}/review`; PATCH `/api/v1/mod/questions/{id}/answer`; PATCH `/api/v1/mod/questions/{id}/reject`; PATCH `/api/v1/mod/complaints/{id}/resolve` |
| UGC exact | GET `/api/v1/questions/coupon/{offerId}`; GET `/api/v1/reviews/coupon/{couponId}`; GET `/api/v1/reviews/coupon/{couponId}/eligibility` |
| Partner applications | GET `/api/v1/admin/partner-applications`; GET `/api/v1/admin/partner-applications/{id}`; PATCH `/api/v1/admin/partner-applications/{id}/approve`; PATCH `/api/v1/admin/partner-applications/{id}/reject` |
| Admin users | GET `/api/v1/admin/users`; GET `/api/v1/admin/users/{id}`; PATCH `/api/v1/admin/users/{id}/block` |
| Super admin | GET `/api/v1/super/staff`; POST `/api/v1/super/admins`; DELETE `/api/v1/super/admins/{id}`; PATCH `/api/v1/super/users/{id}/role`; PATCH `/api/v1/super/users/{id}/block`; GET `/api/v1/super/audit-logs` |
| Legacy refunds | POST `/api/v1/orders/{orderId}/refund`; GET `/api/v1/orders/refunds` |
| Admin orders/refunds | GET `/api/v1/admin/orders/{id}`; GET `/api/v1/admin/refunds`; PATCH `/api/v1/admin/refunds/{id}/approve`; PATCH `/api/v1/admin/refunds/{id}/reject`; PATCH `/api/v1/admin/refunds/{id}/complete`; PATCH `/api/v1/admin/refunds/{id}`; GET `/api/v1/admin/purchased-coupons/lookup` |

The same admin bazaar/shop paths are implemented by coupon-service and bazaar-service. The gateway routes them to bazaar-service, making the coupon-service implementations unreachable through the gateway.

## Examples

```bash
curl -sS 'http://localhost:8080/api/v1/coupons?page=0&size=20'
curl -sS -H 'Authorization: Bearer <access-token>' \
  'http://localhost:8080/api/v1/orders/my-coupons'
```

Evidence:
- `services/*/src/main/java/**/controller`
- `services/*/src/main/java/**/dto`
- `infrastructure/api-gateway/src/main/resources/application.yml`
- `infrastructure/api-gateway/src/main/java/uz/topdim/gateway/filter/JwtAuthenticationFilter.java`
