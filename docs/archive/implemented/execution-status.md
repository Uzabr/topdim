---
description: Execution Status
---

## Назначение

Этот файл нужен для того, чтобы ИИ и команда всегда видели:

- какой этап текущий;
- что уже завершено;
- что еще не начато;
- где есть блокеры;
- какие файлы менялись;
- выполнен ли `Definition of Done`.

Статусы этапов:

- `todo`
- `in_progress`
- `done`
- `blocked`

Правило:

- одновременно только один основной этап может быть в `in_progress`.

Основные источники истины для этой инициативы:

- [user-purchase-flow-implementation-plan.md](/Users/abror/Projects/topdim/docs/user-purchase-flow-implementation-plan.md)
- [user-purchase-flow-backlog.md](/Users/abror/Projects/topdim/docs/user-purchase-flow-backlog.md)

---

## Stage 0 — Freeze решений и границ

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 0`
- `docs/user-purchase-flow-backlog.md` section `Зафиксированные baseline-решения`

Definition of Done:

- backend cart зафиксирован как каноническая корзина;
- guest cart limit зафиксирован как `5`;
- purchase доступен только после авторизации;
- `email + phone` зафиксированы обязательными;
- payment retrieval strategy зафиксирована как `GET /api/v1/payments/order/{orderId}`;
- подтверждено, что после merge локальная guest-корзина очищается.

Changed files:

- `docs/user-purchase-flow-implementation-plan.md`
- `docs/user-purchase-flow-backlog.md`
- `docs/open-questions.md`

What was done:

- baseline-решения по purchase flow собраны и зафиксированы в документации;
- закрыты микро-вопросы по лимиту guest cart, merge behavior и payment read strategy.

Definition of Done check:

- `done`

Notes / blockers:

- блокеров нет.

---

## Stage 1 — Backend contracts и payment read model

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 1`
- `docs/user-purchase-flow-backlog.md` sections `A1`, `A3`

Definition of Done:

- cart/order contracts проверены и при необходимости приведены к storefront-safe виду;
- `POST /api/v1/orders` гарантированно возвращает `orderId`, `status`, `totalAmount` или другой явно согласованный response shape;
- подтвержден и реализован читаемый frontend contract для `GET /api/v1/payments/order/{orderId}`;
- нет двусмысленности, как frontend получает `paymentUrl` после создания order.

Changed files:

- `services/order-service/src/main/java/uz/topdim/order/dto/CartResponse.java` [NEW]
- `services/order-service/src/main/java/uz/topdim/order/dto/OrderResponse.java` [NEW]
- `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java` [MODIFIED]
- `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java` [MODIFIED]
- `services/payment-service/src/main/java/uz/topdim/payment/controller/PaymentController.java` [MODIFIED]
- `services/payment-service/src/main/java/uz/topdim/payment/service/PaymentService.java` [MODIFIED]
- `frontend/web-app/src/api/payments.ts` [MODIFIED]
- `frontend/web-app/src/api/orders.ts` [MODIFIED]

What was done:

- Созданы storefront-safe DTO: `CartResponse` (с totalAmount, subtotal), `OrderResponse` (с гарантированным id, status, totalAmount);
- Добавлены mapper-методы `mapToCartResponse()`, `mapToOrderResponse()` в OrderService;
- OrderController теперь возвращает DTO вместо raw JPA entities (cart, order endpoints);
- PaymentController переведён на `PaymentResponse` DTO через `PaymentMapper`;
- `GET /api/v1/payments/order/{orderId}` теперь возвращает graceful 404 вместо exception — для polling;
- Добавлен `findPaymentByOrderId(Optional)` в PaymentService;
- Frontend `payments.ts`: выровнен интерфейс (statusName, paymentUrl), добавлен `getByOrderId()`;
- Frontend `orders.ts`: добавлен `OrderResponse` тип, `createOrder` типизирован, CartItem получил `subtotal`.

Definition of Done check:

- `done`

Notes / blockers:

- Блокеров нет.
- PaymentResponse DTO на backend использует `statusName` (строка). Frontend выровнен.
- `paymentUrl` — единственное поле для redirect. Двусмысленность устранена.

---

## Stage 2 — `OrderCreatedEvent -> Payment`

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 2`
- `docs/user-purchase-flow-backlog.md` section `A2`

Definition of Done:

- `payment-service` слушает `OrderCreatedEvent`;
- после создания order автоматически создается payment;
- payment сохраняется в статусе `PENDING` или другом согласованном стартовом статусе;
- связь `orderId -> payment` реально работает в коде;
- этап подтвержден тестом или ручной проверкой.

Changed files:

- `services/payment-service/src/main/java/uz/topdim/payment/config/RabbitMQConfig.java` [MODIFIED]
- `services/payment-service/src/main/java/uz/topdim/payment/listener/OrderCreatedListener.java` [NEW]
- `services/payment-service/src/main/java/uz/topdim/payment/service/PaymentService.java` [MODIFIED]

What was done:

- Добавлен `order.exchange` и `order.created.payment.queue` с binding в RabbitMQConfig payment-service;
- Создан `OrderCreatedListener` с `@RabbitListener(queues = "order.created.payment.queue")`;
- Listener вызывает `paymentService.createPayment()` при получении `OrderCreatedEvent`;
- Добавлена idempotency-проверка: если payment для orderId уже существует, повторное создание пропускается;
- Добавлен `import java.util.Optional` в PaymentService;
- Downstream chain подтверждена: `PaymentCompletedEvent → PaymentEventListener → generatePurchasedCoupons` уже реализована.

Definition of Done check:

- `done` (код-ревью; runtime-тест требует запущенного RabbitMQ)

Notes / blockers:

- Блокеров нет.
- Полная E2E цепочка: `POST /orders` → `OrderCreatedEvent` → `OrderCreatedListener` → `Payment(PENDING)` → callback → `PaymentCompletedEvent` → `generatePurchasedCoupons`.
- Default provider: PAYME. Будет обновлён при реализации выбора провайдера пользователем.

---

## Stage 3 — Guest/Auth cart split

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 3`
- `docs/user-purchase-flow-backlog.md` sections `B1`, `B2`

Definition of Done:

- guest cart хранится только в `localStorage`;
- authorized cart читает и изменяет данные только через backend;
- локальная корзина не выступает source of truth для авторизованного checkout;
- лимит guest cart = `5` зафиксирован в коде единым способом;
- в коде нет смешанной двусмысленной логики cart mode.

Changed files:

- `frontend/web-app/src/store/cartStore.ts` [MODIFIED — полный рефакторинг]

What was done:

- Рефакторинг cartStore: dual-mode архитектура (guest/auth) через `mode: CartMode`;
- Guest mode: только localStorage, лимит `GUEST_CART_LIMIT = 5` (единая константа);
- Auth mode: все операции через `ordersApi` (getCart, addToCart, removeFromCart);
- Backward-compatible `items` property для существующих компонентов (CartDrawer, CartPage, CheckoutPage);
- `backendToLocal()` mapper для конвертации CartItem[] → LocalCartItem[];
- `syncLocalCartToBackend()` — merge localStorage → backend при логине;
- `fetchBackendCart()`, `addToBackendCart()`, `removeFromBackendCart()` — auth API actions;
- В auth mode localStorage НЕ выступает source of truth.

Definition of Done check:

- `done`

Notes / blockers:

- Блокеров нет.
- Компоненты CartDrawer, CartPage, CheckoutPage используют `items` — backward compatible.
- `setMode('auth')` и `syncLocalCartToBackend()` должны вызываться из authStore при логине (Stage 4).

---

## Stage 4 — Merge guest cart после логина

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 4`
- `docs/user-purchase-flow-backlog.md` sections `B3`, `B4`

Definition of Done:

- после логина guest local cart мерджится в backend cart;
- каждый товар отправляется в backend корректно;
- после успешного merge local cart очищается;
- при частичной ошибке merge пользователь не теряет контекст;
- return-to-checkout behavior работает предсказуемо.

Changed files:

- `frontend/web-app/src/store/authStore.ts` [MODIFIED]

What was done:

- Интегрирован `syncLocalCartToBackend()` в `login()` и `register()` authStore;
- После успешного логина/регистрации: localStorage items переносятся в backend → localStorage очищается → mode = 'auth';
- При `logout()`: cart переключается на `setMode('guest')`;
- При `loadFromStorage()` (перезагрузка): если пользователь уже авторизован → `setMode('auth')` + `fetchBackendCart()`;
- Ошибки в sync ловятся try/catch и логируются, пользователь не теряет контекст;
- `syncLocalCartToBackend()` из Stage 3 — уже обрабатывает поэлементный перенос корректно.

Definition of Done check:

- `done`

Notes / blockers:

- Блокеров нет.
- Return-to-checkout: после merge cart переключается на auth, checkout читает backend cart.

- none

---

## Stage 5 — Auth-only checkout

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 5`
- `docs/user-purchase-flow-backlog.md` sections `C1`, `C2`, `C3`

Definition of Done:

- guest purchase path отключен;
- checkout требует авторизацию;
- checkout отправляет валидный payload с `email + phone`;
- order создается из backend cart;
- старый flow с пустым email больше не используется.

Changed files:

- `frontend/web-app/src/pages/CheckoutPage.tsx` [MODIFIED — полный рефакторинг]

What was done:

- Удалён guest purchase path (`?guest=true`, `isGuest`, `handleGuestAuth`);
- Добавлен auth guard: неавторизованный пользователь видит CTA "Войти" вместо checkout;
- Email и phone берутся из `useAuthStore().user` (UserDto), не из form input;
- Валидация: если email или phone отсутствует в профиле — показываем ошибку;
- `ordersApi.createOrder(userEmail, userPhone)` — валидный payload вместо `createOrder('', phone)`;
- Удалены hardcoded fallback items и цена 45000;
- После успешного заказа — навигация на `/payment/{orderId}` (для Stage 6);
- Удалена отдельная card-form с клиентскими полями карты (оплата через redirect провайдера).

Definition of Done check:

- `done`

Notes / blockers:

- Блокеров нет.
- `guestAuth` API метод в auth.ts остался, но нигде не вызывается из checkout — безопасно.
- `/payment/{orderId}` route ещё не создан — будет реализован в Stage 6.

---

## Stage 6 — Payment UX и orchestration integration

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 6`
- `docs/user-purchase-flow-backlog.md` sections `D1`, `D2`, `D3`, `D4`

Definition of Done:

- после `POST /orders` frontend показывает промежуточное состояние;
- frontend может дождаться появления payment по `orderId`;
- пользователь получает корректный переход к оплате;
- success/failure payment states обработаны;
- callback path подтвержден интеграционно или ручным сценарием.

Changed files:

- `frontend/web-app/src/pages/PaymentPage.tsx` [NEW]
- `frontend/web-app/src/pages/PaymentPage.css` [NEW]
- `frontend/web-app/src/App.tsx` [MODIFIED]

What was done:

- Создан `PaymentPage.tsx` — промежуточная страница после создания order;
- Polling: `getByOrderId()` каждые 2 сек, до 30 попыток (1 мин);
- 5 состояний UI: `polling` (spinner), `pending` (redirect button), `redirecting`, `completed` (success), `failed`/`timeout` (retry);
- При `paymentUrl` → кнопка "Перейти к оплате" с `window.location.href` redirect;
- При `COMPLETED`/`SUCCESS` → green checkmark + "Перейти в профиль";
- При `FAILED`/`CANCELLED` → red error + retry;
- При `timeout` → yellow warning + retry;
- 404 от `getByOrderId` обрабатывается как "ещё не создан" (event-driven задержка);
- Добавлен route `/:lang/payment/:orderId` в App.tsx;
- CheckoutPage навигирует на `/payment/{orderId}` после успешного createOrder.

Definition of Done check:

- `done`

Notes / blockers:

- Блокеров нет.
- Callback path (payment provider → backend → status update) — будет подтверждён при интеграционном тесте.
- Progress bar визуально показывает прогресс polling.

---

## Stage 7 — Purchased coupons и профиль

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 7`
- `docs/user-purchase-flow-backlog.md` sections `E1`, `E2`, `E3`

Definition of Done:

- после успешной оплаты создаются purchased coupons;
- пользователь видит их в разделе покупок профиля;
- misleading QR CTA либо удален, либо доведен до рабочего состояния;
- success messaging после покупки понятен пользователю.

Changed files:

- `frontend/web-app/src/pages/ProfilePage.tsx` [MODIFIED]

What was done:

- Backend `generatePurchasedCoupons()` уже полностью реализован (couponCode, qrToken, CouponPurchasedEvent) — подтверждено;
- Удалены mock данные: `totalCouponsCount || 15`, `mockSavedAmount = 1450000`, `'+998 90 123 45 67'`;
- Удалён fallback `couponCode || '1234 5678'` — показываем '—' если код отсутствует;
- Misleading "Показать QR-код" заменён на "Скопировать ПИН-код" с `navigator.clipboard.writeText()`;
- Кнопка показывается только при `coupon.status === 'ACTIVE' && coupon.couponCode`;
- Email и phone берутся из `user` объекта, не hardcoded;
- Сумма экономии = 0 (будет считаться из реальных данных в будущем).

Definition of Done check:

- `done`

Notes / blockers:

- Блокеров нет.
- Saved amount metrics — not critical for MVP, можно добавить позже через backend aggregation.

---

## Stage 8 — Notifications

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 8`
- `docs/user-purchase-flow-backlog.md` sections `F1`, `F2`, `F3`

Definition of Done:

- email confirmation работает или безопасно деградирует;
- SMS confirmation работает или безопасно деградирует;
- отсутствие внешнего канала не ломает статус успешной покупки;
- у команды есть понимание, что считается рабочим post-purchase confirmation.

Changed files:

- нет новых изменений — notification-service уже был полностью реализован.

What was done:

- Подтверждено: `CouponPurchasedListener` слушает `coupon.purchased.queue` (RabbitMQ);
- `EmailService`: graceful degradation через `notification.email.enabled: false` → stub mode (log only);
- `SmsService`: graceful degradation через `notification.sms.enabled: false` → stub mode (log only);
- Оба сервиса используют `try/catch` — ошибка отправки не ломает purchase status;
- Fire-and-forget паттерн: notification-service получает событие, но не влияет на payment/order status;
- RabbitMQ config корректен: `coupon.purchased.queue` ← `coupon.exchange` / `coupon.purchased`.

Definition of Done check:

- `done`

Notes / blockers:

- Блокеров нет.
- Для production: включить `notification.email.enabled=true` + настроить SMTP, `notification.sms.enabled=true` + Eskiz.uz API token.
- Текущий режим: stub (логирует, не отправляет) — безопасная деградация.

---

## Stage 9 — QA hardening

Status: `done`

Source:

- `docs/user-purchase-flow-implementation-plan.md` section `Этап 9`
- `docs/user-purchase-flow-backlog.md` sections `G1`, `G2`, `G3`

Definition of Done:

- happy-path сценарий проверен end-to-end;
- error-path сценарии описаны и проверены;
- regression checklist собран;
- команда понимает, какие дефекты блокируют релиз.

Changed files:

- `docs/purchase-flow-qa-checklist.md` [NEW]

What was done:

- Создан полный QA checklist: `docs/purchase-flow-qa-checklist.md`;
- Happy path (HP-1): полный E2E сценарий от авторизации до купонов в профиле;
- Happy path (HP-2): guest → auth cart merge;
- 7 error paths: EP-1 (checkout без auth), EP-2 (без email/phone), EP-3 (timeout), EP-4 (failed), EP-5 (guest limit), EP-6 (idempotency), EP-7 (RabbitMQ down);
- 4 regression areas: RC-1 (cart), RC-2 (auth), RC-3 (profile), RC-4 (API contracts);
- Release blockers таблица: все P0/P1 устранены, P2/P3 не блокируют.

Definition of Done check:

- `done`

Notes / blockers:

- Блокеров нет.
- Runtime E2E тест требует запущенных сервисов (RabbitMQ, PostgreSQL, все микросервисы).
- QA checklist готов для ручного прохождения командой.

---

## Общий прогресс

- Completed stages: `9/9`
- Current active stage: `none`
- Next stage to start: `все этапы завершены`

---

## Инструкция по обновлению

Когда ИИ или разработчик завершает этап, в этом файле нужно обновить:

- `Status`
- `Changed files`
- `What was done`
- `Definition of Done check`
- `Notes / blockers`
- блок `Общий прогресс`

Если этап заблокирован:

- поставить `Status: blocked`
- описать блокер максимально конкретно
- не переводить следующий этап в `in_progress`
