# Backend Services — Документация для разработчиков

## Межсервисное взаимодействие

### Синхронные вызовы (OpenFeign)

```
order-service ──▶ coupon-service   (CouponClient: getCouponById, getCouponOption)
order-service ──▶ user-service     (UserClient: getUserById)
bazaar-service ──▶ coupon-service  (CouponClient: getCouponOfferById)
```

### Асинхронные события (RabbitMQ)

```
order-service ──publish──▶ order.exchange / order.created
       │                         └──▶ payment-service (PaymentEventListener)
       │
       └──publish──▶ coupon.exchange / coupon.purchased
                            └──▶ notification-service (CouponPurchasedListener)

payment-service ──publish──▶ payment.exchange / payment.completed
                                   └──▶ order-service (PaymentEventListener)
```

### Общие модули (shared)

- `common-dto` → `ApiResponse<T>` — единый формат ответа для всех сервисов
- `common-events` → `OrderCreatedEvent`, `PaymentCompletedEvent`, `CouponPurchasedEvent`

---

## auth-service (:8081)

**Назначение:** Регистрация, аутентификация, JWT токены.

### Бизнес-логика
- Регистрация: email + password + phone → JWT access + refresh tokens
- Login: email/password → JWT tokens
- Refresh: обмен refresh token на новый access token
- Logout: refresh token отзывается, access token добавляется в Redis blacklist

### API Endpoints
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/auth/register` | ❌ | Регистрация |
| POST | `/api/v1/auth/login` | ❌ | Вход |
| POST | `/api/v1/auth/refresh` | ❌ | Обновление токена |
| POST | `/api/v1/auth/logout` | ✅ | Выход |
| PUT  | `/api/v1/auth/change-password` | ✅ | Смена пароля |
| GET  | `/api/v1/super/staff` | ✅ SUPER_ADMIN | Список персонала (Admin/Moderator) |
| POST | `/api/v1/super/admins` | ✅ SUPER_ADMIN | Назначение нового сотрудника |
| PATCH| `/api/v1/super/users/{id}/role`| ✅ SUPER_ADMIN | Изменение роли сотрудника |
| PATCH| `/api/v1/super/users/{id}/block`| ✅ SUPER_ADMIN | Блокировка/разблокировка сотрудника |
| GET  | `/api/v1/super/audit-logs` | ✅ SUPER_ADMIN | Просмотр логов действий администраторов |

### Ключевые классы
- `AuthService` — основная бизнес-логика
- `JwtService` — генерация/валидация JWT
- `TokenBlacklistService` — Redis blacklist для отозванных токенов
- `SecurityConfig` — Spring Security конфигурация

### Модели
- `User` (id, email, phone, password, firstName, lastName, role, enabled)
- `RefreshToken` (token, user, expiresAt, revoked)

---

## coupon-service (:8083)

**Назначение:** Управление каталогом купонов, категориями и партнёрами.

### Бизнес-логика
- Каталог: фильтрация по категории, поиск, сортировка (popular/new/price/discount)
- Redis кэш: categories (1h TTL), catalog (3m), topSelling (15m)
- Admin CRUD: создание/обновление/удаление купонов и партнёров
- При изменении данных — автоматический `@CacheEvict`

### API Endpoints
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/categories` | ❌ | Все категории |
| GET | `/api/v1/coupons` | ❌ | Каталог (page, size, categoryId, search, sortBy) |
| GET | `/api/v1/coupons/{id}` | ❌ | Детали купона |
| GET | `/api/v1/coupons/top-selling` | ❌ | Топ продаж |
| GET | `/api/v1/merchants` | ❌ | Список партнёров |
| POST | `/api/v1/admin/coupons` | ✅ ADMIN | Создать купон |
| PATCH| `/api/v1/admin/coupons/{id}/status` | ✅ ADMIN | Изменить статус |
| DELETE| `/api/v1/admin/coupons/{id}` | ✅ ADMIN | Удалить купон |
| POST | `/api/v1/admin/merchants` | ✅ ADMIN | Создать партнёра |
| POST | `/api/v1/reviews` | ✅ USER | Оставить отзыв |
| PATCH| `/api/v1/mod/reviews/{id}/status` | ✅ MODERATOR | Модерация отзывов |
| POST | `/api/v1/admin/promocodes` | ✅ ADMIN / PARTNER | Генерация промокодов |

### Модели
- `CouponOffer` (title, description, merchant, category, prices, discount, images, status)
- `CouponOption` (title, regularPrice, couponPrice, quantityLimit, quantitySold)
- `Category` (name, slug, iconUrl, sortOrder, active)
- `Merchant` (name, description, logoUrl, address, phone, active)

### Кэширование (Redis)
```java
@Cacheable("catalog")       // getCatalog() — 3 мин TTL
@Cacheable("topSelling")    // getTopSelling() — 15 мин TTL
@Cacheable("categories")    // getAllCategories() — 1 час TTL
@CacheEvict                 // create(), update(), delete() → сброс кэша
```

---

## order-service (:8084)

**Назначение:** Корзина, оформление заказов, купленные купоны, погашение, возврат.

### Бизнес-логика
- **Корзина:** добавление/удаление товаров, поддержка подарочных купонов
- **Checkout:** корзина → заказ + publish `OrderCreatedEvent` в RabbitMQ
- **После оплаты:** `PaymentEventListener` → `generatePurchasedCoupons()` → уникальный код + QR token
- **Погашение:** merchant сканирует код → `redeemCoupon()` → статус USED + запись `Redemption`
- **Возврат:** пользователь создаёт `RefundRequest` → админ approve/reject

### API Endpoints
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/cart` | ✅ | Получить корзину |
| POST | `/api/v1/cart/items` | ✅ | Добавить в корзину |
| DELETE | `/api/v1/cart/items/{id}` | ✅ | Удалить из корзины |
| POST | `/api/v1/orders` | ✅ | Оформить заказ |
| GET | `/api/v1/orders` | ✅ | Мои заказы |
| GET | `/api/v1/orders/{id}` | ✅ | Детали заказа |
| GET | `/api/v1/orders/my-coupons` | ✅ | Мои купоны (status filter) |
| POST | `/api/v1/orders/redeem` | ✅ | Погасить купон |
| GET | `/api/v1/orders/{id}/coupons` | ✅ | Купоны заказа |
| POST | `/api/v1/orders/{id}/refund` | ✅ | Запрос на возврат |
| GET | `/api/v1/orders/refunds` | ✅ | Мои запросы на возврат |
| PATCH | `/api/v1/admin/refunds/{id}` | ✅ ADMIN | Одобрить/отклонить возврат |
| POST | `/api/v1/complaints` | ✅ USER | Подать жалобу на заказ |
| PATCH | `/api/v1/mod/complaints/{id}/resolve`| ✅ MODERATOR | Резолюция модератора по жалобе |

### Модели
- `Cart` → `CartItem[]` (couponId, optionId, quantity, unitPrice, gift)
- `Order` → `OrderItem[]` (orderNumber, userId, totalAmount, status)
- `PurchasedCoupon` (couponCode, qrToken, status: ACTIVE/USED/EXPIRED/REFUNDED)
- `Redemption` (purchasedCoupon, redemptionCode, merchantId, redeemedByStaff)
- `RefundRequest` (order, userId, reason, status: PENDING/APPROVED/REJECTED)

### Статусы заказа
```
PENDING → PAID → COMPLETED
              → CANCELLED
              → REFUNDED
```

---

## payment-service (:8085)

**Назначение:** Обработка платежей.

### Бизнес-логика
- Слушает `OrderCreatedEvent` → создаёт `Payment` (status: PENDING)
- Интеграция с платёжными системами (Payme, Click) — в разработке
- Публикует `PaymentCompletedEvent` при успешной оплате

### Модели
- `Payment` (orderId, userId, amount, currency, provider, status, transactionId)

### Статусы
```
PENDING → PROCESSING → COMPLETED
                     → FAILED
                     → REFUNDED
```

---

## bazaar-service (:8088)

**Назначение:** Базары, магазины, геолокация.

### Бизнес-логика
- CRUD базаров с координатами (lat/lng) для карты Leaflet
- Магазины внутри базаров с категориями и тегами
- Поиск ближайших базаров

### API Endpoints
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/bazaars` | ❌ | Все базары |
| GET | `/api/v1/bazaars/{id}` | ❌ | Детали базара |
| GET | `/api/v1/bazaars/{id}/shops` | ❌ | Магазины базара |
| POST | `/api/v1/admin/bazaars` | ✅ ADMIN | Создать базар |
| POST | `/api/v1/admin/bazaars/{id}/shops` | ✅ ADMIN | Добавить магазин |

### Модели
- `Bazaar` (name, address, lat, lng, description, imageUrl, shops[])
- `Shop` (name, floor, section, phone, category, productTags[])
- `ShopCategory` (name, slug)

---

## user-service (:8082)

**Назначение:** Профиль пользователя и избранное.

### API Endpoints
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/users/me` | ✅ | Мой профиль |
| PUT | `/api/v1/users/me` | ✅ | Обновить профиль |
| GET | `/api/v1/users/me/favorites` | ✅ | Моё избранное |
| POST | `/api/v1/users/me/favorites` | ✅ | Добавить в избранное |
| DELETE | `/api/v1/users/me/favorites/{id}`| ✅ | Удалить из избранного |
| GET | `/api/v1/admin/users` | ✅ ADMIN | Управление пользователями |
| POST | `/api/v1/partner/staff`| ✅ PARTNER | Добавление сотрудников (кассиров) |

---

## notification-service (:8086)

**Назначение:** Email и SMS уведомления.

### Бизнес-логика
- Слушает `CouponPurchasedEvent` из RabbitMQ
- Отправляет email (Spring Mail / SMTP) и SMS (Eskiz.uz API)
- По умолчанию — **stub mode** (только логирование)

### Включение реальной отправки
```yaml
notification:
  email:
    enabled: true           # включить SMTP
    from: noreply@topdim.uz
  sms:
    enabled: true           # включить Eskiz.uz
    api-token: YOUR_TOKEN
```

---

## media-service (:8087)

**Назначение:** Загрузка и хранение файлов (MinIO).

### API Endpoints
| Method | URL | Описание |
|---|---|---|
| POST | `/api/v1/media/upload` | Загрузить файл (multipart) |
| GET | `/api/v1/media/{fileName}` | Скачать файл |
| DELETE | `/api/v1/media/{fileName}` | Удалить файл |

---

## Безопасность (API Gateway)

### JWT Flow
1. Клиент → `POST /api/v1/auth/login` → получает `accessToken` + `refreshToken`
2. Клиент → запрос с `Authorization: Bearer {accessToken}` → API Gateway
3. Gateway → `JwtAuthenticationFilter` валидирует токен
4. Gateway → пробрасывает headers downstream: `X-User-Id`, `X-User-Email`, `X-User-Role`
5. Сервис → читает `@RequestHeader("X-User-Id")` для авторизации

### Открытые endpoints (без JWT)
- `/api/v1/auth/**`
- `/api/v1/categories`
- `/api/v1/coupons/**` (GET)
- `/api/v1/bazaars/**` (GET)
- `/api/v1/merchants` (GET)
- `/swagger-ui/**`, `/v3/api-docs/**`
- `/actuator/**`
