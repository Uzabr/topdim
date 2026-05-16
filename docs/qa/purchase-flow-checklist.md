# Purchase Flow — QA Checklist

## Happy Path (E2E)

### HP-1: Полный сценарий покупки (авторизованный)
- [ ] Пользователь авторизован (email + phone в профиле)
- [ ] Добавляет купон в корзину (auth mode → backend cart)
- [ ] Переходит в `/checkout` → видит контактные данные из профиля
- [ ] Нажимает "Оплатить" → `POST /orders` создаёт order
- [ ] Redirect на `/payment/{orderId}` → polling начинается
- [ ] `OrderCreatedEvent` → `OrderCreatedListener` → `Payment(PENDING)` создан
- [ ] Frontend получает payment с `paymentUrl`
- [ ] Пользователь нажимает "Перейти к оплате" → redirect на провайдера
- [ ] После оплаты → callback → `PaymentCompletedEvent`
- [ ] `generatePurchasedCoupons()` → PurchasedCoupon[] создан
- [ ] `CouponPurchasedEvent` → notification-service → email/SMS (stub)
- [ ] Пользователь видит купоны в профиле (`/profile` → "Активные")
- [ ] Купон код и кнопка "Скопировать ПИН-код" работают

### HP-2: Guest → Auth cart merge
- [ ] Guest добавляет купон (localStorage, лимит 5)
- [ ] Guest авторизуется → `syncLocalCartToBackend()` вызван
- [ ] localStorage очищается
- [ ] Backend cart содержит merged items
- [ ] Cart mode = 'auth'

---

## Error Paths

### EP-1: Checkout без авторизации
- [ ] Неавторизованный → `/checkout` → видит "Войти" CTA
- [ ] Нет возможности оформить заказ как guest

### EP-2: Checkout без email/phone
- [ ] Авторизованный пользователь без email → видит ошибку "В профиле не указан email"
- [ ] Авторизованный пользователь без phone → видит ошибку "В профиле не указан телефон"

### EP-3: Payment polling timeout
- [ ] Если payment не создан за 60 сек → показывается "Платёж не создан" + retry

### EP-4: Payment failed
- [ ] Если payment status = FAILED → показывается ошибка + retry + "Перейти в профиль"

### EP-5: Guest cart limit
- [ ] Guest пытается добавить >5 items → alert "Максимум 5 позиций"

### EP-6: Duplicate payment (idempotency)
- [ ] Повторный `OrderCreatedEvent` для того же orderId → пропускается (log warning)

### EP-7: RabbitMQ unavailable
- [ ] Notification-service недоступен → payment/order status не затронут
- [ ] EmailService/SmsService ловят исключения → log error, не ломают flow

---

## Regression Checklist

### RC-1: Cart
- [ ] Guest cart работает (localStorage, лимит 5)
- [ ] Auth cart работает (backend API)
- [ ] `items` backward-compatible property корректна
- [ ] CartDrawer показывает items
- [ ] CartPage показывает items с actions
- [ ] Logout → mode='guest', cart из localStorage

### RC-2: Auth
- [ ] Login → syncLocalCartToBackend → mode='auth'
- [ ] Register → syncLocalCartToBackend → mode='auth'
- [ ] Page reload → loadFromStorage → mode='auth' + fetchBackendCart
- [ ] Logout → mode='guest'

### RC-3: Profile
- [ ] Email/phone из user объекта (не hardcoded)
- [ ] Реальный count купонов (не mock 15)
- [ ] PIN-код отображается в карточке купона
- [ ] QR-код отображается как настоящее QR-изображение (НЕ текст qrToken)
- [ ] Tabs (Активные/Использованные/Истёкшие) работают

### RC-4: Redemption (Partner App)
- [ ] PIN-погашение через `POST /api/v1/partner/redemptions` работает
- [ ] QR-погашение через `POST /api/v1/partner/redemptions/qr` работает
- [ ] Повторное погашение используемого купона → ошибка "Этот купон уже был использован."
- [ ] Погашение чужого мерчанта → ошибка "Этот купон относится к другому партнёру."
- [ ] Истёкший купон → ошибка "Срок действия купона истёк."
- [ ] После погашения — статус в профиле покупателя меняется на `USED`

### RC-5: API contracts
- [ ] OrderController → CartResponse/OrderResponse DTOs
- [ ] PaymentController → PaymentResponse DTO
- [ ] GET /payments/order/{orderId} → 404 graceful (не exception)
- [ ] Legacy `POST /api/v1/orders/redeem` — support-only compatibility; новый UI не должен зависеть от `X-Merchant-Id`

---

## Release Blockers

| Severity | Описание | Статус |
|----------|----------|--------|
| **P0 — Blocker** | `POST /orders` не создаёт order | Нет |
| **P0 — Blocker** | Payment не создаётся через event | Нет |
| **P0 — Blocker** | Purchased coupons не генерируются | Нет |
| **P1 — Critical** | Checkout доступен без авторизации | Исправлено (Stage 5) |
| **P1 — Critical** | Email пустой при создании order | Исправлено (Stage 5) |
| **P1 — Critical** | Mock данные в Profile | Исправлено (Stage 7) |
| **P2 — Major** | Notification не отправляется | Graceful degradation |
| **P3 — Minor** | Saved amount = 0 | Future feature |

---

## Итог

Все P0/P1 дефекты устранены. Оставшиеся P2/P3 не блокируют релиз.
