# Backlog разработки пользовательского purchase flow

## 1. Цель документа

Это рабочий backlog для команды разработки по каноническому сценарию:

`guest local cart → login → merge cart → backend checkout → order → payment orchestration → purchased coupon → notifications`

Документ разбит по:

- этапам;
- backend/frontend/QA задачам;
- зависимостям;
- порядку выполнения;
- тому, что можно делать параллельно.

## 2. Зафиксированные baseline-решения

### Подтверждено решением

- каноническая корзина для покупки — `backend cart`;
- guest до логина использует только `localStorage cart`;
- после логина guest cart мерджится в backend cart;
- email и phone обязательны для checkout;
- покупка доступна только после авторизации;
- payment flow строится через orchestration/events;
- localStorage для авторизованного пользователя допустим только как кэш, но не источник правды.

### Подтверждено решением

- guest local cart limit: `5`.

## 3. Стратегия выполнения

### Рекомендация

Выполнять работу в таком порядке:

1. Сначала contracts и orchestration backend.
2. Потом auth/cart/frontend merge.
3. Потом checkout UI и order creation.
4. Потом payment UX.
5. Потом profile/notifications.
6. Потом QA hardening.

### Риск

- Если начать с UI checkout без завершения contract/orchestration, команда снова придет к полуготовому flow.

---

## 4. Этап A — Backend contracts и orchestration foundation

## A1. Подтвердить и стабилизировать cart/order contracts

### Тип

`Backend`

### Задачи

- Проверить DTO `AddToCartRequest`, `CreateOrderRequest`.
- Подтвердить response shape для:
  - `GET /api/v1/cart`
  - `POST /api/v1/cart/items`
  - `DELETE /api/v1/cart/items/{id}`
  - `POST /api/v1/orders`
- Если нужно, ввести storefront-safe response DTO вместо возврата внутренних entity.

### Acceptance criteria

- frontend может безопасно читать cart/order responses без догадок.

## A2. Реализовать `OrderCreatedEvent -> Payment` в payment-service

### Тип

`Backend`

### Задачи

- Добавить queue/binding для `order.exchange` / `order.created`.
- Добавить listener в `payment-service` на `OrderCreatedEvent`.
- При событии создавать `Payment` в статусе `PENDING`.
- Логировать `orderId -> paymentId`.

### Acceptance criteria

- после `POST /orders` в системе появляется payment без ручного вызова отдельного `payments/create`.

## A3. Определить storefront read-model для payment

### Тип

`Backend + Product`

### Задачи

- Зафиксировать, как frontend узнает payment state после event-driven создания платежа.
- Выбранный вариант для MVP:
  - `GET /api/v1/payments/order/{orderId}`

### Acceptance criteria

- у frontend есть один официальный способ получить `paymentUrl`.

## A4. Проверить `PaymentCompletedEvent -> PurchasedCoupon`

### Тип

`Backend`

### Задачи

- Протестировать callback path.
- Убедиться, что:
  - payment становится `COMPLETED`;
  - публикуется `PaymentCompletedEvent`;
  - order-service генерирует purchased coupons;
  - order становится `PAID`.

### Acceptance criteria

- test/stage сценарий проходит от payment callback до purchased coupon.

## A5. Проверить `CouponPurchasedEvent -> Email/SMS`

### Тип

`Backend`

### Задачи

- Проверить routing `coupon.exchange -> coupon.purchased.queue`.
- Проверить `notification-service`.
- Проверить stub/real mode email и SMS.

### Acceptance criteria

- после покупки сообщение уходит как минимум в логически рабочий notification pipeline.

### Зависимости этапа A

- Без `A2` и `A3` нельзя правильно строить payment UX.

---

## 5. Этап B — Guest cart и login merge

## B1. Разделить cart behavior по auth state

### Тип

`Frontend`

### Задачи

- Явно описать два cart mode:
  - `guest cart`
  - `authenticated cart`
- Guest cart хранится только локально.
- Auth cart читается только с backend.

### Acceptance criteria

- в коде больше нет смешанной неявной модели cart source of truth.

## B2. Зафиксировать guest cart limit

### Тип

`Product + Frontend`

### Задачи

- Зафиксировать лимит `5` единой константой.
- Показать понятное сообщение при превышении лимита.

### Acceptance criteria

- лимит не разбросан по проекту и не меняется “по памяти”.

## B3. Реализовать merge после логина

### Тип

`Frontend`

### Задачи

- После логина проверить наличие guest local cart.
- Для каждого товара вызвать `POST /api/v1/cart/items`.
- Обработать ошибки merge.
- После успешного merge сразу очистить local guest cart.

### Acceptance criteria

- пользователь не теряет товары после авторизации.

## B4. Return-to-checkout flow

### Тип

`Frontend`

### Задачи

- Если guest нажал “Оформить заказ”, сохранять intended route.
- После логина возвращать пользователя:
  - либо на checkout;
  - либо на cart, если merge частично неуспешен.

### Acceptance criteria

- переход guest -> login -> checkout выглядит бесшовно.

### Что можно делать параллельно

- `B1`, `B2` и часть `B4` можно делать параллельно.
- `B3` зависит от стабильного backend cart contract.

---

## 6. Этап C — Checkout и order creation

## C1. Убрать guest purchase path

### Тип

`Frontend`

### Задачи

- Удалить/отключить guest-auth purchase path из checkout.
- Если user неавторизован, checkout должен переводить в login.

### Acceptance criteria

- неавторизованный пользователь не может создать order.

## C2. Исправить checkout form contract

### Тип

`Frontend`

### Задачи

- Сделать обязательными:
  - `email`
  - `phone`
- Для авторизованного пользователя префиллить данные из профиля.
- При отсутствии данных требовать ручной ввод.

### Acceptance criteria

- checkout отправляет валидный payload для `CreateOrderRequest`.

## C3. Создание order из backend cart

### Тип

`Frontend + Backend`

### Задачи

- Checkout summary должен строиться из backend cart.
- `POST /orders` вызывается только если cart не пуст.
- Ошибка пустой корзины корректно отражается в UI.

### Acceptance criteria

- order создается ровно из того набора позиций, который пользователь видит перед оплатой.

### Зависимости этапа C

- зависит от завершения этапов `A1`, `B1`, `B3`.

---

## 7. Этап D — Payment UX и orchestration integration

## D1. Payment waiting state

### Тип

`Frontend`

### Задачи

- После успешного `POST /orders` показывать промежуточный экран:
  - “Создаем платеж...”
- Polling по `GET /api/v1/payments/order/{orderId}` до появления payment.

### Acceptance criteria

- пользователь не попадает в “черную дыру” между order и payment.

## D2. Payment provider transition

### Тип

`Frontend`

### Задачи

- Получать `paymentUrl`.
- Делать redirect в провайдерский flow или открывать payment page.

### Acceptance criteria

- у пользователя есть один явный следующий шаг к оплате.

## D3. Payment result screens

### Тип

`Frontend`

### Задачи

- Экран:
  - `успех`
  - `в процессе`
  - `ошибка / не завершено`

### Acceptance criteria

- после возврата из payment user не остается без понятного статуса заказа.

## D4. Callback integration test

### Тип

`Backend + QA`

### Задачи

- Проверить:
  - normal callback;
  - duplicate callback;
  - callback с некорректной суммой / orderId;
  - missing payment case.

### Acceptance criteria

- callback path предсказуем и не создает дубликатов purchased coupons.

---

## 8. Этап E — Purchased coupons, profile, post-purchase UX

## E1. Показ purchased coupons

### Тип

`Frontend`

### Задачи

- Проверить профиль после успешной оплаты.
- Обеспечить, что новые купоны видны в разделе покупок профиля без лишних ручных действий.

### Acceptance criteria

- после покупки купон реально отображается в разделе покупок профиля.

## E2. Убрать misleading QR CTA или довести до рабочего состояния

### Тип

`Frontend`

### Задачи

- Если QR входит в MVP:
  - реализовать экран/модалку QR.
- Если не входит:
  - убрать кнопку “Показать QR-код” до готовности.

### Acceptance criteria

- пользователь не видит неработающих post-purchase действий.

## E3. Success messaging

### Тип

`Frontend`

### Задачи

- Показать:
  - номер заказа;
  - что покупка успешна;
  - где искать купоны;
  - что придет письмо/SMS.

### Acceptance criteria

- пользователь понимает, что делать после оплаты.

---

## 9. Этап F — Notifications

## F1. Email confirmation

### Тип

`Backend + QA`

### Задачи

- Проверить реальный email mode.
- Проверить шаблон:
  - coupon title;
  - coupon code;
  - базовая инструкция.

### Acceptance criteria

- письмо доставляется в happy path.

## F2. SMS confirmation

### Тип

`Backend + QA`

### Задачи

- Проверить реальный SMS mode.
- Убедиться, что сбой SMS не ломает успешную покупку.

### Acceptance criteria

- SMS канал либо работает, либо деградирует безопасно.

## F3. Optional in-app notification

### Тип

`Post-MVP or Nice-to-have`

### Задачи

- только после стабилизации основных каналов.

---

## 10. Этап G — QA hardening

## G1. Happy-path сценарии

### Тип

`QA`

### Задачи

- auth user: coupon -> cart -> checkout -> payment -> profile -> email
- guest: coupon -> local cart -> login -> merge -> checkout -> payment

## G2. Error-path сценарии

### Тип

`QA`

### Задачи

- invalid email
- empty backend cart
- merge cart partial failure
- payment not created after order
- payment callback delayed
- notification service unavailable

## G3. Regression список

### Тип

`QA`

### Задачи

- favorites still work
- catalog still works
- profile loading still works
- cart cleanup after successful payment works

---

## 11. Параллелизация работ

### Можно параллельно

- Backend `A1` и Frontend `B2`
- Backend `A2/A3` и Frontend `B1/B2`
- Frontend `B4` и UI work for checkout states
- QA preparation чеклистов параллельно этапам `C/D`

### Нельзя эффективно параллельно

- Финальный checkout frontend без готового backend contract
- Payment UX без готового `GET /api/v1/payments/order/{orderId}` read flow
- Profile success UX без working purchased coupon generation

---

## 12. Рекомендуемый порядок PR / merge

### PR-1

- Backend contracts cleanup
- `OrderCreatedEvent -> Payment` wiring
- payment read-model endpoint для `orderId`

### PR-2

- Frontend cart mode split
- guest local cart limit
- merge on login

### PR-3

- Checkout form cleanup
- auth-only checkout
- valid order creation

### PR-4

- payment polling / redirect / result screens

### PR-5

- profile success path
- QR CTA cleanup
- post-purchase messaging

### PR-6

- notifications verification
- QA fixes

---

## 13. Definition of Done для всей инициативы

### Flow initiative считается завершенной, если:

- cart source of truth не вызывает сомнений;
- guest товары не теряются после логина;
- order создается из backend cart;
- payment создается через orchestration;
- payment callback приводит к purchased coupons;
- purchased coupons видны пользователю;
- email/SMS или минимум один надежный post-purchase канал работает;
- ручная QA-проверка happy path проходит стабильно.

---

## 14. Ближайший следующий шаг

### Рекомендация

Backlog можно переводить в конкретные engineering tickets.
