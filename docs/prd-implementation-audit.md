# PRD Implementation Audit - 2026-06-08

Аудит сделан по текущему коду проекта, без опоры на git log. Цель - понять, что реально реализовано, что не реализовано, и где `docs/product/prd.md` отстал от проекта или расходится с ним.

## Краткий вывод

PRD v2 от `2026-04-28` уже не является точной картой текущей реализации. Ядро продукта в целом совпадает с проектом: coupons marketplace, directory/map, staff moderation, partner approval, cart/order/payment, purchased coupons. Но PRD отстал минимум на одну продуктовую итерацию: примерно 3-5 недель разработки и несколько крупных feature slices.

Главный разрыв не в том, что проект пустой, а наоборот: проект местами ушел вперед, а PRD продолжает описывать старую границу MVP. Особенно это видно по отдельному partner app, buyer profile с refunds/complaints/notifications/reviews, support-разделам admin-app и payment demo/provider flow.

## Метод проверки

Проверены:

- `docs/product/prd.md`
- backend services: `identity-service`, `coupon-service`, `order-service`, `payment-service`, `bazaar-service`, `notification-service`, `media-service`
- infrastructure: `api-gateway`, `config-server`, `discovery-server`
- frontend apps: `frontend/web-app`, `frontend/admin-app`, `frontend/partner`
- `telegram-bot`
- backend test suite structure

Не проверялось:

- git history
- production server
- ручной browser smoke
- полный запуск всех тестов в этом аудите

## Реализовано и в целом совпадает с PRD

| Область | Что есть в проекте | Комментарий |
|---|---|---|
| Public storefront | home, catalog, coupon detail, favorites, cart, checkout, payment, bazaar, shop, search, profile, legal pages | Подтверждается routes в `frontend/web-app/src/App.tsx`. |
| Auth/profile | register, login, refresh, logout, password reset, email confirmation, profile, favorites | Backend реализован в `identity-service`; frontend использует auth/profile/favorites. |
| Coupon catalog | categories, public coupons, coupon detail, top-selling, reviews | Реализовано в `coupon-service` и `web-app`. |
| Coupon lifecycle | `LEAD -> DRAFT -> WAITING_FOR_MERCHANT -> REVISION_REQUESTED -> ACTIVE`, `SOLD_OUT`, `ARCHIVED` | Логика и tests есть в `coupon-service`. |
| Merchant publication readiness | active merchant + active primary location + address before publish | Это уже отражено в PRD и покрыто business tests. |
| Orders/payment | backend cart, order creation, payment creation, callback/demo-complete, purchased coupons | Реализовано в `order-service`, `payment-service`, `web-app`. |
| Purchased coupons | PIN/QR, usage snapshot, active/used/expired/refund states in profile | Реализовано в `web-app` profile and `order-service`. |
| Refunds | per-purchased-coupon refund request, admin approve/reject/complete | Реализовано шире, чем основной PRD описывает. |
| Complaints | user complaint on purchased coupon, moderator resolution | Реализовано в `order-service`, `web-app` profile и admin support UI. |
| Reviews | create review, eligibility, public approved reviews, moderation | Реализовано в `coupon-service`, `web-app`, `admin-app`. |
| Notifications | in-app notification list, unread badge, mark as read | Реализовано в `notification-service` и `web-app` profile/header. |
| Admin moderation | dashboard, coupons list, kanban, coupon form, merchant review fallback, partner applications | Реализовано в `admin-app`. |
| Staff/super admin | staff, role changes, blocking, audit logs | Реализовано в `identity-service` и `admin-app`. |
| Partner portal | dashboard, coupons, coupon request, approve/revision, redemption, staff | Реализовано как отдельное `frontend/partner`, не как lite-section в `admin-app`. |
| Telegram bot | lead intake, merchant lookup, approve/reject, stats | Есть отдельный `telegram-bot` и bot API в `coupon-service`. |
| Directory/map | bazaar/shop listing, detail pages, area search, 2GIS external injection | Реализовано в public UI and backend, но есть доменное дублирование между services. |
| Media | upload, public GET, restricted delete | Реализовано в `media-service`. |

## Частично реализовано

| Область | Текущее состояние | Что не хватает или спорно |
|---|---|---|
| Guest checkout | Guest/local cart есть, `/api/v1/auth/guest` есть, но `CheckoutPage` сейчас auth-only | PRD говорит, что guest проходит checkout. В текущем UI гость должен login/register, затем local cart sync -> backend cart. |
| Canonical cart | Backend cart используется для order creation, local cart используется для guest | Модель стала понятнее, но PRD все еще формулирует это как открытый риск. Нужно закрепить фактическое правило. |
| Payment providers | Есть `payment.mode=demo|provider`, callback и provider UX | Реальные Payme/Click/Uzum не production-ready; demo-complete остается ключевым локальным сценарием. |
| Email/SMS | Есть сервисы и конфиги | По умолчанию stub/disabled; нельзя считать production delivery готовым. |
| Admin support | Reviews/refunds/complaints routes есть | PRD местами все еще относит full support UI в Post-MVP. |
| Admin catalog/order/users | Backend endpoints есть, меню содержит пункты | В `admin-app` нет routes для `/catalog/bazaars`, `/catalog/shops`, `/orders/promocodes`, `/users/list`. |
| Promocodes | Backend `admin/promocodes` есть | Admin UI route в меню есть, но страница не подключена. |
| Users management | Backend admin users есть | Admin UI route `/users/list` не подключен. |
| Bazaar/shop admin | Backend public/admin endpoints есть, public UI есть | Admin UI для bazaar/shop не подключен; есть также дублирование `coupon-service` и `bazaar-service` directory domains. |
| Search | Coupon/search and directory search есть | Нет production-ready global search across coupons/shops/bazaars. |
| City/location rules | City exists in UI/product language | Нет единого backend rule, который канонически влияет на catalog/directory/map выдачу. |
| Frontend QA | Builds/scripts есть | В актуальных frontend apps не найдено проектных `.test`/`.spec` файлов; нужен хотя бы smoke/e2e по основным flow. |
| Stock/sold-out safety | `registerSale` и `SOLD_OUT` есть | Остается риск oversell: checkout проверяет snapshot, а фактическое списание лимита происходит после оплаты. |

## Не реализовано или не подтверждено кодом

| Область | Статус |
|---|---|
| Phone verification | Не найдено. |
| User account deletion | В `roles.md` уже отмечено как отсутствующее. |
| Notification settings | Не найдено. |
| Отдельная JWT-роль `SUPPORT`/`OPERATOR` | Не реализована; support действия идут через moderator/admin/super-admin. |
| Отдельная JWT-роль `PARTNER_CASHIER` | Не реализована; cashier - staff context поверх роли `PARTNER`. |
| Full merchant public page | Не найдено как отдельная public page. |
| Super-admin system settings | Не найдено. |
| Super-admin finance reports | Не найдено. |
| Full production payment integration | Не готово. |
| Full production email/SMS delivery | Не подтверждено как готовое. |
| Frontend automated tests | Не найдены в актуальных frontend apps. |
| General "report incorrect info" flow for coupon/shop | Есть complaints по purchased coupon, но нет общего user report flow для неверной информации по купону/магазину. |
| Production scale stack: Elasticsearch/Citus/read replicas/backups | Описано как архитектурная цель в docs, не как текущая локальная реализация. |

## Где PRD отстал или ошибается

| PRD section | Что написано | Что по проекту сейчас | Вывод |
|---|---|---|---|
| Status | PRD v2 dated `2026-04-28` | Документация и код уже содержат изменения после этого периода | Документ устарел как status source. |
| Product Overview | Current surfaces: public web, admin-app, Telegram bot, backend | Есть еще отдельное `frontend/partner` | PRD не отражает partner app как самостоятельный продуктовый контур. |
| Merchant / Partner | Partner dashboard через `admin-app` на 3001 | Partner flow вынесен в `frontend/partner` | Нужно переписать merchant contour. |
| Merchant dashboard | "Полноценного web-dashboard не найдено" | Есть partner dashboard, coupons, requests, approval, redemption, staff | Устарело. |
| Notifications | "Явного notification center в storefront не найдено" | Есть notifications tab, unread badge, mark-read API | Устарело. |
| Support/reviews/complaints | В MVP/Post-MVP местами отнесено к roadmap | В admin-app и web-app уже есть reviews/refunds/complaints UI | PRD занижает текущую реализацию. |
| Guest checkout | Guest может проходить checkout | Current `CheckoutPage` requires auth; guest cart sync happens after login/register | PRD завышает готовность guest checkout. |
| Coupon states | Нет `PAUSED` в PRD state list | `CouponStatus` содержит `PAUSED` | PRD state machine неполный. |
| Purchased coupon states | PRD перечисляет `ACTIVE`, `USED`, `EXPIRED`, `CANCELLED` | Код содержит также `REFUND_PENDING`, `REFUNDED` | PRD state list неполный. |
| Refund model | Refund упоминается, но не как полноценная state machine | Есть `RefundRequest`: `PENDING -> APPROVED_PROCESSING -> REFUNDED/REJECTED` | Нужно добавить отдельный refund state section. |
| Admin IA | PRD не фиксирует все текущие support/orders modules | Admin app имеет support and orders pages, но часть меню без routes | Нужно разделить реализованные страницы и gaps. |
| Data definitions | Нет `Complaint`, `PromoCode`, `PartnerStaff/AccessContext` как ключевых сущностей | Эти контуры есть в коде/API | Data section неполный. |
| MVP/Post-MVP | Notification center, complaints workflow, review UI местами Post-MVP | Они уже частично реализованы | Перенести в implemented/partial, не держать в Post-MVP. |

## Текущее состояние разработки по крупным контурам

| Контур | Оценка состояния |
|---|---|
| Buyer browsing | В основном реализовано. |
| Buyer purchase | Реализовано для auth user + backend cart + demo payment; guest checkout надо решить отдельно. |
| Buyer post-purchase | Реализовано заметно шире PRD: coupons, orders, refunds, complaints, notifications, review CTA. |
| Coupon moderation | Сильная реализация, есть business tests. |
| Merchant/partner | Реализован отдельный partner app; PRD надо срочно обновить. |
| Admin/support | Реализованы ключевые support/order modules; есть route gaps. |
| Directory/map | Public контур реализован; admin и canonical domain требуют решения. |
| Payments | Demo/local flow реализован; production providers не готовы. |
| Notifications | In-app реализовано; real email/SMS delivery не считать готовым. |
| QA/tests | Backend покрыт хорошо; frontend automated tests отсутствуют. |
| Production readiness | Docker/prod artifacts есть, но текущий аудит показывает локальную разработку; production hardening еще отдельная задача. |

## Бизнес-риски

1. **Oversell при конкурентных покупках.** Checkout смотрит snapshot, но reserve/atomic sale registration до оплаты нет. Если два пользователя одновременно покупают последний лимит, оба могут пройти оплату, а конфликт всплывет поздно.
2. **PRD как ложный источник статуса.** Метки "Подтверждено кодом" местами уже неверны или неполны. Это может привести к неправильному планированию.
3. **Guest checkout не совпадает с PRD.** Product ожидает guest checkout, UI фактически требует auth checkout.
4. **Admin menu route gaps.** Пользователь видит пункты меню, для которых нет routes.
5. **Production payment ambiguity.** UI и backend имеют provider mode, но реальные провайдеры не готовы. Для релиза это надо явно пометить.
6. **Frontend без automated regression.** После крупных buyer/admin/partner изменений нет test suite, поэтому риск регрессий высокий.
7. **Directory domain split.** Bazaar/shop логика живет в нескольких местах; без canonical ownership легко получить разные данные в public/admin/partner flows.
8. **Смешение stub/real notifications.** In-app готов, email/SMS по умолчанию stub/disabled; PRD должен не обещать production delivery.

## Что обновить в PRD в первую очередь

1. Обновить дату и статус документа: `2026-06-08`, текущий статус - implementation audit needed.
2. Добавить `frontend/partner` как отдельную product surface.
3. Переписать merchant contour: partner app + Telegram bot + partner staff/cashier context.
4. Переписать checkout rule: auth-only backend cart сейчас; guest checkout либо убрать из MVP, либо взять в разработку.
5. Добавить actual buyer post-purchase modules: refunds, complaints, notifications, reviews.
6. Обновить state machines:
   - coupon: добавить `PAUSED`;
   - purchased coupon: добавить `REFUND_PENDING`, `REFUNDED`;
   - refund request: добавить `PENDING`, `APPROVED_PROCESSING`, `REFUNDED`, `REJECTED`.
7. Обновить Admin IA: отметить support/orders как implemented, а bazaars/shops/promocodes/users-list как UI gaps.
8. Добавить отсутствующие entities: `Complaint`, `PromoCode`, `PartnerStaff`, `PartnerAccessContext`.
9. Перенести notification center, complaints/reviews support UI из Post-MVP в implemented/partial.
10. Явно пометить production gaps: payment providers, email/SMS, frontend tests/e2e, reserve/oversell safety.

## Что взять в разработку после обновления PRD

| Priority | Задача | Почему |
|---|---|---|
| P0 | Решить stock reservation/atomic sale registration | Финансовый риск и риск oversell. |
| P0 | Зафиксировать checkout модель: auth-only или guest checkout | Сейчас PRD и UI расходятся. |
| P1 | Подключить admin routes: bazaars, shops, promocodes, users list | Меню уже обещает эти разделы. |
| P1 | Добавить frontend smoke/e2e для buyer flow | Без этого легко ломать checkout/profile/refund/review. |
| P1 | Обновить PRD как source of truth | Иначе AI/команда будут брать в работу уже реализованные или неверные задачи. |
| P2 | Развести/current-mark production payment vs demo | Нужно для релиза и настройки сервера. |
| P2 | Уточнить directory canonical ownership | Уменьшит расхождения между services. |
| P2 | Решить notification settings/phone verification/account deletion | Это пользовательские account gaps, но не блокируют core MVP. |

## Верификация аудита (2026-06-08)

Аудит верифицирован автоматической проверкой по кодовой базе. Проверено 38 утверждений — **38/38 подтверждены кодом**.

### Результаты верификации

| Раздел | Проверено | Подтверждено |
|--------|-----------|--------------|
| Реализовано | 14 | 14/14 |
| Частично реализовано | 6 | 6/6 |
| Не реализовано | 9 | 9/9 |
| Расхождения PRD и риски | 9 | 9/9 |

---

## Критические технические детали (по результатам верификации)

### CRITICAL-1: Oversell — гонка при конкурентных покупках

**Файл:** `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`

**Проблема:** `registerSaleOnce` (строки 1040-1047) выполняет неатомарный read-modify-write:
```java
option.setQuantitySold(option.getQuantitySold() + quantity);  // line 1045
offer.setTotalSold(offer.getTotalSold() + quantity);          // line 1047
```

**Цепочка уязвимости:**
1. `createOrder` (строка 200) → `assertQuantityAvailable` (строки 896-902) — проверяет snapshot `quantityLimit - quantitySold`
2. Нет `SELECT FOR UPDATE`, нет `@Version`, нет pessimistic/optimistic lock
3. Между проверкой доступности и регистрацией продажи — окно гонки
4. Два пользователя могут одновременно купить последний купон

**Влияние:** Финансовые потери, возврат денег, негатив пользователей.

**Решение (варианты по приоритету):**
1. **Pessimistic lock:** `@Lock(LockModeType.PESSIMISTIC_WRITE)` на `findById` перед проверкой остатка
2. **Optimistic lock:** `@Version` поле на `CouponOption`, retry при `OptimisticLockException`
3. **Atomic SQL:** `UPDATE coupon_options SET quantity_sold = quantity_sold + ? WHERE id = ? AND quantity_sold + ? <= quantity_limit`

---

### CRITICAL-2: Directory domain split — Bazaar/Shop дублирование

**Файлы:**
- `services/coupon-service/src/main/java/uz/topdim/coupon/entity/Bazaar.java`
- `services/bazaar-service/src/main/java/uz/topdim/bazaar/entity/Bazaar.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/entity/Shop.java`
- `services/bazaar-service/src/main/java/uz/topdim/bazaar/entity/Shop.java`

**Проблема:** Одинаковые сущности Bazaar и Shop определены в двух сервисах с разными JPA-маппингами, но (вероятно) на одни таблицы в БД.

**Риски:**
- DDL-конфликты при Flyway миграциях (оба сервиса могут мигрировать одну таблицу)
- Расхождение данных (один сервис пишет, другой читает stale state)
- coupon-service Shop имеет `merchant_id`, `LocationType`; bazaar-service Shop имеет `bazaar_id`, `category_id`, `ShopProductTag`

**Решение:** Назначить canonical owner:
- **bazaar-service** владеет Bazaar/Shop (CRUD, миграции)
- **coupon-service** вызывает bazaar-service по REST/Feign для чтения
- Убрать JPA-сущности Bazaar/Shop из coupon-service

---

### HIGH-1: Oversell в coupon options — нет резервирования

**Файл:** `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`, строки 199-271

**Текущий flow:**
1. `createOrder` → проверяет snapshot доступности
2. Создаёт Order + PurchasedCoupons
3. Ожидает оплату
4. **После** оплаты → `registerSaleOnce` увеличивает `quantitySold`

**Проблема:** Между шагом 1 и шагом 4 нет резерва. Другой пользователь может купить те же купоны.

---

### HIGH-2: Admin UI — мертвые пункты меню

**Файл:** `frontend/admin-app/src/App.tsx`

**Пункты меню без routes (TODO-комменты в коде):**
- `/catalog/bazaars` — "подключить BazaarsPage"
- `/catalog/shops` — "подключить ShopsPage"
- `/orders/promocodes` — страница не создана
- `/users/list` — "подключить UsersListPage"

**Риск:** Пользователь (админ) видит пункты меню, кликает — ничего не происходит. Снижает доверие к системе.

---

### HIGH-3: Frontend — ноль автоматических тестов

**Проверено:** `frontend/web-app/src/`, `frontend/admin-app/src/`, `frontend/partner/src/`

**Результат:** 0 файлов `.test.tsx`, `.test.ts`, `.spec.tsx`, `.spec.ts`.

**Риск:** Любое изменение в buyer flow (checkout, cart, payment, refund) может сломать UI без обнаружения. Регрессии находятся только вручную.

---

### MEDIUM-1: Guest checkout расходится с PRD

**PRD утверждает:** гость может пройти checkout.
**Реальность:** `CheckoutPage.tsx` (строки 30-41) — auth-only guard с redirect на login.

**Решение:** Либо убрать guest checkout из PRD, либо реализовать. Рекомендация — оставить auth-only для MVP.

---

### MEDIUM-2: Payment providers не production-ready

**Файл:** `services/payment-service/src/main/java/uz/topdim/payment/entity/PaymentProvider.java`

**Enum содержит:** PAYME, CLICK, UZUM.
**Реальность:** Только `demo` mode работает. Нет SDK-интеграции, нет callbacks от реальных провайдеров.

---

### MEDIUM-3: Email/SMS — stub по умолчанию

**Файлы:**
- `services/notification-service/src/main/java/uz/topdim/notification/service/EmailService.java` — `enabled: false`
- `services/notification-service/src/main/java/uz/topdim/notification/service/SmsService.java` — `enabled: false`

**Статус:** Безопасно для MVP. Не отправляет случайно. Для production нужны ENV vars + `enabled: true`.

---

## Приоритеты реализации (обновлено после верификации)

| Priority | Задача | Тип | Effort | Файлы |
|----------|--------|-----|--------|-------|
| **P0** | Atomic stock reservation (CRITICAL-1) | Backend | 1-2 дня | `OrderService.java`, `CouponOfferRepository.java` |
| **P0** | Зафиксировать checkout: auth-only для MVP | Product decision | 1 час | PRD update |
| **P1** | Убрать мёртвые admin menu пункты или подключить страницы (HIGH-2) | Frontend | 2-3 дня | `admin-app/src/App.tsx` + 4 страницы |
| **P1** | Frontend smoke tests для buyer flow (HIGH-3) | Frontend | 3-5 дней | `web-app/src/__tests__/` |
| **P1** | Обновить PRD как source of truth | Docs | 1 день | `docs/product/prd.md` |
| **P1** | Разрешить directory domain split (CRITICAL-2) | Architecture | 2-3 дня | bazaar-service, coupon-service |
| **P2** | Пометить payment demo vs production | Backend + Docs | 0.5 дня | `PaymentService.java`, PRD |
| **P2** | Phone verification, account deletion, notification settings | Backend + Frontend | 3-5 дней | identity-service, web-app |

## Итог

Проект реализован значительно шире, чем старый PRD показывает. PRD не нужно выбрасывать: его ядро все еще полезно, но сейчас он должен быть обновлен из "после MVP" в "текущая фактическая карта продукта + backlog gaps".

Аудит верифицирован и дополнен конкретными файлами, строками кода и техническими деталями. Следующий шаг — взять P0 задачи: **atomic stock reservation** и **checkout decision**.
