# Backend Services — Документация для разработчиков

## Межсервисное взаимодействие

### Синхронные вызовы (OpenFeign)

```
order-service ──▶ coupon-service   (CouponClient: getCouponById, getCouponOption)
order-service ──▶ identity-service (UserClient: getUserById)
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

## identity-service (:8081)

**Назначение:** Аутентификация, JWT токены, профиль пользователя, партнёрские заявки, управление персоналом.

> ⚠️ Сервис объединяет функции бывших `auth-service` и `user-service`.  
> БД: `topdim_identity`

### Бизнес-логика
- Регистрация: email + password + phone → JWT access + refresh tokens
- Login: email/password → JWT tokens
- Refresh: обмен refresh token на новый access token
- Logout: refresh token отзывается, access token добавляется в Redis blacklist
- Гостевой доступ: временный токен без регистрации
- Сброс пароля через email (одноразовый токен)
- Верификация email (cooldown 60 сек между повторными запросами)
- Партнёрские заявки: публичная подача заявки → модерация Admin

### API Endpoints

#### Аутентификация (`/api/v1/auth`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/auth/register` | ❌ | Регистрация |
| POST | `/api/v1/auth/login` | ❌ | Вход |
| POST | `/api/v1/auth/refresh` | ❌ | Обновление токена |
| POST | `/api/v1/auth/logout` | ✅ | Выход |
| POST | `/api/v1/auth/guest` | ❌ | Гостевой доступ (временный токен) |
| PUT  | `/api/v1/auth/change-password` | ✅ | Смена пароля |
| POST | `/api/v1/auth/password-reset/request` | ❌ | Запрос сброса пароля на email |
| POST | `/api/v1/auth/password-reset/confirm` | ❌ | Подтверждение нового пароля |
| POST | `/api/v1/auth/confirm/request` | ✅ | Отправить код верификации email |
| POST | `/api/v1/auth/confirm/email` | ❌ | Подтвердить email по коду |

#### Пользователи (`/api/v1/users`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET  | `/api/v1/users/me` | ✅ | Мой профиль |
| PUT  | `/api/v1/users/me` | ✅ | Обновить профиль |
| GET  | `/api/v1/users/me/favorites` | ✅ | Моё избранное |
| POST | `/api/v1/users/me/favorites` | ✅ | Добавить в избранное |
| DELETE | `/api/v1/users/me/favorites/{id}` | ✅ | Удалить из избранного |

#### Super Admin (`/api/v1/super`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET  | `/api/v1/super/staff` | ✅ SUPER_ADMIN | Список персонала |
| POST | `/api/v1/super/admins` | ✅ SUPER_ADMIN | Назначение нового сотрудника |
| PATCH | `/api/v1/super/users/{id}/role` | ✅ SUPER_ADMIN | Изменение роли |
| PATCH | `/api/v1/super/users/{id}/block` | ✅ SUPER_ADMIN | Блокировка/разблокировка |
| GET  | `/api/v1/super/audit-logs` | ✅ SUPER_ADMIN | Аудит логи |

#### Admin Users (`/api/v1/admin/users`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET  | `/api/v1/admin/users` | ✅ ADMIN | Список всех пользователей |
| GET  | `/api/v1/admin/users/{id}` | ✅ ADMIN | Детали пользователя |
| PATCH | `/api/v1/admin/users/{id}/block` | ✅ ADMIN | Блокировать пользователя |

#### Партнёрские заявки
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/partners/applications` | ❌ | Подать заявку на партнёрство |
| GET  | `/api/v1/admin/partner-applications` | ✅ ADMIN | Список заявок (пагинация) |
| PATCH | `/api/v1/admin/partner-applications/{id}` | ✅ ADMIN | Одобрить/отклонить заявку |

#### Partner Staff (`/api/v1/partner/staff`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET  | `/api/v1/partner/staff` | ✅ PARTNER | Мои сотрудники |
| POST | `/api/v1/partner/staff` | ✅ PARTNER | Добавить сотрудника |
| DELETE | `/api/v1/partner/staff/{id}` | ✅ PARTNER | Удалить сотрудника |

### Ключевые классы
- `AuthController` — register, login, refresh, logout, guest, password-reset, confirm
- `UserController` — профиль, избранное
- `SuperAdminController` — управление персоналом, роли, аудит
- `AdminUserController` — управление пользователями
- `PartnerApplicationController` — публичная подача заявок
- `AdminPartnerApplicationController` — модерация заявок
- `PartnerStaffController` — управление кассирами
- `AuthService` — основная бизнес-логика
- `JwtService` — генерация/валидация JWT
- `TokenBlacklistService` — Redis blacklist для отозванных токенов
- `PasswordResetService` — сброс пароля через email
- `EmailConfirmationService` — верификация email

### Модели
- `User` (id, email, phone, password, firstName, lastName, role, enabled, emailVerified)
- `RefreshToken` (token, user, expiresAt, revoked)
- `AuditLog` (userId, action, entityType, entityId, details, ipAddress)

---

## coupon-service (:8083)

**Назначение:** Управление каталогом купонов, категориями, партнёрами, справочником базаров и магазинов.

### Бизнес-логика
- Каталог: фильтрация по категории, поиск, сортировка (popular/new/price/discount)
- Redis кэш: categories (1h TTL), catalog (3m), topSelling (15m)
- Жизненный цикл купона: LEAD → DRAFT → WAITING_FOR_MERCHANT → REVISION_REQUESTED → ACTIVE → SOLD_OUT
- Партнёр создаёт купон (LEAD), модератор берёт в работу (DRAFT), отправляет на согласование мерчанту
- Admin/Moderator CRUD купонов и партнёров
- Справочник базаров и магазинов (с геопоиском по области)

### API Endpoints

#### Публичный каталог
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/categories` | ❌ | Все категории |
| GET | `/api/v1/coupons` | ❌ | Каталог (page, size, categoryId, search, sortBy) |
| GET | `/api/v1/coupons/{id}` | ❌ | Детали купона |
| GET | `/api/v1/coupons/top-selling` | ❌ | Топ продаж |
| GET | `/api/v1/merchants` | ❌ | Список партнёров |

#### Справочник базаров и магазинов (`/api/v1/directory`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/directory/bazaars` | ❌ | Базары (search, type, page, size) |
| GET | `/api/v1/directory/bazaars/{id}` | ❌ | Детали базара |
| GET | `/api/v1/directory/bazaars/{id}/shops` | ❌ | Магазины базара (search) |
| GET | `/api/v1/directory/shops` | ❌ | Все магазины (search, category, page, size) |
| GET | `/api/v1/directory/shops/{id}` | ❌ | Детали магазина |
| GET | `/api/v1/directory/area` | ❌ | Поиск в области (minLat, maxLat, minLon, maxLon) |

#### Отзывы
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/reviews` | ✅ USER | Оставить отзыв |
| GET  | `/api/v1/reviews/me` | ✅ USER | Мои отзывы |

#### Admin — купоны (`/api/v1/admin/coupons`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/admin/coupons` | ✅ MOD+ | Все купоны (фильтр по статусу) |
| GET | `/api/v1/admin/coupons/{id}` | ✅ MOD+ | Детали купона |
| POST | `/api/v1/admin/coupons` | ✅ MOD+ | Создать купон |
| PUT | `/api/v1/admin/coupons/{id}` | ✅ MOD+ | Обновить купон |
| PATCH | `/api/v1/admin/coupons/{id}/status` | ✅ MOD+ | Изменить статус купона |
| DELETE | `/api/v1/admin/coupons/{id}` | ✅ MOD+ | Удалить купон |
| POST | `/api/v1/admin/coupons/{id}/send-to-approval` | ✅ MOD+ | Отправить мерчанту на согласование |
| PATCH | `/api/v1/admin/coupons/{id}/take-to-work` | ✅ MOD+ | Взять лид в работу (LEAD→DRAFT) |

#### Admin — мерчанты (`/api/v1/admin/merchants`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/admin/merchants` | ✅ MOD+ | Список мерчантов |
| GET | `/api/v1/admin/merchants/{id}` | ✅ ADMIN | Детали мерчанта |
| POST | `/api/v1/admin/merchants` | ✅ ADMIN | Создать мерчанта |
| PUT | `/api/v1/admin/merchants/{id}` | ✅ ADMIN | Обновить мерчанта |

#### Admin — категории (`/api/v1/admin/categories`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/admin/categories/{id}` | ✅ ADMIN | Категория по ID |
| POST | `/api/v1/admin/categories` | ✅ ADMIN | Создать категорию |
| PUT | `/api/v1/admin/categories/{id}` | ✅ ADMIN | Обновить категорию |
| DELETE | `/api/v1/admin/categories/{id}` | ✅ ADMIN | Удалить категорию |

#### Admin — справочник базаров и магазинов
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/admin/bazaars` | ✅ ADMIN | Создать базар |
| PUT | `/api/v1/admin/bazaars/{id}` | ✅ ADMIN | Обновить базар |
| POST | `/api/v1/admin/shops` | ✅ ADMIN | Создать магазин |
| PUT | `/api/v1/admin/shops/{id}` | ✅ ADMIN | Обновить магазин |

#### Admin — промокоды
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/admin/promocodes` | ✅ ADMIN/PARTNER | Генерация промокодов |

#### Moderator (`/api/v1/mod`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/mod/coupons` | ✅ MOD+ | Купоны на модерации (PENDING, страница) |
| PATCH | `/api/v1/mod/coupons/{id}/review` | ✅ MOD+ | Принять/отклонить купон |
| GET | `/api/v1/mod/reviews` | ✅ MOD+ | Отзывы на модерации |
| PATCH | `/api/v1/mod/reviews/{id}/review` | ✅ MOD+ | Принять/отклонить отзыв |

#### Partner (`/api/v1/partner/coupons`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/partner/coupons` | ✅ PARTNER | Мои купоны (фильтр по статусу) |
| GET | `/api/v1/partner/coupons/{id}` | ✅ PARTNER | Детали моего купона |
| POST | `/api/v1/partner/coupons` | ✅ PARTNER | Создать купон (статус LEAD) |
| PUT | `/api/v1/partner/coupons/{id}` | ✅ PARTNER | Обновить купон (только DRAFT/REVISION_REQUESTED) |

#### Bot API (`/api/v1/bot/coupons`)
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/bot/coupons/{id}/approve` | X-Bot-Api-Key | Мерчант одобряет купон → ACTIVE |
| POST | `/api/v1/bot/coupons/{id}/reject` | X-Bot-Api-Key | Мерчант запрашивает правки → REVISION_REQUESTED |
| POST | `/api/v1/bot/coupons/leads` | X-Bot-Api-Key | Создать LEAD из заявки Telegram-бота |
| GET | `/api/v1/bot/coupons/merchants/{chatId}` | X-Bot-Api-Key | Купоны мерчанта по telegramChatId |
| GET | `/api/v1/bot/coupons/{id}/stats` | X-Bot-Api-Key | Статистика купона (продажи, погашения, рейтинг) |

### Модели
- `CouponOffer` (title, shortDescription, fullDescription, merchant, category, prices, discount, images, status, terms, usageRules, howToUse, address, contactPhone, workingHours, giftAvailable, redeemedCount, totalTurnover, viewCount)
- `CouponOption` (title, regularPrice, couponPrice, quantityLimit, quantitySold)
- `Category` (name, nameUz, slug, iconUrl, sortOrder, active)
- `Merchant` (name, description, logoUrl, coverUrl, address, phone, email, website, workingHours, contactPerson, active, userId, telegramChatId)

### Статусы купона
```
LEAD → DRAFT → WAITING_FOR_MERCHANT → ACTIVE → SOLD_OUT
                                     → REVISION_REQUESTED → DRAFT
```

### Кэширование (Redis)
```java
@Cacheable("catalog")       // getCatalog() — 3 мин TTL
@Cacheable("topSelling")    // getTopSelling() — 15 мин TTL
@Cacheable("categories")    // getAllCategories() — 1 час TTL
@CacheEvict                 // create(), update(), delete() → сброс кэша
```

---

## order-service (:8084)

**Назначение:** Корзина, оформление заказов, купленные купоны, погашение, возврат, жалобы.

### Бизнес-логика
- **Корзина:** добавление/удаление/очистка, поддержка подарочных купонов
- **Checkout:** корзина → заказ + publish `OrderCreatedEvent` в RabbitMQ
- **После оплаты:** `PaymentEventListener` → `generatePurchasedCoupons()` → уникальный код + QR token
- **Погашение:** PARTNER сканирует код → `redeemCoupon()` → статус USED + запись `Redemption`
- **Возврат:** пользователь создаёт `RefundRequest` → Admin approve/reject
- **Жалоба:** пользователь создаёт жалобу → Moderator резолюция

### API Endpoints

#### Корзина
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/cart` | ✅ | Получить корзину |
| POST | `/api/v1/cart/items` | ✅ | Добавить в корзину |
| DELETE | `/api/v1/cart/items/{itemId}` | ✅ | Удалить из корзины |
| DELETE | `/api/v1/cart` | ✅ | Очистить корзину полностью |

#### Заказы
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/orders` | ✅ | Оформить заказ |
| GET | `/api/v1/orders` | ✅ | Мои заказы (с пагинацией) |
| GET | `/api/v1/orders/{id}` | ✅ | Детали заказа |

#### Мои купоны
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/orders/my-coupons` | ✅ | Мои купоны (фильтр по статусу) |
| GET | `/api/v1/orders/{orderId}/coupons` | ✅ | Купоны конкретного заказа |

#### Погашение
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/orders/redeem` | ✅ PARTNER | Погасить купон (QR/код) |

#### Возвраты
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/orders/{orderId}/refund` | ✅ | Запрос на возврат |
| GET | `/api/v1/orders/refunds` | ✅ | Мои запросы на возврат |
| PATCH | `/api/v1/admin/refunds/{id}` | ✅ ADMIN | Одобрить/отклонить возврат |

#### Admin
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/admin/orders` | ✅ ADMIN | Все заказы (фильтр по статусу, пагинация) |
| GET | `/api/v1/admin/orders/{id}` | ✅ ADMIN | Детали заказа |

#### Жалобы
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/complaints` | ✅ USER | Подать жалобу на заказ |
| PATCH | `/api/v1/mod/complaints/{id}/resolve` | ✅ MOD+ | Резолюция модератора |

#### Статистика Partner
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/partner/stats` | ✅ PARTNER | Статистика продаж и погашений |
| GET | `/api/v1/partner/redemptions` | ✅ PARTNER | История погашений |

### Модели
- `Cart` → `CartItem[]` (couponOfferId, optionId, quantity, unitPrice, isGift, giftRecipientName/Phone)
- `Order` → `OrderItem[]` (orderNumber, userId, userEmail, userPhone, totalAmount, status)
- `PurchasedCoupon` (couponCode, qrToken, status: ACTIVE/USED/EXPIRED/REFUNDED)
- `Redemption` (purchasedCoupon, redemptionCode, merchantId, redeemedByStaff)
- `RefundRequest` (order, userId, reason, status: PENDING/APPROVED/REJECTED, adminComment)
- `Complaint` (order, userId, reason, status, resolutionComment)

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

### API Endpoints
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/payments/create` | ✅ | Создать платёж |
| GET | `/api/v1/payments/{id}/status` | ✅ | Статус платежа |
| GET | `/api/v1/payments/order/{orderId}` | ✅ | Платёж по заказу |
| POST | `/api/v1/payments/order/{orderId}/demo-complete` | ✅ | Demo-завершение (только в payment.mode=demo) |

### Модели
- `Payment` (orderId, userId, amount, currency, provider[PAYME/CLICK/UZUM], status, transactionId, paymentUrl, errorMessage)

### Статусы
```
PENDING → COMPLETED
       → FAILED
       → REFUNDED
```

---

## bazaar-service (:8086)

**Назначение:** Базары, магазины, геолокация, карты этажей.

### Бизнес-логика
- CRUD базаров с координатами (lat/lng) для карты 2GIS
- Магазины внутри базаров с категориями и тегами
- Карты этажей базаров (floor maps)
- Поиск магазинов по ключевому слову
- Фильтр магазинов по наличию купона (`hasCoupon`)

### API Endpoints

#### Публичные
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/bazaars` | ❌ | Все базары (фильтр по городу) |
| GET | `/api/v1/bazaars/{id}` | ❌ | Детали базара |
| GET | `/api/v1/bazaars/{id}/map` | ❌ | Карты этажей базара |
| GET | `/api/v1/bazaars/{id}/shops` | ❌ | Магазины базара (hasCoupon filter) |
| GET | `/api/v1/shops/{id}` | ❌ | Детали магазина |
| GET | `/api/v1/shops/search` | ❌ | Поиск магазинов по ключевому слову |
| GET | `/api/v1/shops/categories` | ❌ | Категории магазинов |

#### Admin
| Method | URL | Auth | Описание |
|---|---|---|---|
| POST | `/api/v1/admin/bazaars` | ✅ ADMIN | Создать базар |
| PUT | `/api/v1/admin/bazaars/{id}` | ✅ ADMIN | Обновить базар |
| POST | `/api/v1/admin/shops` | ✅ ADMIN | Создать магазин |
| PUT | `/api/v1/admin/shops/{id}` | ✅ ADMIN | Обновить магазин |
| POST | `/api/v1/admin/bazaars/{id}/maps` | ✅ ADMIN | Загрузить карту этажа |

#### Partner
| Method | URL | Auth | Описание |
|---|---|---|---|
| GET/PUT | `/api/v1/partner/shops/**` | ✅ PARTNER | Управление своим магазином |

### Модели
- `Bazaar` (name, nameUz, type, address, city, lat, lng, description, searchVector)
- `BazaarMap` (bazaar, floorNumber, mapImageUrl, mapSvgUrl)
- `Shop` (name, bazaar, category, hasCoupon, linkedCouponOfferId, searchVector)
- `ShopCategory` (name, nameUz, slug, active)
- `ShopProductTag` (shop, tag, tagUz)

---

## notification-service (:8087)

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

## media-service (:8088)

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

### Rate Limiting (API Gateway)
- `/api/v1/auth/login` — 2 req/sec, burst 3
- `/api/v1/auth/register` — 1 req/sec, burst 2

### Открытые endpoints (без JWT)
- `/api/v1/auth/**` (кроме change-password, confirm/request)
- `/api/v1/categories`
- `/api/v1/coupons/**` (GET)
- `/api/v1/directory/**` (GET)
- `/api/v1/bazaars/**` (GET)
- `/api/v1/shops/**` (GET)
- `/api/v1/merchants` (GET)
- `/api/v1/partners/applications` (POST)
- `/swagger-ui/**`, `/v3/api-docs/**`
- `/actuator/**`
