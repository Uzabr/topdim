# TopDim — Полное сравнение: план vs код

> Источник: `docs/implementation_plan.md` (10 фаз) vs реальный код (151 Java, 44 frontend, 8 тестов)

---

## Фаза 1: Инфраструктура

| Пункт плана | Статус | Что есть в коде |
|---|---|---|
| Gradle multi-module | ✅ | root `build.gradle` + 8 сервисов + 2 shared + 3 infra |
| Docker Compose (PG, Redis, RabbitMQ, MinIO) | ✅ | `docker-compose.yml` + override + init-databases.sql |
| Eureka Server | ✅ | `infrastructure/discovery-server/` — работает |
| Config Server | ⚠️ **есть, но не подключён** | `infrastructure/config-server/` — Application.java + application.yml есть, но **ни один сервис не использует** `spring.config.import` |
| API Gateway + JWT фильтр | ✅ | `api-gateway/` — JwtAuthenticationFilter, SecurityConfig, routes |
| Общие модули | ✅ | `shared/common-dto/` (ApiResponse) + `shared/common-events/` (3 события) |

> ⚠️ **Config Server** создан, но не интегрирован ни с одним сервисом. Все конфиги хранятся локально в `application.yml`.

---

## Фаза 2: Auth + User

| Пункт | Статус | Код |
|---|---|---|
| Auth — register | ✅ | `POST /api/v1/auth/register` |
| Auth — login | ✅ | `POST /api/v1/auth/login` |
| Auth — refresh | ✅ | `POST /api/v1/auth/refresh` |
| Auth — logout | ✅ | `POST /api/v1/auth/logout` |
| Auth — verify email/phone | ❌ | `GET /api/v1/auth/verify` — **нет в коде** |
| Auth — change password | ✅ | `PUT /api/v1/auth/change-password` |
| User — profile GET | ✅ | `GET /api/v1/users/me` |
| User — profile PUT | ✅ | `PUT /api/v1/users/me` |
| User — favorites | ✅ | GET/POST/DELETE `/api/v1/users/me/favorites` |
| User — delete account | ❌ | `DELETE /api/v1/users/me` — **нет** |
| JWT blacklist | ✅ | `TokenBlacklistService` (Redis) |
| Login attempt protection | ✅ | `LoginAttemptService` (progressive delay) |

**Entities:**
- ✅ `User` (id, email, phone, password, firstName, lastName, role, enabled)
- ✅ `RefreshToken` (token, user, expiresAt, revoked)
- ✅ `Role` (GUEST, USER, PARTNER, MODERATOR, ADMIN, SUPER_ADMIN)
- ✅ `Favorite` (userId, couponOfferId)

---

## Фаза 3: Coupon + Catalog

| Пункт | Статус | Код |
|---|---|---|
| GET каталог (фильтры, сортировка, пагинация) | ✅ | `GET /api/v1/coupons` |
| GET карточка купона | ✅ | `GET /api/v1/coupons/{id}` |
| GET top-selling | ✅ | `GET /api/v1/coupons/top-selling` |
| GET категории | ✅ | `GET /api/v1/categories` |
| Admin CRUD купонов | ✅ | POST/PATCH/DELETE `/api/v1/admin/coupons` |
| GET/POST merchants | ✅ | `/api/v1/admin/merchants` |
| Redis кэш каталога | ✅ | `@Cacheable("catalog")`, `@Cacheable("categories")`, `@Cacheable("topSelling")` |
| Admin CRUD категорий | ❌ | POST/PUT/DELETE `/api/v1/admin/categories` — **нет** |
| GET coupons/{id}/options | ❌ | отдельного endpoint **нет** (опции внутри купона) |

**Entities:**
- ✅ CouponOffer, CouponOption, Category, Merchant, CouponImage
- ✅ CouponStatus, CouponOptionStatus

---

## Фаза 4: Order + Payment

| Пункт | Статус | Код |
|---|---|---|
| Cart CRUD | ✅ | GET/POST/DELETE `/api/v1/cart` |
| Order checkout | ✅ | `POST /api/v1/orders` |
| Order list + detail | ✅ | GET `/api/v1/orders`, `/api/v1/orders/{id}` |
| PurchasedCoupon generation | ✅ | генерация couponCode + qrToken после оплаты |
| Мои купоны | ✅ | `GET /api/v1/orders/my-coupons` |
| Redemption (погашение) | ✅ | `POST /api/v1/orders/redeem` |
| Refund request | ✅ | POST/GET `/api/v1/orders/{orderId}/refund`, `/api/v1/orders/refunds` |
| Admin refund approve | ✅ | `PATCH /api/v1/admin/refunds/{id}` |
| Payment create | ✅ | `POST /api/v1/payments/create` |
| Payment status | ✅ | `GET /api/v1/payments/{id}/status` |
| Payment callback | ✅ | `POST /api/v1/payments/callback` |
| **Payme/Click реальная интеграция** | ❌ | **stub** — PaymentService возвращает mock ответы |
| Idempotency key для платежей | ❌ | **нет** |
| RabbitMQ events | ✅ | OrderCreated → Payment, PaymentCompleted → Order, CouponPurchased → Notification |
| Admin все заказы | ❌ | `GET /api/v1/admin/orders` — **нет отдельного endpoint** |

**Entities:**
- ✅ Cart, CartItem, Order, OrderItem, PurchasedCoupon, Redemption, RefundRequest
- ✅ Payment (orderId, amount, provider, status, transactionId)

---

## Фаза 5: Bazaar

| Пункт | Статус | Код |
|---|---|---|
| GET базары | ✅ | `GET /api/v1/bazaars` |
| GET карточка базара | ✅ | `GET /api/v1/bazaars/{id}` |
| GET карта базара | ✅ | `GET /api/v1/bazaars/{id}/map` |
| GET магазины базара | ✅ | `GET /api/v1/bazaars/{id}/shops` |
| GET карточка магазина | ✅ | `GET /api/v1/shops/{id}` |
| Поиск магазинов | ✅ | `GET /api/v1/shops/search` |
| Категории магазинов | ✅ | `GET /api/v1/shops/categories` |
| Admin CRUD | ✅ | POST/PUT bazaars, shops, maps |
| Связь Shop ↔ CouponOffer | ⚠️ | Feign client есть, но **нет UI или endpoint для привязки** |

**Entities:**
- ✅ Bazaar, BazaarMap, Shop, ShopCategory, ShopProductTag, BazaarType

---

## Фаза 6: Notification + Media

| Пункт | Статус | Код |
|---|---|---|
| Email при покупке | ✅ | `EmailService` (stub mode) |
| SMS при покупке | ✅ | `SmsService` (stub mode — Eskiz.uz API) |
| RabbitMQ listener | ✅ | `CouponPurchasedListener` |
| Media upload | ✅ | `POST /api/v1/media/upload` (MinIO) |
| Media download | ✅ | `GET /api/v1/media/{fileName}` |
| Media delete | ✅ | `DELETE /api/v1/media/{fileName}` |
| In-app уведомления (WebSocket/SSE) | ❌ | **нет** |
| User notification endpoints | ❌ | GET/PATCH уведомлений — **нет** |

---

## Фаза 7: Frontend Web App

| Пункт | Статус | Файлы |
|---|---|---|
| Vite + React 18 setup | ✅ | `frontend/web-app/` — vite.config.ts, package.json |
| API client (Axios) | ✅ | `api/client.ts`, `auth.ts`, `coupons.ts`, `bazaars.ts`, `orders.ts` |
| Zustand store | ✅ | `store/authStore.ts`, `store/cartStore.ts` |
| Layout (Header, BottomNav) | ✅ | `components/layout/Header.tsx`, `BottomNav.tsx` |
| CouponCard | ✅ | `components/coupon/CouponCard.tsx` |
| CartDrawer | ✅ | `components/cart/CartDrawer.tsx` |
| HomePage | ✅ | `pages/HomePage.tsx` |
| CouponCatalogPage | ✅ | `pages/CouponCatalogPage.tsx` |
| CouponDetailPage | ✅ | `pages/CouponDetailPage.tsx` |
| CartPage | ✅ | `pages/CartPage.tsx` |
| CheckoutPage | ✅ | `pages/CheckoutPage.tsx` |
| ProfilePage | ✅ | `pages/ProfilePage.tsx` |
| BazaarMapPage | ✅ | `pages/BazaarMapPage.tsx` |
| BazaarDetailPage | ✅ | `pages/BazaarDetailPage.tsx` |
| ShopDetailPage | ✅ | `pages/ShopDetailPage.tsx` |
| SearchPage | ✅ | `pages/SearchPage.tsx` |
| LoginPage | ✅ | `pages/LoginPage.tsx` |
| **UI Kit** (Button, Card, Modal...) | ❌ | `components/ui/` — **папки нет** |
| **Bazaar компоненты** (BazaarMarker, InteriorMap) | ❌ | **нет** |
| **i18n (ru/uz)** | ❌ | `i18n/` — **нет** |
| **React Hook Form + Zod** | ❌ | форма — **plain state** |
| **CSS Modules** | ❌ | используется plain CSS |
| format utils | ✅ | `utils/format.ts`, `hooks/useFormatPrice.ts` |

---

## Фаза 8: Admin Panel

| Пункт | Статус |
|---|---|
| `frontend/admin-app/` | ❌ **не создан** |
| Admin авторизация | ❌ |
| CRUD купонов/партнёров | ❌ |
| Управление заказами | ❌ |
| CRUD базаров/магазинов | ❌ |
| Dashboard аналитика | ❌ |

---

## Фаза 9: Тестирование

| Пункт | Статус | Файлы |
|---|---|---|
| Unit тесты (JUnit + Mockito) | ⚠️ частично | 8 файлов: AuthServiceTest, JwtServiceTest, OrderServiceTest, PaymentServiceTest, CouponOfferServiceTest, UserServiceTest, BazaarServiceTest, ShopServiceTest |
| Integration (Testcontainers) | ❌ | **ноль** |
| E2E (Playwright) | ❌ | **ноль** |
| Нагрузочное (k6) | ❌ | **ноль** |

---

## Фаза 10: Деплой

| Пункт | Статус |
|---|---|
| Dockerfile для каждого сервиса | ❌ |
| docker-compose.prod.yml | ❌ |
| GitHub Actions CI/CD | ❌ |
| VPS + домен + SSL | ❌ |
| Prometheus + Grafana | ✅ `docker-compose.yml` — контейнеры есть |
| Loki | ✅ контейнер есть |
| ELK (Elasticsearch + Kibana) | ✅ контейнеры есть |
| Nginx reverse proxy | ❌ |

---

## Безопасность (полностью закрыта)

| Пункт из плана (Section 6) | Статус |
|---|---|
| JWT (access 15 мин, refresh 7 дней) | ✅ |
| Spring Security + roles | ✅ |
| API Gateway auth filter | ✅ |
| Rate Limiting (Redis) | ✅ |
| CORS | ✅ |
| Idempotency key для платежей | ❌ |
| Input Validation | ✅ |
| Secrets → env vars | ✅ |

---

## Roles — недостающие endpoints (из ROLES.md)

### PARTNER (8% → только redeem)
| Чего нет | Нужен контроллер |
|---|---|
| `GET/POST/PUT /api/v1/partner/coupons` | PartnerCouponController |
| `GET /api/v1/partner/shop` | PartnerShopController |
| `GET /api/v1/partner/stats/*` | PartnerStatsController |
| `GET /api/v1/partner/redemptions` | PartnerRedemptionController |
| `GET/POST/DELETE /api/v1/partner/staff` | PartnerStaffController |

### MODERATOR (0%)
| Чего нет | Нужен контроллер |
|---|---|
| `GET/PATCH /api/v1/mod/coupons` | ModCouponController |
| `GET/PATCH /api/v1/mod/complaints` | ModComplaintController (+Complaint entity) |
| `PATCH /api/v1/mod/reviews/{id}/block` | ModReviewController (+Review entity) |
| `GET /api/v1/mod/users` | ModUserController |

### ADMIN (доп.)
| Чего нет | Где |
|---|---|
| `GET /api/v1/admin/users` | AdminUserController |
| `PATCH /api/v1/admin/users/{id}/block` | AdminUserController |
| `POST/PUT/DELETE /api/v1/admin/categories` | AdminCategoryController |
| `POST /api/v1/admin/promo-codes` | PromoCodeController (+PromoCode entity) |
| `GET /api/v1/admin/dashboard` | AdminDashboardController |
| `GET /api/v1/admin/orders` | AdminOrderController |

### SUPER_ADMIN (0%)
| Чего нет | Где |
|---|---|
| `POST/DELETE /api/v1/super/admins` | SuperAdminController |
| `PATCH /api/v1/super/users/{id}/role` | SuperAdminController |
| `GET/PUT /api/v1/super/settings` | SystemSettingsController |
| `GET /api/v1/super/finance` | FinanceController |
| `GET /api/v1/super/audit-logs` | AuditLogController (+AuditLog entity) |

---

## Недостающие Entities

| Entity | Сервис | Для чего |
|---|---|---|
| **Complaint** | order-service или новый | жалобы пользователей (MODERATOR) |
| **Review** | coupon-service или order-service | отзывы на купоны (USER/MODERATOR) |
| **PromoCode** | coupon-service | промокоды (ADMIN) |
| **AuditLog** | auth-service | лог действий (SUPER_ADMIN) |
| **Notification** | notification-service | хранение in-app уведомлений (USER) |
| **NotificationSetting** | user-service | настройки уведомлений |

---

## Итоговая сводка по фазам

| Фаза | Описание | % готовности |
|---|---|---|
| 1 | Инфраструктура | **90%** (Config Server не подключён) |
| 2 | Auth + User | **85%** (нет verify, delete account) |
| 3 | Coupon + Catalog | **90%** (нет admin categories) |
| 4 | Order + Payment | **80%** (Payme/Click stub, нет idempotency) |
| 5 | Bazaar | **95%** |
| 6 | Notification + Media | **70%** (нет in-app, нет user endpoints) |
| 7 | Frontend Web App | **65%** (нет UI kit, i18n, forms, CSS modules) |
| 8 | Admin Panel | **0%** |
| 9 | Тестирование | **15%** (unit частично, нет integration/e2e) |
| 10 | Деплой | **10%** (только мониторинг контейнеры) |
| + | Безопасность | **100%** ✅ |
| + | PARTNER роль | **8%** |
| + | MODERATOR роль | **0%** |
| + | SUPER_ADMIN роль | **0%** |

### **Общая готовность проекта: ~55-60%**
