# Создание купона — актуальный флоу

Дата сверки с кодом: 2026-05-16.

Этот документ описывает текущий путь создания купонного предложения в admin-app, partner-app, Telegram bot и coupon-service. Источник правды по статусам — `CouponStatus`.

---

## 1. Каноническая модель статусов

```text
LEAD → DRAFT → WAITING_FOR_MERCHANT → ACTIVE
                                     ↘ REVISION_REQUESTED → DRAFT
ACTIVE → PAUSED / SOLD_OUT / ARCHIVED
```

Ключевое правило: новый купон не публикуется сразу. Admin create, partner request и bot lead начинают процесс со статуса `LEAD`. Публикация (`ACTIVE`) происходит только после подготовки и согласования.

---

## 2. Admin create flow

```text
frontend/admin-app
  CouponFormPage
    POST /api/v1/admin/coupons
      api-gateway
        coupon-service :8083
          AdminCouponController.createCoupon()
            CouponOfferService.create()
              coupon_offers + coupon_options + coupon_images
```

Результат `POST /api/v1/admin/coupons`: купон создан в статусе `LEAD`.

После создания модератор берёт лид в работу:

```http
PATCH /api/v1/admin/coupons/{id}/take-to-work
```

Результат: `LEAD → DRAFT`, закрепляется `assignedModeratorId/assignedModeratorName`.

---

## 3. Partner request flow

```http
POST /api/v1/partner/coupons
Authorization: Bearer <partner JWT>
```

Контроллер: `PartnerCouponController.createCouponRequest()`.

Результат: заявка партнёра создаётся как `LEAD`. Партнёр может редактировать свои предложения только в безопасных статусах `LEAD`, `DRAFT`, `REVISION_REQUESTED`.

---

## 4. Bot lead flow

```http
POST /api/v1/bot/coupons/leads
X-Bot-Api-Key: <bot key>
```

Контроллер: `BotWebhookController`.

Результат: создаётся `LEAD` из Telegram-заявки. Bot endpoints не используют JWT; они защищаются `X-Bot-Api-Key`.

---

## 5. Подготовка и согласование

Модератор заполняет/редактирует купон:

```http
PUT /api/v1/admin/coupons/{id}
```

Затем отправляет мерчанту:

```http
POST /api/v1/admin/coupons/{id}/send-to-approval
```

Результат: `DRAFT` или `REVISION_REQUESTED` → `WAITING_FOR_MERCHANT`.

Мерчант подтверждает:

```http
POST /api/v1/partner/coupons/{id}/approve
POST /api/v1/bot/coupons/{id}/approve
```

Результат: `WAITING_FOR_MERCHANT → ACTIVE`, если merchant готов к публикации.

Мерчант просит правки:

```http
POST /api/v1/partner/coupons/{id}/request-revision
POST /api/v1/bot/coupons/{id}/reject
```

Результат: `WAITING_FOR_MERCHANT → REVISION_REQUESTED`.

---

## 6. Media upload

Изображения загружаются отдельным шагом в media-service:

```http
POST /api/v1/media/upload
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

Публичное получение файла:

```http
GET /api/v1/media/{fileName}
```

Удаление файла доступно только admin/super-admin:

```http
DELETE /api/v1/media/{fileName}
```

---

## 7. Основные файлы

| Зона | Файл |
|---|---|
| Admin form | `frontend/admin-app/src/features/coupons/CouponFormPage.tsx` |
| Partner request form | `frontend/partner/src/pages/CouponRequestFormPage.tsx` |
| Admin controller | `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java` |
| Partner controller | `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java` |
| Bot controller | `services/coupon-service/src/main/java/uz/topdim/coupon/controller/BotWebhookController.java` |
| Business logic | `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java` |
| Partner business logic | `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java` |
| DTO | `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java` |
| Entity | `services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponOffer.java` |
| Status enum | `services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponStatus.java` |

---

## 8. Бизнес-риски, которые надо проверять тестами

- Нельзя создать публичный `ACTIVE` купон напрямую через create endpoint.
- Нельзя отправить на согласование купон без publication-ready merchant.
- Нельзя редактировать `ACTIVE`, `SOLD_OUT` и `ARCHIVED` как черновик.
- Нельзя одобрить чужой coupon offer из partner account.
- Повторное approve/revision не должно ломать state machine.
- Bot endpoints должны fail-closed при неверном `X-Bot-Api-Key`.
- Media upload не должен обходить auth, а delete не должен быть доступен обычному user/partner.
