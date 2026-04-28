# План имплементации пользовательского флоу покупки купона

## 1. Назначение документа

Этот документ переводит анализ текущего состояния в **практический план разработки** для сквозного пользовательского сценария:

`поиск купона → выбор опции → guest local cart → login → merge в backend cart → checkout → платеж → выдача купона → профиль → email/SMS`

Документ отвечает на вопросы:

- какой flow считаем каноническим;
- какие решения уже приняты;
- что делать по этапам;
- что менять во frontend;
- что менять в backend;
- как понять, что flow действительно заработал.

## 2. Исходная точка

### Подтверждено кодом

Сейчас flow не является рабочим E2E-сценарием.

Ключевые проблемы исходного состояния:

- storefront использует локальную корзину, а backend оформляет заказ из серверной корзины;
- checkout отправляет пустой `email`, хотя backend требует обязательный email;
- автоматическая цепочка `order.created → payment created` в текущем коде не собрана;
- guest checkout был реализован как полуготовый обходной путь;
- notification pipeline существует, но не достигается как стабильный storefront flow.

Связанный документ:

- [user-purchase-flow-status.md](/Users/abror/Projects/topdim-prd/docs/research/user-purchase-flow-status.md)

## 3. Зафиксированные продуктовые решения

Ниже перечислены решения, которые считаются принятыми и должны использоваться как baseline для разработки.

## 3.1. Каноническая корзина

### Подтверждено решением

- **Каноническая корзина для покупки — backend cart.**

### Подтверждено решением

Поведение по состояниям пользователя:

- `Гость`
  - корзина хранится только в `localStorage`;
  - cart API не вызываются;
  - действует лимит на количество позиций;
  - покупка недоступна до авторизации.

- `После логина`
  - локальная корзина мерджится в backend cart;
  - каждый товар отправляется через `POST /api/v1/cart/items`;
  - после успешного merge локальная корзина очищается сразу.

- `Авторизованный пользователь`
  - все cart-операции идут через backend;
  - localStorage может использоваться только как кэш UI, но не как source of truth.

### Подтверждено решением

- Лимит guest local cart фиксируется как `5` позиций.

## 3.2. Контакты для checkout

### Подтверждено решением

- Для checkout обязательны:
  - `email`
  - `phone`

### Следствие

- UI и backend validation должны совпадать.
- Нельзя сохранять старый flow с пустым email.

## 3.3. Payment flow

### Подтверждено решением

- Payment flow строится **через оркестрацию / event-driven цепочку**, а не через упрощенный синхронный shortcut.

### Каноническая цепочка

- `POST /orders`
- `OrderCreatedEvent`
- `payment-service` подхватывает событие и создает `Payment`
- storefront получает payment state / `paymentUrl`
- payment callback завершает платеж
- `PaymentCompletedEvent`
- `order-service` генерирует purchased coupons
- `notification-service` отправляет email/SMS

## 3.4. Auth-only purchase

### Подтверждено решением

- **Покупки доступны только после авторизации.**

### Следствие

- guest checkout исключается из MVP purchase flow;
- guest может собирать локальную корзину;
- при попытке купить guest переводится в auth flow;
- после логина корзина не должна теряться.

---

## 4. Целевой результат

### Рекомендация

После завершения этого плана должен работать следующий канонический сценарий:

1. Пользователь открывает купон.
2. Выбирает purchase option.
3. Если не авторизован:
   - товар добавляется в guest local cart.
4. При попытке перейти к покупке пользователь проходит авторизацию.
5. После логина guest cart мерджится в backend cart.
6. Checkout читает backend cart.
7. Пользователь заполняет или подтверждает `email + phone`.
8. Создается order.
9. Через `OrderCreatedEvent` создается payment.
10. Frontend получает `paymentUrl` и переводит пользователя в payment flow.
11. После успешной оплаты backend генерирует purchased coupons.
12. Пользователь видит купленный купон в разделе покупок в профиле.
13. Пользователь получает email/SMS подтверждение.

### Flow считается готовым только если

- он реально проходит от storefront до purchased coupon;
- он не зависит от mock-данных;
- он не требует ручного вмешательства разработчика;
- он покрыт happy path и основными error cases.

---

## 5. Рекомендуемая целевая архитектура MVP-флоу

## 5.1. User Flow

### Подтверждено решением

Целевой MVP flow:

1. User открывает coupon detail.
2. User выбирает option.
3. Если user неавторизован:
   - товар попадает в guest local cart.
4. User нажимает “оформить заказ”.
5. Если user неавторизован:
   - попадает на login/registration;
   - после логина выполняется merge cart.
6. Checkout читает backend cart:
   - `GET /api/v1/cart`
7. Frontend вызывает:
   - `POST /api/v1/orders`
8. `order-service` публикует `OrderCreatedEvent`
9. `payment-service` создает `Payment`
10. Frontend получает payment state / `paymentUrl`
11. User переходит в payment flow
12. Payment callback завершает платеж:
   - `POST /api/v1/payments/callback`
13. `PaymentCompletedEvent`
14. `order-service` генерирует purchased coupons
15. `notification-service` отправляет email/SMS
16. User видит купоны в разделе покупок в профиле и/или на success page

## 5.2. Technical Flow

### Подтверждено решением

Каноническая техническая цепочка:

- guest local cart
- login
- merge local cart → backend cart
- frontend → `order`
- `OrderCreatedEvent` → `payment-service`
- storefront → payment read-model
- payment callback → `PaymentCompletedEvent`
- `order-service` → `generatePurchasedCoupons`
- `order-service` → `CouponPurchasedEvent`
- `notification-service` → email/SMS

---

## 6. Этапы имплементации

## Этап 0. Freeze решений и границ

### Цель

Зафиксировать baseline, чтобы команда не строила несколько конкурирующих checkout моделей.

### Уже принято

- backend cart — каноническая;
- `email + phone` обязательны;
- payment flow — event-driven/orchestrated;
- guest checkout исключен;
- guest cart существует локально до логина.

### Подтверждено решением

- guest local cart limit: `5`.

### Exit criteria

- у команды один checkout contract;
- у команды нет старых веток логики про guest checkout и синхронный `payments/create` как основную модель.

---

## Этап 1. Стабилизация backend contract

### Цель

Сделать backend API и event-модель непротиворечивыми для целевого flow.

### Backend-задачи

1. Подтвердить и задокументировать request/response contract для:
   - `POST /api/v1/cart/items`
   - `GET /api/v1/cart`
   - `DELETE /api/v1/cart/items/{id}`
   - `POST /api/v1/orders`
   - `GET /api/v1/payments/order/{orderId}`
   - `POST /api/v1/payments/callback`
   - `GET /api/v1/orders/my-coupons`

2. Подтвердить обязательные поля заказа:
   - `email`
   - `phone`

3. Подтвердить поведение пустой корзины:
   - backend возвращает явную ошибку `Корзина пуста`

4. Подтвердить поведение gift-полей:
   - сохраняются в cart item;
   - сохраняются в order item;
   - отражаются в purchased coupon, если это часть продукта.

5. Подтвердить response `POST /api/v1/orders`:
   - должен гарантированно возвращать `orderId`, `status`, `totalAmount`

6. Зафиксировать storefront-safe payment read model:
   - storefront использует `orderId` как внешний идентификатор checkout flow;
   - frontend получает payment через `GET /api/v1/payments/order/{orderId}`;
   - `paymentId` считается внутренним техническим идентификатором и не должен быть стартовой точкой UX;
   - frontend по ответу понимает, что payment уже создан, и получает `paymentUrl`.

### Рекомендация

- Если текущие entity serialization слишком “грязные”, добавить специальные DTO для storefront.

### Exit criteria

- backend contract purchase flow зафиксирован;
- frontend знает, как получать cart, order и payment state;
- в коде больше нет двусмысленности про обязательные поля checkout.

---

## Этап 2. Реализация `OrderCreatedEvent -> Payment`

### Цель

Собрать каноническую orchestration-цепочку на backend.

### Backend-задачи

1. Добавить queue/binding для:
   - `order.exchange`
   - routing key `order.created`

2. Реализовать listener в `payment-service` на `OrderCreatedEvent`.

3. При получении события создавать `Payment` со статусом `PENDING`.

4. Обеспечить возможность получения созданного payment по `orderId`.

5. Проверить, что `PaymentCompletedEvent` и downstream `PurchasedCoupon` generation продолжают работать консистентно.

### Exit criteria

- после `POST /orders` платеж реально появляется автоматически;
- storefront может прочитать `paymentUrl` через официальный backend endpoint;
- orchestration больше не является “только описанной в комментариях”.

---

## Этап 3. Разделение guest cart и authorized cart

### Цель

Убрать смешанную cart-логику на storefront.

### Frontend-задачи

1. Явно разделить cart behavior:
   - `guest cart`
   - `authorized cart`

2. Guest cart:
   - только localStorage;
   - лимит по числу позиций;
   - без cart API.

3. Authorized cart:
   - только backend cart;
   - cart page и checkout читают `GET /api/v1/cart`.

4. Убрать локальную корзину как source of truth для авторизованного пользователя.

5. Устранить “quick-buy” с захардкоженной суммой `45000`.

### Backend-задачи

1. Проверить `addToCart()`:
   - не плодит ли лишние дубликаты;
   - корректно ли работает удаление;
   - достаточно ли данных в cart response для storefront summary.

### Exit criteria

- guest и authorized cart живут по разным, но понятным правилам;
- checkout для авторизованного пользователя всегда строится из backend cart.

---

## Этап 4. Merge guest cart после логина

### Цель

Сделать переход guest → auth бесшовным.

### Frontend-задачи

1. После успешного логина проверить наличие guest local cart.
2. Для каждого товара вызвать `POST /api/v1/cart/items`.
3. Обработать частичные ошибки merge.
4. После успешного merge:
   - очистить local guest cart;
   - не использовать старую локальную корзину как активный источник checkout-состояния.
5. Сохранять intended route:
   - если пользователь шел в checkout, вернуть его в checkout после логина.

### Рекомендация

- Если merge завершился частично, вести пользователя в cart page с понятным сообщением, а не в checkout.

### Exit criteria

- пользователь не теряет товары после логина;
- после логина видит те же позиции уже в backend cart;
- local cart не продолжает конфликтовать с сервером.

---

## Этап 5. Auth-only checkout

### Цель

Сделать checkout валидным и убрать старую guest-purchase ветку.

### Frontend-задачи

1. Удалить/отключить guest-auth purchase path из checkout.
2. Если пользователь не авторизован и нажимает “оформить заказ”:
   - вести на login/registration;
   - после логина возвращать в checkout или cart.
3. Сделать обязательными поля:
   - `email`
   - `phone`
4. Для авторизованного пользователя:
   - подтягивать email/phone из профиля;
   - требовать дозаполнение отсутствующих полей.
5. Убрать вызов `ordersApi.createOrder('', fullPhone)` и заменить на валидный payload.

### Backend-задачи

1. Проверить, не осталось ли неявной зависимости checkout от `GUEST` role.
2. Добавить удобные validation messages для storefront.

### Exit criteria

- неавторизованный пользователь не может создать order;
- авторизованный checkout всегда отправляет валидные контакты;
- старый guest checkout flow больше не участвует в покупке.

---

## Этап 6. Payment UX поверх orchestration

### Цель

Сделать понятный пользовательский переход от order к payment.

### Frontend-задачи

1. После успешного `POST /orders` показывать промежуточный экран:
   - “Создаем платеж...”

2. Polling / чтение payment state через канонический endpoint:
   - выбранный MVP-вариант: `GET /api/v1/payments/order/{orderId}`

### Рекомендация

- Для этого проекта и MVP правильнее использовать `orderId` как публичный идентификатор checkout flow.
- `paymentId` должен оставаться внутренним ID платежной сущности, а не стартовой точкой пользовательского сценария.
- Практика, близкая к реальным production-проектам:
  - frontend создает order;
  - backend асинхронно создает payment;
  - frontend читает payment по `orderId`, пока payment не появится;
  - после появления payment получает `paymentUrl` и переводит пользователя в оплату.
- Такой подход лучше подходит для event-driven orchestration, потому что frontend не обязан заранее знать `paymentId`.

3. После получения `paymentUrl`:
   - делать redirect;
   - либо открывать payment step в текущем UX.

4. Реализовать экраны:
   - “Переход к оплате”
   - “Оплата успешна”
   - “Оплата не завершена / попробуйте снова”

### Backend-задачи

1. Проверить корректность `PaymentService.createPayment`.
2. Подготовить тестовый callback сценарий для dev/stage.
3. Проверить:
   - duplicate callback;
   - callback на неизвестный order;
   - callback с расхождением данных.

### Exit criteria

- storefront не попадает в “черную дыру” после order creation;
- user получает реальный payment transition;
- callback приводит к завершению payment flow.

---

## Этап 7. Purchased coupons и профиль

### Цель

Сделать purchased coupon реальным результатом пользовательской покупки.

### Backend-задачи

1. Проверить `generatePurchasedCoupons()`:
   - order переводится в `PAID`;
   - создаются `PurchasedCoupon`;
   - генерируется `couponCode`;
   - генерируется `qrToken`;
   - корректно обрабатывается `quantity > 1`.

2. Проверить обработку `gift` сценария.

### Frontend-задачи

1. После успешной оплаты вести пользователя:
   - на success page;
   - и/или в профиль / раздел “Покупки”.

2. Убедиться, что в профиле пользователь видит:
   - `ACTIVE` купоны;
   - код;
   - срок;
   - статус.

3. Если QR входит в MVP:
   - сделать working QR view.

4. Если QR не входит в MVP:
   - убрать misleading CTA “Показать QR-код”.

### Exit criteria

- после успешной оплаты пользователь реально видит приобретенный результат;
- экран профиля не выглядит “пустым”, когда купон уже создан.

---

## Этап 8. Уведомления и post-purchase confirmation

### Цель

Завершить flow внешним подтверждением покупки.

### Backend-задачи

1. Проверить `CouponPurchasedEvent`.
2. Проверить routing в `notification-service`.
3. Проверить `EmailService`:
   - real mode vs stub mode;
   - sender;
   - шаблон письма.
4. Проверить `SmsService`:
   - real mode vs stub mode;
   - безопасная деградация при ошибке.

### Frontend-задачи

1. Показать UI success confirmation.
2. Если email/SMS временно не отправились, success state покупки не должен ломаться.

### Exit criteria

- после покупки пользователь получает хотя бы один надежный канал подтверждения:
  - UI success;
  - email;
  - SMS.

---

## Этап 9. Ошибки, edge cases, QA

### Цель

Сделать flow устойчивым, а не просто “однажды работающим”.

### Обязательные кейсы QA

1. Guest добавляет товары в локальную корзину
2. Guest логинится и теряет/не теряет товары
3. Merge cart полностью успешен
4. Merge cart частично неуспешен
5. Logged-in checkout
6. Пустая backend корзина
7. Некорректный email
8. Ошибка создания order
9. Payment не создан после `OrderCreatedEvent`
10. Payment callback success
11. Payment callback duplicate
12. Payment callback failure
13. `quantity > 1`
14. Gift purchase
15. Email/SMS отключены
16. User открыл профиль до завершения callback

### Рекомендация

Минимальный набор покрытия:

- unit tests на backend critical services;
- integration test:
  - `order -> payment -> purchased coupon`
- manual QA checklist для storefront;
- smoke scenario на stage/dev.

### Exit criteria

- есть QA matrix;
- happy-path проходит стабильно;
- известны блокирующие и неблокирующие ошибки.

---

## 7. Приоритеты реализации

## P0 — обязательные до любого релиза purchase flow

### Рекомендация

- выбрать guest cart limit;
- реализовать `OrderCreatedEvent -> Payment`;
- разделить guest cart и authorized cart;
- реализовать merge после логина;
- убрать guest checkout;
- исправить checkout payload;
- сделать working payment retrieval flow;
- обеспечить post-payment generation purchased coupons.

## P1 — обязательные для полноценного MVP

### Рекомендация

- success/failure payment screens;
- профиль с working purchased coupon experience;
- email/SMS purchase confirmation;
- QA matrix;
- базовые метрики воронки checkout.

## P2 — улучшения после стабилизации

### Рекомендация

- QR polishing;
- retry/resume payment UX;
- richer notification center;
- deeper refund/complaint UX.

---

## 8. Acceptance Criteria для “flow готов”

Flow считается готовым только если выполняются все пункты ниже.

### AC-001

Guest может собрать локальную корзину в пределах лимита.

### AC-002

После логина guest cart корректно мерджится в backend cart.

### AC-003

Checkout для авторизованного пользователя читает backend cart как source of truth.

### AC-004

Checkout с валидными `email + phone` создает order без ручных обходов.

### AC-005

После `OrderCreatedEvent` автоматически создается payment.

### AC-006

Storefront получает `paymentUrl` через официальный backend flow.

### AC-007

После успешной оплаты создается хотя бы один purchased coupon.

### AC-008

Купленный купон виден пользователю в профиле.

### AC-009

Пользователь получает UI success confirmation и минимум один внешний канал подтверждения покупки.

### AC-010

Flow не зависит от mock-данных и не требует ручного вмешательства разработчика.

---

## 9. Предлагаемый порядок работы по ролям

## Product / Analyst

### Рекомендация

- Зафиксировать baseline этого документа.
- Коммуницировать в команду уже принятые решения:
  - guest cart limit = `5`;
  - после merge локальная корзина очищается;
  - storefront читает payment через `GET /api/v1/payments/order/{orderId}`.
- Утвердить acceptance criteria.
- Утвердить user-facing error/success тексты.

## Backend

### Рекомендация

1. Contracts
2. `OrderCreatedEvent -> Payment`
3. Payment read model
4. Callback and purchased coupons
5. Notification delivery

## Frontend

### Рекомендация

1. Guest vs auth cart split
2. Merge after login
3. Auth-only checkout
4. Payment waiting / redirect UX
5. Profile / success UX

## QA

### Рекомендация

1. Manual checklist
2. Happy path
3. Error paths
4. Regression на cart/profile/auth

---

## 10. Зафиксированные операционные решения

Ниже перечислены решения, которые больше не считаются открытыми вопросами и должны использоваться как baseline для реализации.

### Решение 1

guest local cart limit фиксируется как `5`.

### Решение 2

После успешного merge локальная guest-корзина очищается сразу.

### Решение 3

Storefront получает созданный payment через `GET /api/v1/payments/order/{orderId}`.

### Решение 4

`paymentId` не используется как стартовая опора пользовательского checkout UX.
Сначала frontend работает с `orderId`, а `paymentId` получает только как часть уже созданного payment read model.

---

## 11. Следующий шаг

### Рекомендация

Команда может переходить к task-level execution по backlog-документу:

- [user-purchase-flow-backlog.md](/Users/abror/Projects/topdim-prd/docs/research/user-purchase-flow-backlog.md)
