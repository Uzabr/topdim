# 02. Бизнес-контекст

## Акторы и ответственность

| Актор | Подтверждённые действия |
|---|---|
| Гость | Просмотр каталога/отзывов/Q&A, гостевая сессия, регистрация |
| Покупатель (`USER`) | Корзина, заказ, оплата, купоны, возврат, жалоба, отзыв, вопрос, профиль |
| Владелец партнёра (`PARTNER`) | Заявки на купоны, согласование, сотрудники, статистика, погашение |
| Кассир | Погашение в пределах merchant/location staff-контекста; JWT-роль также `PARTNER` |
| Модератор | Купоны, отзывы, вопросы, жалобы |
| Администратор | Мерчанты, пользователи, возвраты, каталоги и операции модератора |
| Суперадминистратор | Персонал, роли, блокировки, audit log и все admin-возможности |
| Telegram | Вход покупателя и concierge/approval webhook-интеграция |

## Ключевые процессы

### Партнёр и купон

1. Партнёр отправляет заявку web или Telegram.
2. Admin одобряет заявку; identity-service создаёт/связывает пользователя, coupon-service — merchant.
3. Партнёр создаёт coupon lead.
4. Moderator переводит `LEAD → DRAFT`, заполняет карточку и отправляет `WAITING_FOR_MERCHANT`.
5. Партнёр принимает (`ACTIVE`) или запрашивает изменения (`REVISION_REQUESTED`).
6. Активный купон виден публично при соблюдении временных/остаточных ограничений.

### Покупка и погашение

1. Пользователь добавляет выбранную опцию в cart.
2. Checkout фиксирует snapshot и создаёт заказ.
3. `OrderCreatedEvent` инициирует payment.
4. Demo completion/callback публикует `PaymentCompletedEvent`.
5. order-service идемпотентно создаёт purchased coupons, PIN/QR и публикует sale/notification events.
6. Авторизованный staff погашает PIN/QR; order-service проверяет merchant/location и переводит купон в `USED`.
7. coupon-service получает `coupon.redeemed` и ведёт redemption ledger/statistics.

### Поддержка

- Возврат создаётся для purchased coupon, проходит admin approve/reject/complete.
- Жалоба создаётся покупателем и разрешается moderator/admin.
- Отзыв допускается только при подтверждённой eligibility из order-service и проходит модерацию.
- Вопрос проходит модерацию до публикации ответа.

## Подтверждённые правила

- Один активный payment на заказ обеспечен уникальным индексом по `payments.order_id`.
- Продажи и погашения имеют idempotency ledgers.
- Повторная pending-жалоба по одному купону блокируется уникальным частичным индексом.
- Refresh tokens хранятся как SHA-256 hash, а смена пароля/роли/блокировка повышают security version.
- Публичный каталог возвращает только доступные для публикации купоны; административные endpoints видят больше статусов.

## Допущения

Assumption: финансовое списание/возврат выполняется внешним провайдером вне текущего кода.
Evidence: `payment.mode=demo|provider`, отсутствуют production provider clients.
Confidence: High.

Assumption: целевая юрисдикция — Узбекистан, но конкретные требования PCI DSS, налогов, персональных данных и чеков не формализованы.
Evidence: домены `.uz`, телефонный формат и PRD; compliance policy отсутствует.
Confidence: Medium.

Evidence:
- `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java`
- `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- `services/identity-service/src/main/java/uz/topdim/identity/service/PartnerApplicationService.java`
- `services/*/src/main/resources/db/migration/`
