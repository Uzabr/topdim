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

## 4. Документация API (Swagger / OpenAPI)

Вместо ручного описания всех эндпоинтов, проект использует **автоматически генерируемую спецификацию OpenAPI (Swagger)**.
Это гарантирует, что документация всегда на 100% соответствует реальному коду микросервисов.

### Как получить доступ к Swagger UI:

Для просмотра актуальных эндпоинтов, моделей запросов и ответов, а также для тестирования API прямо из браузера:

1. Убедитесь, что сервисы запущены локально (через Docker Compose или IDE).
2. Перейдите по ссылкам ниже:

- **Identity Service (Auth, Users, Partner Applications, Admin):**
  [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
  *(Альтернативно: `http://localhost:8081/v3/api-docs` для получения JSON-спецификации)*

- **Coupon Service (Coupons, Merchants, Categories, Reviews, Bazaars):**
  [http://localhost:8082/swagger-ui/index.html](http://localhost:8082/swagger-ui/index.html)
  *(Альтернативно: `http://localhost:8082/v3/api-docs` для получения JSON-спецификации)*

### Как тестировать защищенные запросы в Swagger:
1. Выполните логин через `/api/v1/auth/login` (в Identity Service Swagger UI).
2. Скопируйте `accessToken` из ответа.
3. Нажмите кнопку **Authorize** в правом верхнем углу Swagger UI.
4. Введите `Bearer <ваш_токен>` (вместо `<ваш_токен>` вставьте скопированный JWT) и нажмите Authorize.
5. Теперь все ваши запросы из Swagger будут автоматически подписываться этим токеном.

---

## 5. Coupon Redemption API (Partner)

### MVP Flow

Погашение купонов выполняется партнёром (Owner или Cashier) через **Partner App** (`frontend/partner`).

#### PIN-код погашение

```http
POST /api/v1/partner/redemptions
Authorization: Bearer <partner JWT>
Content-Type: application/json

{ "couponCode": "CP-XXXX1234" }
```

#### QR-код погашение

```http
POST /api/v1/partner/redemptions/qr
Authorization: Bearer <partner JWT>
Content-Type: application/json

{ "qrToken": "<extracted from QR>" }
```

> **Важно:** QR payload на стороне покупателя имеет формат `TOPDIM-QR:${qrToken}`. Парсинг выполняет Partner App.

#### Legacy endpoint (backward compatibility)

```http
POST /api/v1/orders/redeem
Authorization: Bearer <partner JWT>
X-Merchant-Id: <merchantId>
Content-Type: application/json

{ "couponCode": "CP-XXXX1234" }
```

> `POST /api/v1/orders/redeem` — legacy partner-only compatibility.
> Main MVP flow uses `POST /api/v1/partner/redemptions` and `POST /api/v1/partner/redemptions/qr`.
> Admins must not redeem customer coupons as merchants in MVP.

---

## 6. Admin Support API

### Purchased Coupon Lookup

Поиск купленного купона по коду. Только для чтения, `qrToken` не возвращается.

```http
GET /api/v1/admin/purchased-coupons/lookup?couponCode=CP-XXXX1234
Authorization: Bearer <admin JWT>
```

Ответ:

```json
{
  "success": true,
  "data": {
    "purchasedCouponId": 42,
    "orderId": 15,
    "userId": 100,
    "couponTitle": "SPA для двоих",
    "optionTitle": "Стандарт",
    "couponCode": "CP-XXXX1234",
    "status": "ACTIVE",
    "merchantId": 5,
    "merchantName": "Relax SPA",
    "merchantAddress": "ул. Навои, 55",
    "purchasedAt": "2026-04-28T15:00:00",
    "expiresAt": "2026-07-28T15:00:00",
    "usedAt": null
  }
}
```

Если не найден → HTTP 404:

```json
{ "success": false, "message": "Купон с кодом 'CP-XXXX1234' не найден" }
```

---
*Документ актуализирован 2026-04-29. Добавлены разделы: Redemption API, Admin Support API.*

