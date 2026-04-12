# Купоны — Создание, Публикация, Погашение

## Обзор

Купон проходит полный жизненный цикл: **создание → модерация → публикация → покупка → погашение → возврат**.

---

## 1. Создание купона

### Путь А: Админ/Модератор — сразу ACTIVE

```
POST /api/v1/admin/coupons
Роль: MODERATOR, ADMIN, SUPER_ADMIN
Контроллер: AdminCouponController
Сервис: CouponOfferService.create()
Результат: status = ACTIVE (сразу в каталоге)
```

Модератор заполняет форму в admin-app → купон **сразу публикуется**, модерация не нужна.

### Путь Б: Партнёр — через модерацию

```
POST /api/v1/partner/coupons
Роль: PARTNER
Контроллер: PartnerCouponController
Сервис: PartnerCouponService.createCouponOffer()
Результат: status = PENDING_REVIEW (ждёт одобрения)
```

Партнёр заполняет форму → купон уходит на модерацию → модератор одобряет или отклоняет.

---

## 2. Модерация (только для купонов партнёров)

### Просмотр очереди

```
GET /api/v1/mod/coupons?page=0&size=10
Роль: MODERATOR, ADMIN, SUPER_ADMIN
Контроллер: ModCouponController
Сервис: ModCouponService.getPendingCoupons()
Возвращает: купоны со статусом PENDING_REVIEW
```

### Принятие решения

```
PATCH /api/v1/mod/coupons/{id}/review
Body: { "status": "APPROVE" | "REJECT", "reason": "..." }
Контроллер: ModCouponController
Сервис: ModCouponService.reviewCoupon()
```

- **APPROVE** → статус = `ACTIVE`, партнёр получает уведомление "Купон одобрен"
- **REJECT** → статус = `REJECTED`, партнёр получает уведомление с причиной

Уведомления отправляются через RabbitMQ → notification-service.

### Повторная подача

Партнёр может исправить отклонённый купон:

```
PUT /api/v1/partner/coupons/{id}
Условие: статус = DRAFT, PENDING_REVIEW или REJECTED
Результат: если был REJECTED → автоматически PENDING_REVIEW
```

---

## 3. Управление статусами (Админ)

```
PATCH /api/v1/admin/coupons/{id}/status?status=PAUSED
Роль: MODERATOR, ADMIN, SUPER_ADMIN
Сервис: CouponOfferService.updateStatus()
```

### Все статусы купона

| Статус           | Кто ставит    | Виден покупателям | Описание                          |
|------------------|---------------|-------------------|-----------------------------------|
| `DRAFT`          | Партнёр       | ❌                | Черновик                          |
| `PENDING_REVIEW` | Партнёр       | ❌                | На модерации                      |
| `ACTIVE`         | Модератор     | ✅                | Опубликован, можно купить         |
| `REJECTED`       | Модератор     | ❌                | Отклонён, можно исправить         |
| `PAUSED`         | Админ         | ❌                | Приостановлен                     |
| `EXPIRED`        | Система       | ❌                | Истёк срок buyUntil               |
| `ARCHIVED`       | Админ         | ❌                | В архиве                          |

### Диаграмма переходов

```
DRAFT → PENDING_REVIEW → ACTIVE → PAUSED → ACTIVE
                       ↘ REJECTED → (edit) → PENDING_REVIEW
                         ACTIVE → EXPIRED → ARCHIVED
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
Body: { "couponCode": "CP-A1B2C3D4", "merchantId": 5, "staffName": "Иван" }
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
| `REFUNDED` | Возвращён                |

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
| DRAFT в UI | Нет кнопки "Сохранить черновик" у партнёра |
| auto-EXPIRED | Нет Scheduled-задачи для автоматического истечения купонов |
| Payment интеграция | `generatePurchasedCoupons()` вызывается, но нет реальной оплаты |
| Уведомления | Событие уходит в RabbitMQ, но notification-service не полностью обрабатывает |
| QR-сканер на фронте | Нет страницы/приложения для партнёра, чтобы сканировать QR |
