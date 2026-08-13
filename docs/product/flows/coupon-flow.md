# Купоны — Создание, Публикация, Погашение

## Обзор

Купон проходит полный жизненный цикл: **заявка (LEAD) → подготовка (DRAFT) → согласование с мерчантом (WAITING_FOR_MERCHANT) → публикация (ACTIVE) → покупка → погашение → возврат**. В partner app этот же объект называется **предложением**; для покупателя и сотрудников админки это **купон**.

Важно: отдельной бизнес-сущности «акция» поверх купона нет. Цена, скидка и условия
выгоды уже входят в купон/предложение и показываются на его странице. Модерация
профиля компании, описанная ниже, меняет сведения о компании и филиалах, но не
создаёт новую акцию и не меняет опубликованные купоны.

## Рабочее место администратора

`/coupons` — единственная точка входа для работы сотрудников с купонами. В нём шесть вкладок: «Новые» (`LEAD`), «В работе» (`DRAFT`), «Требуют изменений» (`REVISION_REQUESTED`), «Ожидают партнёра» (`WAITING_FOR_MERCHANT`), «Опубликованные» (`ACTIVE`, `PAUSED`, `SOLD_OUT`) и «Архив» (`ARCHIVED`).

Таблица получает данные постранично с сервера; допустимый размер страницы — 20, 50 или 100. Kanban показывает рабочие статусы отдельными колонками и загружает по 20 записей в страницу каждой колонки. Старые адреса moderation перенаправляются на это рабочее место и не открывают самостоятельные страницы.

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
Результат: status = LEAD
```

После этого модератор берёт купон в работу через `PATCH /api/v1/admin/coupons/{id}/take-to-work`, и только тогда купон переходит в `DRAFT`.

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
```

Альтернативный путь из partner app:

```
POST /api/v1/partner/coupons/{id}/approve
Роль: PARTNER owner
Результат: status = ACTIVE
```

⚠ Публикация требует publication-ready мерчанта:
  - у мерчанта есть active primary location с заполненным адресом.
  - если мерчант не готов — возвращается 409 Conflict с бизнес-причиной.

### Мерчант просит правки → REVISION_REQUESTED

```
POST /api/v1/bot/coupons/{id}/reject
Внутренний вызов: Telegram-бот
Body: { "comment": "Поправьте цену" }
Сервис: CouponOfferService.requestRevisionByMerchant()
Результат: status = REVISION_REQUESTED → модератор получает задачу
```

Альтернативный путь из partner app:

```
POST /api/v1/partner/coupons/{id}/request-revision
Body: { "comment": "Поправьте цену" }
Результат: status = REVISION_REQUESTED
```

### Служебное решение за партнёра

```
PATCH /api/v1/mod/coupons/{id}/review
Body: { "status": "APPROVE" | "REJECT", "reason": "..." }
Сервис: ModCouponService.reviewCoupon()
```

Это исключительный путь, а не действие модератора: он доступен только `ADMIN` и `SUPER_ADMIN`. Непустая бизнес-причина обязательна, а штатное решение принимается партнёром в partner app или Telegram-боте.

- **APPROVE** → вызывает `approveByMerchant()`, статус = `ACTIVE` (при наличии publication-ready мерчанта)
- **REJECT** → вызывает `requestRevisionByMerchant()`, статус = `REVISION_REQUESTED`

Уведомления отправляются через RabbitMQ → notification-service.

---

## 3. Управление статусами (Админ)

```
PATCH /api/v1/admin/coupons/{id}/status?status=ACTIVE
Роль: ADMIN, SUPER_ADMIN
Сервис: CouponOfferService.updateStatus()
```

Этот endpoint допускает только `ACTIVE → PAUSED` и `PAUSED → ACTIVE`; остальные переходы выполняются специализированными endpoint-ами своего этапа.

### Все статусы купона

| Статус                   | Кто ставит        | Виден покупателям | Описание                                    |
|--------------------------|-------------------|-------------------|---------------------------------------------|
| `LEAD`                   | Telegram-бот      | ❌                | Заявка/лид — начальная точка                |
| `DRAFT`                  | Модератор         | ❌                | Черновик, заполняется модератором           |
| `WAITING_FOR_MERCHANT`   | Модератор         | ❌                | Отправлен мерчанту на согласование          |
| `REVISION_REQUESTED`     | Мерчант           | ❌                | Мерчант запросил правки                     |
| `ACTIVE`                 | Мерчант / ADMIN / SUPER_ADMIN (служебно) | ✅ | Опубликован, можно купить. Immutable. |
| `PAUSED`                 | ADMIN / SUPER_ADMIN | ❌              | Временно снят с продажи                     |
| `SOLD_OUT`               | Система           | ❌                | Все сертификаты распроданы. Immutable.       |
| `ARCHIVED`               | ADMIN / SUPER_ADMIN | ❌              | Снят с продажи. Купленные купоны не меняются. |

> **Immutability:** купоны в статусах `ACTIVE` и `SOLD_OUT` нельзя редактировать.
> Для остановки продаж используйте архивирование с обязательной причиной.

### Диаграмма переходов

```
LEAD → DRAFT → WAITING_FOR_MERCHANT → ACTIVE → SOLD_OUT
                                     ↘ REVISION_REQUESTED → DRAFT → ...
                                              ACTIVE → PAUSED/ARCHIVED
                                           SOLD_OUT → ARCHIVED
```

---

## 4. Каталог (публичный API)

```
GET /api/v1/coupons?categoryId=1&search=пицца&sortBy=popular&page=0&size=20
Роль: публичный (без авторизации)
Контроллер: CouponController
Сервис: CouponOfferService.getCatalog()
Фильтр: только ACTIVE купоны, с учётом `buyUntil`, лимитов и доступных options
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
POST /api/v1/partner/redemptions
Роль: PARTNER owner/cashier
Body: { "couponCode": "CP-A1B2C3D4" }
Сервис: OrderService.redeemCouponForPartnerContext()
```

```
POST /api/v1/partner/redemptions/qr
Роль: PARTNER owner/cashier
Body: { "qrToken": "uuid-from-qr" }
Сервис: OrderService.redeemCouponByQrForPartnerContext()
```

### Логика:

1. Ищет `PurchasedCoupon` по `couponCode` или `qrToken`
2. Проверяет что статус = `ACTIVE` (иначе ошибка)
3. Проверяет, что купон принадлежит merchant context текущего партнёра/кассира
4. Меняет статус → `USED`, ставит `usedAt = now()`
5. Создаёт запись `Redemption` (кто, когда, где погасил, метод PIN/QR)

### Статусы купленного купона

| Статус     | Описание                |
|------------|-------------------------|
| `ACTIVE`   | Можно использовать       |
| `USED`     | Погашён партнёром        |
| `EXPIRED`  | Истёк срок действия      |
| `REFUND_PENDING` | По купону открыт возврат |
| `REFUNDED` | Возврат завершён |
| `CANCELLED` | Отменён                 |

---

## 7. Возврат

### Запрос от пользователя

```
POST /api/v1/refunds
Роль: USER
Body: { "purchasedCouponId": 42, "reason": "Не смог воспользоваться" }
Результат: RefundRequest (PENDING)
```

### Решение админа

```
PATCH /api/v1/admin/refunds/{id}/approve
PATCH /api/v1/admin/refunds/{id}/reject
PATCH /api/v1/admin/refunds/{id}/complete
Результат: PENDING → APPROVED_PROCESSING → REFUNDED или PENDING → REJECTED
```

---

## 8. Партнёрская статистика

```
GET /api/v1/partner/stats
Роль: PARTNER
Ответ: { totalCoupons, totalSold, totalRedeemed, totalRevenue }

GET /api/v1/partner/redemptions?page=0&size=20
Роль: PARTNER
Ответ: история погашений с пагинацией

GET /api/v1/partner/dashboard
Роль: PARTNER
Ответ: агрегаты для partner dashboard
```

---

## 9. Связанный флоу: изменение профиля компании

Это самостоятельный процесс рядом с coupon flow:

```text
OWNER/MANAGER создаёт черновик из опубликованной версии
  → редактирует профиль и полный снимок филиалов
  → отправляет на модерацию (PENDING_REVIEW)
  → MODERATOR берёт в работу (IN_REVIEW)
  → APPROVED | REVISION_REQUESTED | REJECTED
```

- До `APPROVED` сайт и купонные страницы продолжают использовать старый
  опубликованный профиль.
- Одобрение атомарно заменяет профиль и филиалы, повышает `profileVersion` и
  переводит остальные активные заявки от прежней версии в `OUTDATED`.
- После `REVISION_REQUESTED` владелец или менеджер редактирует тот же снимок и
  повторно отправляет его.
- `WITHDRAWN`, `REJECTED`, `APPROVED` и `OUTDATED` можно скопировать в новый
  черновик, привязанный к актуальной версии.
- В компании может быть максимум десять активных заявок. Конкурентное создание
  одиннадцатой не должно обходить лимит.
- Филиал с активным кассиром нельзя отключить; если identity-service недоступен,
  отправка и одобрение блокируются.
- Логотип и обложка необязательны: профиль без изображений остаётся валидным.

После каждого недрафтового перехода transactional outbox создаёт событие для
автора заявки и владельца компании; одинаковый получатель дедуплицируется.
notification-service всегда сохраняет одно in-app уведомление на получателя и
`eventKey`. Внешний канал выбирается так: связанный Telegram, иначе подтверждённый
email. Ошибка внешней доставки не откатывает опубликованный профиль: выполняются
повторные попытки, а после исчерпания Telegram-попыток возможен email fallback.

Ручной acceptance-план: [partner-company-profile-manual-test-plan.md](../../qa/partner-company-profile-manual-test-plan.md).

---

## Что НЕ реализовано

| Функция | Описание |
|---------|----------|
| Scheduled auto-EXPIRED | Есть lazy-expire при чтении/операциях, но нет отдельного scheduler job |
| Реальная payment integration | Есть demo/provider mode и callbacks, но Payme/Click/Uzum provider ещё не доведён до production |
| Legacy purchase email/SMS | Профиль компании использует in-app + Telegram/email delivery; отдельный legacy SMS-канал покупки остаётся конфигурационно зависимым |
| Admin roadmap modules | `/users/list` подключён. UI базаров/магазинов и промокодов не входит в текущий admin MVP и остаётся отдельным roadmap-модулем |

## Обработка бизнес-ошибок (coupon flow)

| Исключение | HTTP статус | Когда |
|---|---|---|
| `IllegalStateException` | `409 Conflict` | Попытка approve/publish при отсутствии publication-ready мерчанта |
| `IllegalArgumentException` | `400 Bad Request` | Невалидные данные (например, >1 primary location у мерчанта) |
| `ResourceNotFoundException` | `404 Not Found` | Ресурс не найден |
| Прочие | `500 Internal Server Error` | Непредвиденные ошибки |
