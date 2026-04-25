# API Contract — Frontend ↔ Backend

> Полная документация взаимодействия фронтенда с бэкендом.
> Создано для фронтенд-разработчиков.

---

## 1. Архитектура взаимодействия

```
┌───────────────┐     HTTP      ┌──────────────┐      REST       ┌───────────────────┐
│   React SPA   │ ──────────→  │  API Gateway  │ ────────────→  │   Микросервисы     │
│  :5173 (dev)  │ ←────────── │    :8080      │ ←────────────  │  :8081-8088       │
└───────────────┘    JSON      └──────────────┘                 └───────────────────┘
                                      │
                               JWT валидация
                               X-User-Id header
                               X-User-Role header
```

### Ключевые правила:
1. **Все запросы идут через API Gateway** → `http://localhost:8080`
2. **Формат ответа** — всегда `ApiResponse<T>` (см. ниже)
3. **Авторизация** — Bearer JWT token в заголовке `Authorization`
4. **Content-Type** — всегда `application/json`

---

## 2. Формат ответа (ApiResponse)

**Все** endpoints возвращают единый формат:

### Успешный ответ

```json
{
  "success": true,
  "message": "Операция выполнена",
  "data": { ... },
  "timestamp": "2026-03-25T01:00:00"
}
```

### Ответ с ошибкой

```json
{
  "success": false,
  "message": "Пользователь с таким email уже существует",
  "data": null,
  "timestamp": "2026-03-25T01:00:00"
}
```

### Страничный ответ (для каталога, списков)

```json
{
  "success": true,
  "message": null,
  "data": {
    "content": [ ... ],
    "pageable": { "pageNumber": 0, "pageSize": 20 },
    "totalElements": 156,
    "totalPages": 8,
    "first": true,
    "last": false
  },
  "timestamp": "2026-03-25T01:00:00"
}
```

### HTTP статус коды

| Код | Когда |
|---|---|
| `200` | Успех |
| `201` | Создано (register, create) |
| `400` | Невалидные данные |
| `401` | Нет токена / токен истёк |
| `403` | Нет прав (не ADMIN) |
| `404` | Ресурс не найден |
| `500` | Ошибка сервера |

---

## 3. Аутентификация (JWT)

### Как работает

```
1. Пользователь → POST /auth/login → получает accessToken + refreshToken
2. Каждый запрос → Authorization: Bearer <accessToken>
3. Gateway → валидирует JWT → пробрасывает X-User-Id в сервис
4. AccessToken истёк → POST /auth/refresh → новая пара токенов
5. Logout → POST /auth/logout → token в Redis blacklist
```

### Заголовки

```http
# Для защищённых endpoints:
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

### Время жизни токенов

| Токен | TTL |
|---|---|
| Access Token | 15 минут |
| Refresh Token | 7 дней |

### Axios Interceptor (пример)

```typescript
// api/client.ts
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
});

// Автоматическое добавление токена
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Автоматическое обновление при 401
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      const refreshToken = localStorage.getItem('refreshToken');
      const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken });
      localStorage.setItem('accessToken', data.data.accessToken);
      localStorage.setItem('refreshToken', data.data.refreshToken);
      error.config.headers.Authorization = `Bearer ${data.data.accessToken}`;
      return axios(error.config);
    }
    return Promise.reject(error);
  }
);
```

---

## 4. Endpoints — Auth Service

Base URL: `/api/v1/auth`

### POST `/api/v1/auth/register` — Регистрация

**Auth:** ❌ Не требуется

**Request:**
```json
{
  "email": "user@example.com",
  "phone": "+998901234567",
  "password": "mypassword123",
  "firstName": "Иван",
  "lastName": "Иванов"
}
```

**Response:** `201 Created`
```json
{
  "success": true,
  "message": "Регистрация прошла успешно",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": 1,
      "email": "user@example.com",
      "phone": "+998901234567",
      "firstName": "Иван",
      "lastName": "Иванов",
      "role": "USER",
      "avatarUrl": null
    }
  }
}
```

**Ошибки:**
| Код | Сообщение |
|---|---|
| `400` | "Пользователь с таким email уже существует" |
| `400` | "Пользователь с таким номером телефона уже существует" |

---

### POST `/api/v1/auth/login` — Вход

**Auth:** ❌ Не требуется

**Request:**
```json
{
  "email": "user@example.com",
  "password": "mypassword123"
}
```

**Response:** `200 OK` — формат идентичен register

**Ошибки:**
| Код | Сообщение |
|---|---|
| `401` | "Неверный email или пароль" |

---

### POST `/api/v1/auth/refresh` — Обновление токена

**Auth:** ❌ Не требуется

**Request:**
```json
{
  "refreshToken": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

**Response:** `200 OK` — формат идентичен register (новая пара токенов)

**Ошибки:**
| Код | Сообщение |
|---|---|
| `400` | "Невалидный refresh token" |
| `400` | "Refresh token отозван" |
| `400` | "Refresh token истёк" |

---

### POST `/api/v1/auth/logout` — Выход

**Auth:** ✅ Bearer Token

**Request:**
```json
{
  "refreshToken": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Выход выполнен",
  "data": null
}
```

---

## 5. Endpoints — Coupon Service (публичные)

Base URL: `/api/v1`

### GET `/api/v1/coupons` — Каталог купонов

**Auth:** ❌ Не требуется

**Query параметры:**

| Параметр | Тип | Default | Описание |
|---|---|---|---|
| `categoryId` | Long | null | Фильтр по категории |
| `search` | String | null | Полнотекстовый поиск |
| `sortBy` | String | `popular` | `popular`, `new`, `priceAsc`, `priceDesc`, `discount` |
| `page` | int | 0 | Номер страницы (начиная с 0) |
| `size` | int | 20 | Размер страницы |

**Пример запроса:**
```
GET /api/v1/coupons?categoryId=3&sortBy=discount&page=0&size=10
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "title": "Скидка 50% на SPA массаж",
        "shortDescription": "Релакс массаж 60 мин",
        "fullDescription": "Полный расслабляющий массаж...",
        "merchant": {
          "id": 5,
          "name": "SPA Oasis",
          "logoUrl": "/media/spa-logo.jpg"
        },
        "category": {
          "id": 3,
          "name": "Beauty",
          "slug": "beauty",
          "iconUrl": "/media/icon-beauty.svg"
        },
        "oldPrice": 300000,
        "fromPrice": 150000,
        "discountPercent": 50,
        "coverImageUrl": "/media/spa-cover.jpg",
        "buyUntil": "2026-04-30T23:59:59",
        "useUntil": "2026-06-30T23:59:59",
        "terms": "Только для новых клиентов",
        "usageRules": "Предъявите QR код на кассе",
        "howToUse": "1. Купите купон. 2. Запишитесь. 3. Покажите QR.",
        "address": "Ташкент, ул. Навои 45",
        "contactPhone": "+998901234567",
        "workingHours": "09:00-21:00",
        "giftAvailable": false,
        "totalSold": 57,
        "redeemedCount": 23,
        "viewCount": 340,
        "averageRating": 4.7,
        "reviewCount": 18,
        "options": [
          {
            "id": 1,
            "title": "Стандарт (60 мин)",
            "regularPrice": 300000,
            "couponPrice": 150000,
            "quantitySold": 45,
            "quantityLimit": 100,
            "status": "ACTIVE"
          },
          {
            "id": 2,
            "title": "Премиум (90 мин)",
            "regularPrice": 500000,
            "couponPrice": 250000,
            "quantitySold": 12,
            "quantityLimit": 50,
            "status": "ACTIVE"
          }
        ],
        "images": [
          "/media/spa-1.jpg",
          "/media/spa-2.jpg"
        ],
        "status": "ACTIVE",
        "createdAt": "2026-03-20T10:00:00"
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 10 },
    "totalElements": 56,
    "totalPages": 6,
    "first": true,
    "last": false
  }
}
```

---

### GET `/api/v1/coupons/{id}` — Детали купона

**Auth:** ❌ Не требуется

**Response:** `200 OK` — один объект CouponOfferResponse (формат как выше)

**Ошибки:**
| Код | Сообщение |
|---|---|
| `404` | "Купон не найден" |

---

### GET `/api/v1/coupons/top-selling` — Топ продаж

**Auth:** ❌ Не требуется

| Параметр | Тип | Default | Описание |
|---|---|---|---|
| `limit` | int | 10 | Количество (макс 50) |

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [
    { "id": 1, "title": "...", "fromPrice": 150000, "discountPercent": 50, ... },
    { "id": 7, "title": "...", "fromPrice": 80000, "discountPercent": 40, ... }
  ]
}
```

---

### GET `/api/v1/categories` — Категории купонов

**Auth:** ❌ Не требуется

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "Еда и напитки", "slug": "food", "iconUrl": "/media/icon-food.svg" },
    { "id": 2, "name": "Beauty", "slug": "beauty", "iconUrl": "/media/icon-beauty.svg" },
    { "id": 3, "name": "Развлечения", "slug": "entertainment", "iconUrl": "/media/icon-fun.svg" },
    { "id": 4, "name": "Здоровье и спорт", "slug": "health-sport", "iconUrl": "/media/icon-health.svg" },
    { "id": 5, "name": "Услуги", "slug": "services", "iconUrl": "/media/icon-services.svg" },
    { "id": 6, "name": "Сертификаты и подарки", "slug": "gifts", "iconUrl": "/media/icon-gifts.svg" }
  ]
}
```

---

## 6. Endpoints — Order Service

Base URL: `/api/v1`

### GET `/api/v1/cart` — Получить корзину

**Auth:** ✅ Bearer Token

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": 12,
    "userId": 1,
    "items": [
      {
        "id": 101,
        "couponOfferId": 1,
        "couponOptionId": 2,
        "couponTitle": "Скидка 50% на SPA массаж",
        "optionName": "Премиум (90 мин)",
        "price": 250000,
        "quantity": 1,
        "gift": false,
        "coverImageUrl": "/media/spa-cover.jpg"
      }
    ],
    "totalAmount": 250000,
    "itemCount": 1
  }
}
```

---

### POST `/api/v1/cart/items` — Добавить в корзину

**Auth:** ✅ Bearer Token

**Request:**
```json
{
  "couponOfferId": 1,
  "couponOptionId": 2,
  "quantity": 1,
  "gift": false
}
```

**Response:** `200 OK` — Обновлённая корзина (формат выше)

---

### DELETE `/api/v1/cart/items/{id}` — Удалить из корзины

**Auth:** ✅ Bearer Token

**Response:** `200 OK`

---

### POST `/api/v1/orders` — Оформить заказ (checkout)

**Auth:** ✅ Bearer Token

**Request:** Тело не требуется (берёт из текущей корзины)

**Response:** `201 Created`
```json
{
  "success": true,
  "message": "Заказ создан",
  "data": {
    "id": 45,
    "orderNumber": "TD-20260325-00045",
    "userId": 1,
    "status": "PENDING",
    "totalAmount": 250000,
    "items": [
      {
        "id": 301,
        "couponOfferId": 1,
        "couponOptionId": 2,
        "couponTitle": "Скидка 50% на SPA массаж",
        "optionName": "Премиум (90 мин)",
        "price": 250000,
        "quantity": 1
      }
    ],
    "createdAt": "2026-03-25T01:00:00"
  }
}
```

**Ошибки:**
| Код | Сообщение |
|---|---|
| `400` | "Корзина пуста" |

**Что происходит после checkout:**
```
Frontend: POST /orders → Получает orderId
          ↓
Backend:  OrderCreatedEvent → RabbitMQ → payment-service
          ↓
          PaymentService создаёт Payment (status: PENDING)
          ↓
Frontend: Перенаправить на страницу оплаты
          GET /payments/order/{orderId} → получить paymentUrl
```

---

### GET `/api/v1/orders` — Мои заказы

**Auth:** ✅ Bearer Token

| Параметр | Тип | Default |
|---|---|---|
| `page` | int | 0 |
| `size` | int | 20 |

**Response:** `200 OK` — Page<Order>

---

### GET `/api/v1/orders/{id}` — Детали заказа

**Auth:** ✅ Bearer Token

**Response:** `200 OK` — один Order

---

### GET `/api/v1/orders/my-coupons` — Мои купоны

**Auth:** ✅ Bearer Token

| Параметр | Тип | Default | Описание |
|---|---|---|---|
| `status` | String | null | `ACTIVE`, `USED`, `EXPIRED`, `CANCELLED` |

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "id": 501,
      "couponOfferId": 1,
      "couponOptionId": 10,
      "couponTitle": "Скидка 50% на SPA массаж",
      "optionTitle": "Премиум (90 мин)",
      "couponCode": "TDSP-AB12CD",
      "qrToken": "uuid-unique-qr-token",
      "status": "ACTIVE",
      "merchantId": 77,
      "merchantName": "SPA Oasis",
      "merchantAddress": "Ташкент, ул. Амира Темура, 10",
      "merchantPhone": "+998901234567",
      "merchantWorkingHours": "10:00-22:00",
      "purchasedAt": "2026-03-25T01:05:00",
      "expiresAt": "2026-06-30T23:59:59",
      "usedAt": null
    }
  ]
}
```

> Merchant usage fields (`merchantName`, `merchantAddress`, `merchantPhone`, `merchantWorkingHours`) are purchase-time snapshots. They are shown in the user's profile even if the original public coupon offer later becomes `SOLD_OUT` or `ARCHIVED`.

**Статусы купона:**

```
ACTIVE → купон куплен, можно использовать
USED   → купон погашен (предъявлен партнёру)
EXPIRED → срок истёк
CANCELLED → отменён
```

---

### POST `/api/v1/orders/redeem` — Погашение купона (Legacy)

> **Deprecated.** Используйте `POST /api/v1/partner/redemptions` вместо этого.

**Auth:** ✅ Bearer Token (PARTNER, ADMIN, SUPER_ADMIN)

**Headers:**

| Header | Required | Source |
|---|---|---|
| `X-Merchant-Id` | yes | Legacy — gateway не пробрасывает; используйте новый endpoint |

**Request:**
```json
{
  "couponCode": "TDSP-AB12CD",
  "staffName": "Анна"
}
```

**Response:** `200 OK` — RedeemCouponResponse

---

### POST `/api/v1/partner/redemptions` — Погашение купона (Preferred)

**Auth:** ✅ Bearer Token (PARTNER, ADMIN, SUPER_ADMIN)

**Headers:**

| Header | Required | Source |
|---|---|---|
| `X-User-Id` | yes | API Gateway (из JWT) |

Backend резолвит `merchantId` из `X-User-Id` через coupon-service. Frontend **не** должен передавать `merchantId`.

**Request:**
```json
{
  "couponCode": "TDSP-AB12CD",
  "staffName": "Анна"
}
```

`couponCode` автоматически trim + toUpperCase на бэкенде.

**Response:** `200 OK`
```json
{
  "success": true,
  "message": "Купон использован",
  "data": {
    "purchasedCouponId": 501,
    "couponOfferId": 10,
    "couponOptionId": 20,
    "couponTitle": "SPA-массаж",
    "optionTitle": "Стандарт",
    "couponCode": "TDSP-AB12CD",
    "status": "USED",
    "merchantId": 77,
    "merchantName": "SPA Oasis",
    "purchasedAt": "2026-04-20T10:00:00",
    "expiresAt": "2026-05-20T10:00:00",
    "usedAt": "2026-04-26T12:00:00"
  }
}
```

**Ошибки:**
- `409` — купон уже использован / принадлежит другому мерчанту / мерчант не активен
- `404` — купон не найден

---

### GET `/api/v1/partner/stats` — Статистика партнёра

**Auth:** ✅ Bearer Token (PARTNER, ADMIN, SUPER_ADMIN)

**Headers:** `X-User-Id` (из JWT gateway)

merchantId резолвится автоматически. Запрос не требует `couponOfferIds`.

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "totalCoupons": 5,
    "totalSold": 120,
    "totalRedeemed": 48,
    "totalRevenue": 2400000
  }
}
```

---

### GET `/api/v1/partner/redemptions` — История погашений

**Auth:** ✅ Bearer Token (PARTNER, ADMIN, SUPER_ADMIN)

**Headers:** `X-User-Id` (из JWT gateway)

**Params:** `page` (default: 0), `size` (default: 20)

**Response:** `200 OK` — Page<RedemptionResponse>

---

### GET `/api/v1/orders/{orderId}/coupons` — Купоны заказа

**Auth:** ✅ Bearer Token

**Response:** `200 OK` — список PurchasedCoupon для данного заказа

---

### POST `/api/v1/orders/refund` — Запрос на возврат

**Auth:** ✅ Bearer Token

**Request:**
```json
{
  "orderId": 45,
  "reason": "Не успел использовать купон"
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": 10,
    "orderId": 45,
    "userId": 1,
    "reason": "Не успел использовать купон",
    "status": "PENDING",
    "adminComment": null,
    "createdAt": "2026-03-25T12:00:00"
  }
}
```

---

### GET `/api/v1/orders/refunds` — Мои запросы на возврат

**Auth:** ✅ Bearer Token

**Response:** `200 OK` — список RefundRequest

---

## 7. Endpoints — Payment Service

Base URL: `/api/v1/payments`

### POST `/api/v1/payments/create` — Создать платёж

**Auth:** ✅ Bearer Token

**Request:**
```json
{
  "orderId": 45,
  "userId": 1,
  "amount": 250000,
  "provider": "payme"
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": 78,
    "orderId": 45,
    "userId": 1,
    "amount": 250000,
    "provider": "PAYME",
    "status": "PENDING",
    "transactionId": null,
    "paymentUrl": null,
    "createdAt": "2026-03-25T01:00:00"
  }
}
```

---

### GET `/api/v1/payments/{id}/status` — Статус платежа

**Auth:** ✅ Bearer Token

---

### GET `/api/v1/payments/order/{orderId}` — Платёж по заказу

**Auth:** ✅ Bearer Token

---

### POST `/api/v1/payments/callback` — Webhook от провайдера

**Auth:** ❌ (вызывается платёжной системой)

**Статусы платежа:**
```
PENDING → COMPLETED → (order → PAID → coupons generated)
       → FAILED
       → REFUNDED
```

> Примечание: Провайдеры платежей — `PAYME`, `CLICK`, `UZUM`.

---

## 8. Endpoints — Directory (Bazaars & Shops)

> **Текущий Base URL:** `/api/v1/directory` (через `DirectoryController` в coupon-service)  
> Примечание: В коде frontend и backend используется `/api/v1/directory/*`. Планируется миграция на отдельный bazaar-service — см. `coupon-merchant-db-refactor-plan.md`.

### GET `/api/v1/directory/bazaars` — Список базаров

**Auth:** ❌ Не требуется

| Параметр | Тип | Default |
|---|---|---|
| `search` | String | null |
| `type` | String | null |
| `page` | int | 0 |
| `size` | int | 20 |

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "Чорсу базар",
      "nameUz": "Chorsu bozori",
      "type": "BAZAAR",
      "address": "Ташкент, ул. Навои 1",
      "city": "Ташкент",
      "latitude": 41.3245,
      "longitude": 69.2345,
      "description": "Крупнейший базар Ташкента",
      "coverImageUrl": "/media/chorsu.jpg",
      "workingHours": "06:00 - 20:00",
      "phone": "+998712345678",
      "shopCount": 234,
      "status": "ACTIVE"
    }
  ],
  "totalPages": 5,
  "totalElements": 95
}
```

**Для карты Leaflet:**
```javascript
// Используйте latitude + longitude для маркеров на карте
bazaars.forEach(b => L.marker([b.latitude, b.longitude]).addTo(map));
```

---

### GET `/api/v1/directory/bazaars/{id}` — Детали базара

**Auth:** ❌ Не требуется

---

### GET `/api/v1/directory/bazaars/{id}/shops` — Магазины базара

**Auth:** ❌ Не требуется

| Параметр | Тип | Default |
|---|---|---|
| `search` | String | null |

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "id": 15,
      "name": "Электроника мир",
      "description": "Телефоны и аксессуары",
      "category": "Электроника",
      "subcategory": "Мобильные телефоны",
      "goodsDescription": "Телефоны, аксессуары, ноутбуки",
      "phone": "+998901111111",
      "workingHours": "08:00 - 18:00",
      "photos": ["/media/shop-42.jpg"],
      "locationType": "BAZAAR",
      "bazaar": { "id": 1, "name": "Чорсу базар" },
      "pavilion": "A",
      "sector": "north",
      "rowNumber": "A",
      "shopNumber": "42",
      "floorNumber": 2,
      "address": "Ташкент, ул. Навои 1",
      "latitude": 41.3245,
      "longitude": 69.2345,
      "status": "ACTIVE"
    }
  ]
}
```

---

### GET `/api/v1/directory/shops` — Все магазины / Поиск

**Auth:** ❌ Не требуется

| Параметр | Тип | Default |
|---|---|---|
| `search` | String | null |
| `category` | String | null |
| `page` | int | 0 |
| `size` | int | 20 |

---

### GET `/api/v1/directory/shops/{id}` — Детали магазина

**Auth:** ❌ Не требуется

---

### GET `/api/v1/directory/area` — Поиск по области карты

**Auth:** ❌ Не требуется

| Параметр | Тип |
|---|---|
| `minLat` | double |
| `maxLat` | double |
| `minLon` | double |
| `maxLon` | double |

**Response:** `200 OK` — `{ bazaars: [...], shops: [...] }`

---

## 9. Endpoints — Identity Service (Users)

> User-related endpoints входят в `identity-service` (единый сервис auth + профиль).

Base URL: `/api/v1/users`

### GET `/api/v1/users/me` — Мой профиль

**Auth:** ✅ Bearer Token

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com",
    "phone": "+998901234567",
    "firstName": "Иван",
    "lastName": "Иванов",
    "role": "USER",
    "avatarUrl": "/media/avatar-1.jpg",
    "emailVerified": true,
    "phoneVerified": false,
    "createdAt": "2026-03-20T10:00:00"
  }
}
```

---

### PUT `/api/v1/users/me` — Обновить профиль

**Auth:** ✅ Bearer Token

**Request:**
```json
{
  "firstName": "Иван",
  "lastName": "Иванов",
  "phone": "+998901234567",
  "avatarUrl": "/media/new-avatar.jpg"
}
```

**Response:** `200 OK` — обновлённый профиль

---

### GET `/api/v1/users/me/favorites` — Мои избранные

**Auth:** ✅ Bearer Token

**Response:** `200 OK`
```json
{
  "success": true,
  "data": [
    { "id": 1, "couponOfferId": 7, "addedAt": "2026-03-24T15:00:00" },
    { "id": 2, "couponOfferId": 12, "addedAt": "2026-03-25T08:00:00" }
  ]
}
```

---

### POST `/api/v1/users/me/favorites` — Добавить в избранное

**Auth:** ✅ Bearer Token

**Request:**
```json
{
  "couponOfferId": 7
}
```

**Response:** `201 Created`

---

### DELETE `/api/v1/users/me/favorites/{couponOfferId}` — Удалить из избранного

**Auth:** ✅ Bearer Token

**Response:** `200 OK`

---

## 10. Endpoints — Media Service

Base URL: `/api/v1/media`

### POST `/api/v1/media/upload` — Загрузка файла

**Auth:** ✅ Bearer Token  
**Content-Type:** `multipart/form-data`

**Request:**
```
FormData: file = <binary>
```

**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "fileName": "abc123-photo.jpg",
    "url": "/api/v1/media/abc123-photo.jpg"
  }
}
```

---

### GET `/api/v1/media/{fileName}` — Скачать файл

**Auth:** ❌ Не требуется  
**Content-Type:** `image/jpeg`, `image/png`, etc.

**Response:** Бинарный файл

---

### DELETE `/api/v1/media/{fileName}` — Удалить файл

**Auth:** ✅ Bearer Token  

---

## 11. Endpoints — Admin (Coupon Management)

Base URL: `/api/v1/admin` — **Все требуют роль ADMIN**

### GET `/api/v1/admin/coupons` — Все купоны (Admin)

| Параметр | Тип | Default |
|---|---|---|
| `page` | int | 0 |
| `size` | int | 20 |

---

### POST `/api/v1/admin/coupons` — Создать купон

**Request:**
```json
{
  "title": "Скидка 50% на SPA массаж",
  "shortDescription": "Релакс массаж 60 мин",
  "fullDescription": "Полное описание...",
  "merchantId": 5,
  "categoryId": 3,
  "oldPrice": 300000,
  "fromPrice": 150000,
  "discountPercent": 50,
  "coverImageUrl": "/media/spa-cover.jpg",
  "buyUntil": "2026-04-30T23:59:59",
  "useUntil": "2026-06-30T23:59:59",
  "terms": "Только для новых клиентов",
  "usageRules": "Предъявите QR код",
  "howToUse": "1. Купите. 2. Запишитесь. 3. Покажите QR.",
  "address": "Ташкент, ул. Навои 45",
  "options": [
    {
      "title": "Стандарт (60 мин)",
      "regularPrice": 300000,
      "couponPrice": 150000,
      "quantityLimit": 100
    }
  ]
}
```

**Response:** `201 Created` — CouponOfferResponse

---

### PATCH `/api/v1/admin/coupons/{id}/status` — Изменить статус

**Query:** `status` = `LEAD` | `DRAFT` | `WAITING_FOR_MERCHANT` | `REVISION_REQUESTED` | `ACTIVE` | `SOLD_OUT`

> **Note:** `ARCHIVED` нельзя установить через generic status endpoint.
> Используйте dedicated `POST /api/v1/admin/coupons/{id}/archive`.

---

### DELETE `/api/v1/admin/coupons/{id}` — Удалить купон

Допустимо только из статусов: `LEAD`, `DRAFT`, `REVISION_REQUESTED`.
`ACTIVE`, `WAITING_FOR_MERCHANT`, `SOLD_OUT`, `ARCHIVED` защищены от удаления.

---

### POST `/api/v1/admin/coupons/{id}/archive` — Архивировать купон

**Auth:** 🔒 MODERATOR / ADMIN / SUPER_ADMIN

**Допустимые исходные статусы:** `ACTIVE`, `SOLD_OUT`

Останавливает продажи, скрывает из публичного каталога.
Уже купленные сертификаты остаются действительными — никакого автоматического рефанда или отмены.

**Request:**
```json
{
  "reason": "Ошибка в условиях акции"
}
```

**Response:** `200 OK` — CouponOfferResponse с `status: "ARCHIVED"`, `archiveReason`, `archivedAt`.

**Ошибки:**
| Код | Сообщение |
|---|---|
| `400` | "Причина архивирования обязательна" |
| `400` | "Архивирование запрещено из статуса ..." |
| `404` | "Купон не найден" |

---

### GET `/api/v1/admin/merchants` — Список партнёров

### GET `/api/v1/admin/merchants/{id}` — Партнёр по ID

### POST `/api/v1/admin/merchants` — Создать партнёра

**Request:**
```json
{
  "name": "SPA Oasis",
  "description": "Премиум SPA салон",
  "logoUrl": "/media/spa-logo.jpg",
  "coverUrl": "/media/spa-cover.jpg",
  "address": "Ташкент, ул. Навои 45",
  "phone": "+998901234567",
  "email": "info@spa-oasis.uz",
  "website": "https://spa-oasis.uz",
  "workingHours": "09:00-21:00",
  "contactPerson": "Алишер"
}
```

### PUT `/api/v1/admin/merchants/{id}` — Обновить партнёра

---

### PATCH `/api/v1/admin/orders/refunds/{id}/resolve` — Решить возврат (Admin)

**Request:**
```json
{
  "approved": true,
  "adminComment": "Возврат одобрен"
}
```

---

## 12. Потоки данных — Какой компонент что вызывает

### React компонент → API endpoint

| Страница / Компонент | Вызов | Endpoint |
|---|---|---|
| `LoginPage` | `api.auth.login()` | `POST /auth/login` |
| `LoginPage` | `api.auth.register()` | `POST /auth/register` |
| `HomePage` | `api.coupons.getTopSelling()` | `GET /coupons/top-selling` |
| `HomePage` | `api.coupons.getCategories()` | `GET /categories` |
| `CouponCatalogPage` | `api.coupons.getCatalog()` | `GET /coupons?page&categoryId&search&sortBy` |
| `CouponDetailPage` | `api.coupons.getById(id)` | `GET /coupons/{id}` |
| `CouponDetailPage` | `api.orders.addToCart()` | `POST /cart/items` |
| `CartPage` | `api.orders.getCart()` | `GET /cart` |
| `CartPage` | `api.orders.removeItem()` | `DELETE /cart/items/{id}` |
| `CheckoutPage` | `api.orders.createOrder()` | `POST /orders` |
| `ProfilePage` | `api.users.getProfile()` | `GET /users/profile` |
| `ProfilePage` | `api.orders.getMyCoupons()` | `GET /orders/my-coupons` |
| `ProfilePage` | `api.users.getFavorites()` | `GET /users/favorites` |
| `BazaarMapPage` | `directoryApi.getBazaars()` | `GET /directory/bazaars` |
| `BazaarDetailPage` | `directoryApi.getBazaarById(id)` | `GET /directory/bazaars/{id}` |
| `BazaarDetailPage` | `directoryApi.getShopsByBazaar(id)` | `GET /directory/bazaars/{id}/shops` |
| `SearchPage` | `directoryApi.getShops({search})` | `GET /directory/shops?search=...` |
| `ShopDetailPage` | `directoryApi.getShopById(id)` | `GET /directory/shops/{id}` |

### Zustand Store → API

| Store | Метод | Endpoint |
|---|---|---|
| `authStore` | `login()` | `POST /auth/login` |
| `authStore` | `register()` | `POST /auth/register` |
| `authStore` | `logout()` | `POST /auth/logout` |
| `authStore` | `refresh()` | `POST /auth/refresh` |
| `cartStore` | `fetchCart()` | `GET /cart` |
| `cartStore` | `addItem()` | `POST /cart/items` |
| `cartStore` | `removeItem()` | `DELETE /cart/items/{id}` |
| `cartStore` | `checkout()` | `POST /orders` |

---

## 13. Полный жизненный цикл покупки купона

```
┌──────────────────── FRONTEND ────────────────────────────────┐
│                                                               │
│ 1. HomePage                                                   │
│    GET /coupons/top-selling → показать карточки                │
│          ↓ (клик на купон)                                    │
│                                                               │
│ 2. CouponDetailPage                                           │
│    GET /coupons/{id} → показать детали + опции                 │
│    кнопка "Купить" → POST /cart/items                          │
│          ↓                                                    │
│                                                               │
│ 3. CartPage                                                   │
│    GET /cart → список товаров + итого                          │
│    кнопка "Оформить" → POST /orders                           │
│          ↓                                                    │
│                                                               │
│ 4. CheckoutPage (после заказа)                                │
│    GET /payments/order/{orderId} → получить статус/ссылку      │
│    → перенаправить на оплату (Payme/Click)                    │
│          ↓                                                    │
└──────────────────────────────────────────────────────────────┘

┌──────────────────── BACKEND (автоматически) ─────────────────┐
│                                                               │
│ 5. OrderCreatedEvent → RabbitMQ → PaymentService              │
│    PaymentService создаёт Payment (PENDING)                   │
│          ↓ (пользователь оплачивает)                         │
│                                                               │
│ 6. POST /payments/callback ← платёжная система               │
│    Payment → COMPLETED                                        │
│    PaymentCompletedEvent → RabbitMQ → OrderService             │
│          ↓                                                    │
│                                                               │
│ 7. OrderService.generatePurchasedCoupons()                    │
│    Создаёт PurchasedCoupon[] с уникальными кодами             │
│    CouponPurchasedEvent → RabbitMQ → NotificationService       │
│          ↓                                                    │
│                                                               │
│ 8. NotificationService                                        │
│    → Email: "Ваш купон: TDSP-AB12CD"                         │
│    → SMS: "TopDim: Купон TDSP-AB12CD активен"                 │
│                                                               │
└──────────────────────────────────────────────────────────────┘

┌──────────────────── FRONTEND (после оплаты) ─────────────────┐
│                                                               │
│ 9. ProfilePage → вкладка "Мои купоны"                         │
│    GET /orders/my-coupons?status=ACTIVE                       │
│    → показать QR код (qrToken) + код (code)                   │
│          ↓ (пользователь идёт к партнёру)                    │
│                                                               │
│ 10. Партнёр сканирует QR                                      │
│     POST /orders/redeem { couponCode: "TDSP-AB12CD" }         │
│     → статус купона: USED                                     │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 14. Обработка ошибок на фронте

```typescript
// Универсальный обработчик
try {
  const { data } = await api.post('/api/v1/orders', {});
  if (data.success) {
    // Успех
    toast.success(data.message);
  }
} catch (error) {
  if (error.response) {
    const { status, data } = error.response;
    
    switch (status) {
      case 400:
        toast.error(data.message); // "Корзина пуста"
        break;
      case 401:
        // Interceptor автоматически попробует refresh
        // Если и refresh вернул 401 → редирект на /login
        authStore.logout();
        navigate('/login');
        break;
      case 403:
        toast.error('Нет доступа');
        break;
      case 404:
        toast.error('Не найдено');
        break;
      default:
        toast.error('Ошибка сервера');
    }
  }
}
```

---

## 15. Цены и валюта

- **Все цены в сумах (UZS)** как целые числа (без копеек)
- Пример: `150000` = 150 000 сум
- Форматирование на фронте: `150 000 сум`

```typescript
// utils/format.ts
export const formatPrice = (price: number): string => {
  return new Intl.NumberFormat('ru-RU').format(price) + ' сум';
};
// formatPrice(150000) → "150 000 сум"
```

---

## 16. Сводная таблица всех endpoints

| # | Метод | Endpoint | Auth | Сервис |
|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/register` | ❌ | auth |
| 2 | POST | `/api/v1/auth/login` | ❌ | auth |
| 3 | POST | `/api/v1/auth/refresh` | ❌ | auth |
| 4 | POST | `/api/v1/auth/logout` | ✅ | auth |
| 5 | GET | `/api/v1/categories` | ❌ | coupon |
| 6 | GET | `/api/v1/coupons` | ❌ | coupon |
| 7 | GET | `/api/v1/coupons/{id}` | ❌ | coupon |
| 8 | GET | `/api/v1/coupons/top-selling` | ❌ | coupon |
| 9 | GET | `/api/v1/cart` | ✅ | order |
| 10 | POST | `/api/v1/cart/items` | ✅ | order |
| 11 | DELETE | `/api/v1/cart/items/{id}` | ✅ | order |
| 12 | POST | `/api/v1/orders` | ✅ | order |
| 13 | GET | `/api/v1/orders` | ✅ | order |
| 14 | GET | `/api/v1/orders/{id}` | ✅ | order |
| 15 | GET | `/api/v1/orders/my-coupons` | ✅ | order |
| 16 | GET | `/api/v1/orders/{orderId}/coupons` | ✅ | order |
| 17 | POST | `/api/v1/orders/redeem` | ✅ | order |
| 18 | POST | `/api/v1/orders/refund` | ✅ | order |
| 19 | GET | `/api/v1/orders/refunds` | ✅ | order |
| 20 | POST | `/api/v1/payments/create` | ✅ | payment |
| 21 | GET | `/api/v1/payments/{id}/status` | ✅ | payment |
| 22 | GET | `/api/v1/payments/order/{orderId}` | ✅ | payment |
| 23 | POST | `/api/v1/payments/callback` | ❌ | payment |
| 24 | GET | `/api/v1/directory/bazaars` | ❌ | coupon (directory) |
| 25 | GET | `/api/v1/directory/bazaars/{id}` | ❌ | coupon (directory) |
| 26 | GET | `/api/v1/directory/bazaars/{id}/shops` | ❌ | coupon (directory) |
| 27 | GET | `/api/v1/directory/shops` | ❌ | coupon (directory) |
| 28 | GET | `/api/v1/directory/shops/{id}` | ❌ | coupon (directory) |
| 29 | GET | `/api/v1/directory/area` | ❌ | coupon (directory) |
| 31 | GET | `/api/v1/users/me` | ✅ | user |
| 32 | PUT | `/api/v1/users/me` | ✅ | user |
| 33 | GET | `/api/v1/users/me/favorites` | ✅ | user |
| 34 | POST | `/api/v1/users/me/favorites` | ✅ | user |
| 35 | DELETE | `/api/v1/users/me/favorites/{couponOfferId}` | ✅ | user |
| 36 | POST | `/api/v1/media/upload` | ✅ | media |
| 37 | GET | `/api/v1/media/{fileName}` | ❌ | media |
| 38 | DELETE | `/api/v1/media/{fileName}` | ✅ | media |
| 39 | GET | `/api/v1/admin/coupons` | 🔒 ADMIN | coupon |
| 40 | POST | `/api/v1/admin/coupons` | 🔒 ADMIN | coupon |
| 41 | PATCH | `/api/v1/admin/coupons/{id}/status` | 🔒 ADMIN | coupon |
| 42 | DELETE | `/api/v1/admin/coupons/{id}` | 🔒 ADMIN | coupon |
| 42a | POST | `/api/v1/admin/coupons/{id}/archive` | 🔒 ADMIN | coupon |
| 43 | GET | `/api/v1/admin/merchants` | 🔒 ADMIN | coupon |
| 44 | GET | `/api/v1/admin/merchants/{id}` | 🔒 ADMIN | coupon |
| 45 | POST | `/api/v1/admin/merchants` | 🔒 ADMIN | coupon |
| 46 | PUT | `/api/v1/admin/merchants/{id}` | 🔒 ADMIN | coupon |
| 47 | GET | `/api/v1/admin/users` | 🔒 ADMIN | user |
| 48 | PATCH | `/api/v1/admin/users/{id}/block` | 🔒 ADMIN | user |
| 49 | POST | `/api/v1/admin/promocodes` | 🔒 ADMIN/PARTNER | coupon |
| 50 | POST | `/api/v1/partner/staff` | 🔒 PARTNER | user |
| 51 | GET | `/api/v1/partner/staff` | 🔒 PARTNER | user |
| 52 | GET | `/api/v1/mod/coupons` | 🛡️ MODERATOR | coupon |
| 53 | PATCH | `/api/v1/mod/coupons/{id}/review` | 🛡️ MODERATOR | coupon |
| 54 | GET | `/api/v1/mod/complaints` | 🛡️ MODERATOR | order |
| 55 | PATCH | `/api/v1/mod/complaints/{id}/resolve`| 🛡️ MODERATOR | order |
| 56 | PATCH | `/api/v1/mod/reviews/{id}/status` | 🛡️ MODERATOR | coupon |
| 57 | POST | `/api/v1/super/admins` | 👑 SUPER_ADMIN | auth |
| 58 | PATCH | `/api/v1/super/users/{id}/role` | 👑 SUPER_ADMIN | auth |
| 59 | GET | `/api/v1/super/audit-logs` | 👑 SUPER_ADMIN | auth |
| 60 | POST | `/api/v1/reviews` | ✅ USER | coupon |
| 61 | POST | `/api/v1/complaints` | ✅ USER | order |
| 62 | GET | `/api/v1/notifications` | ✅ USER | notification |
| 63 | PATCH | `/api/v1/notifications/{id}/read` | ✅ USER | notification |
