# Купоны — Создание, Публикация, Погашение

## Обзор

Купон проходит полный жизненный цикл: **заявка (LEAD) → подготовка (DRAFT) → согласование с мерчантом (WAITING_FOR_MERCHANT) → публикация (ACTIVE) → покупка → погашение → возврат**.

---

## 1. Создание купона (Консьерж-модель)

### Путь А: Заявка из Telegram-бота → LEAD

```
POST /api/v1/bot/coupons/leads
Внутренний вызов: Telegram-бот
Контроллер: BotWebhookController
Сервис: CouponOfferService.createLeadFromBot()
Результат: status = LEAD
```

### Путь Б: Модератор берёт лид в работу → DRAFT

```
PATCH /api/v1/admin/coupons/{id}/take-to-work
Роль: MODERATOR, ADMIN, SUPER_ADMIN
Контроллер: AdminCouponController
Сервис: CouponOfferService.takeToWork()
Результат: status = DRAFT, модератор закреплён
```

### Путь В: Модератор создаёт купон напрямую

```
POST /api/v1/admin/coupons
Роль: MODERATOR, ADMIN, SUPER_ADMIN
Контроллер: AdminCouponController
Сервис: CouponOfferService.create()
Результат: status = DRAFT
```

---

## 2. Согласование с мерчантом

### Отправка на согласование → WAITING_FOR_MERCHANT

```
POST /api/v1/admin/coupons/{id}/send-to-approval
Роль: MODERATOR, ADMIN, SUPER_ADMIN
Контроллер: AdminCouponController
Сервис: CouponOfferService.sendToApproval()
Результат: status = WAITING_FOR_MERCHANT
```

### Мерчант одобряет → ACTIVE

```
POST /api/v1/bot/coupons/{id}/approve
Внутренний вызов: Telegram-бот
Сервис: CouponOfferService.approveByMerchant()
Результат: status = ACTIVE (купон в каталоге)

⚠ Публикация требует publication-ready мерчанта:
  - у мерчанта есть active primary location с заполненным адресом.
  - если мерчант не готов — возвращается 409 Conflict с бизнес-причиной.
```

### Мерчант просит правки → REVISION_REQUESTED

```
POST /api/v1/bot/coupons/{id}/reject
Внутренний вызов: Telegram-бот
Body: { "comment": "Поправьте цену" }
Сервис: CouponOfferService.requestRevisionByMerchant()
Результат: status = REVISION_REQUESTED → модератор получает задачу
```

### Модератор пересогласовывает купон

```
PATCH /api/v1/mod/coupons/{id}/review
Body: { "status": "APPROVE" | "REJECT", "reason": "..." }
Сервис: ModCouponService.reviewCoupon()
```

- **APPROVE** → вызывает `approveByMerchant()`, статус = `ACTIVE` (при наличии publication-ready мерчанта)
- **REJECT** → вызывает `requestRevisionByMerchant()`, статус = `REVISION_REQUESTED`

Уведомления отправляются через RabbitMQ → notification-service.

---

## 3. Управление статусами (Админ)

```
PATCH /api/v1/admin/coupons/{id}/status?status=ACTIVE
Роль: MODERATOR, ADMIN, SUPER_ADMIN
Сервис: CouponOfferService.updateStatus()
```

### Все статусы купона

| Статус                   | Кто ставит        | Виден покупателям | Описание                                    |
|--------------------------|-------------------|-------------------|---------------------------------------------|
| `LEAD`                   | Telegram-бот      | ❌                | Заявка/лид — начальная точка                |
| `DRAFT`                  | Модератор         | ❌                | Черновик, заполняется модератором           |
| `WAITING_FOR_MERCHANT`   | Модератор         | ❌                | Отправлен мерчанту на согласование          |
| `REVISION_REQUESTED`     | Мерчант           | ❌                | Мерчант запросил правки                     |
| `ACTIVE`                 | Мерчант/Модератор | ✅                | Опубликован, можно купить. Immutable.       |
| `SOLD_OUT`               | Система           | ❌                | Все сертификаты распроданы. Immutable.       |
| `ARCHIVED`               | Staff             | ❌                | Снят с продажи. Купленные купоны не меняются. |

> **Immutability:** `ACTIVE` и `SOLD_OUT` офферы нельзя редактировать.
> Для остановки продаж используйте архивирование с обязательной причиной.

### Диаграмма переходов

```
LEAD → DRAFT → WAITING_FOR_MERCHANT → ACTIVE → SOLD_OUT
                                     ↘ REVISION_REQUESTED → DRAFT → ...
                                              ACTIVE → ARCHIVED
                                           SOLD_OUT → ARCHIVED
```

---

## 4. Каталог (публичный API)

```
GET /api/v1/coupons?categoryId=1&search=пицца&sortBy=popular&page=0&size=20
Роль: публичный (без авторизации)
Контроллер: CouponController
Сервис: CouponOfferService.getCatalog()
Фильтр: только ACTIVE купоны
```

Сортировка: `popular`, `new`, `priceAsc`, `priceDesc`, `discount`

---

## 5. Покупка купона

### a) Добавление в корзину

```
POST /api/v1/cart/items
Роль: USER
Body: { couponOfferId, couponOptionId, couponTitle, optionTitle, unitPrice, quantity }
Сервис: OrderService.addToCart()
```

### b) Оформление заказа (Checkout)

```
POST /api/v1/orders
Роль: USER
Body: { email, phone }
Сервис: OrderService.createOrder()
Результат: Order (PENDING), корзина очищается
Событие: order.created → RabbitMQ
```

### c) После оплаты — генерация купонов

```
OrderService.generatePurchasedCoupons(orderId)
Вызывается: после подтверждения оплаты от payment-service
Результат: PurchasedCoupon × quantity (ACTIVE)
  - couponCode: "CP-A1B2C3D4" (уникальный)
  - qrToken: UUID (для QR-кода)
Событие: coupon.purchased → RabbitMQ (для email/SMS)
```

### d) Просмотр купленных купонов

```
GET /api/v1/orders/my-coupons?status=ACTIVE
Роль: USER
Сервис: OrderService.getUserCoupons()
Возвращает: [{couponCode, qrToken, status, purchasedAt, expiresAt}]
```

---

## 6. Погашение купона

```
POST /api/v1/orders/redeem
Роль: PARTNER, ADMIN, SUPER_ADMIN
Headers: X-Merchant-Id: 5
Body: { "couponCode": "CP-A1B2C3D4", "staffName": "Иван" }
Сервис: OrderService.redeemCoupon()
```

### Логика:

1. Ищет `PurchasedCoupon` по `couponCode`
2. Проверяет что статус = `ACTIVE` (иначе ошибка)
3. Меняет статус → `USED`, ставит `usedAt = now()`
4. Создаёт запись `Redemption` (кто, когда, где погасил)

### Статусы купленного купона

| Статус     | Описание                |
|------------|-------------------------|
| `ACTIVE`   | Можно использовать       |
| `USED`     | Погашён партнёром        |
| `EXPIRED`  | Истёк срок действия      |
| `CANCELLED` | Отменён                 |

---

## 7. Возврат

### Запрос от пользователя

```
POST /api/v1/orders/{orderId}/refund
Роль: USER
Body: { "reason": "Не смог воспользоваться" }
Результат: RefundRequest (PENDING)
```

### Решение админа

```
PATCH /api/v1/admin/refunds/{id}
Body: { "status": "APPROVED" | "REJECTED", "comment": "..." }
Результат: статус обновлен, resolvedAt = now()
```

---

## 8. Партнёрская статистика

```
GET /api/v1/partner/stats?couponOfferIds=1,2,3
Роль: PARTNER
Ответ: { totalCoupons, totalSold, totalRedeemed, totalRevenue }

GET /api/v1/partner/redemptions?page=0&size=20
Роль: PARTNER
Ответ: история погашений с пагинацией
```

---

## Что НЕ реализовано

| Функция | Описание |
|---------|----------|
| auto-EXPIRED | Нет Scheduled-задачи для автоматического истечения купонов |
| Payment интеграция | `generatePurchasedCoupons()` вызывается, но нет реальной оплаты |
| Уведомления | Событие уходит в RabbitMQ, но notification-service не полностью обрабатывает |
| QR-сканер на фронте | Нет страницы/приложения для партнёра, чтобы сканировать QR |

## Обработка бизнес-ошибок (coupon flow)

| Исключение | HTTP статус | Когда |
|---|---|---|
| `IllegalStateException` | `409 Conflict` | Попытка approve/publish при отсутствии publication-ready мерчанта |
| `IllegalArgumentException` | `400 Bad Request` | Невалидные данные (например, >1 primary location у мерчанта) |
| `ResourceNotFoundException` | `404 Not Found` | Ресурс не найден |
| Прочие | `500 Internal Server Error` | Непредвиденные ошибки |
