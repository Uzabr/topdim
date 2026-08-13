# Роли и функционал — sizbiz

> Этот документ — продуктовый справочник по ролям и ожидаемому поведению.
> Для точных endpoint-контрактов всегда дополнительно сверяй Swagger и `docs/backend/api-contract.md`.

---

## Иерархия ролей

```
GUEST → USER → PARTNER → MODERATOR → ADMIN → SUPER_ADMIN
```

JWT роли перечислены в `Role`: `GUEST`, `USER`, `PARTNER`, `MODERATOR`, `ADMIN`, `SUPER_ADMIN`.

Важно: наследование прав не универсальное для всех сервисов. Gateway прокидывает `X-User-Role`, а дальше каждый сервис применяет свои правила. Например, некоторые coupon admin endpoints допускают `ADMIN/SUPER_ADMIN`, а order-service partner endpoints рассчитаны на реальный `PARTNER` context.

Отдельной JWT-роли `PARTNER_CASHIER` сейчас нет. Кассир — это запись `staff.role=CASHIER`; его login-user имеет роль `PARTNER`, а ограничения применяются через `PartnerAccessContext`.

---

## 1. GUEST (неавторизованный)

> Может только смотреть и регистрироваться.

### Аутентификация
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Регистрация | `POST /api/v1/auth/register` | ✅ |
| Логин | `POST /api/v1/auth/login` | ✅ |
| Обновить токен | `POST /api/v1/auth/refresh` | ✅ |

### Каталог купонов (только чтение)
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Список купонов (фильтр, поиск) | `GET /api/v1/coupons` | ✅ |
| Детали купона | `GET /api/v1/coupons/{id}` | ✅ |
| Топ продаваемых | `GET /api/v1/coupons/top-selling` | ✅ |
| Категории | `GET /api/v1/categories` | ✅ |

### Базары и магазины (только чтение)
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Список базаров | `GET /api/v1/bazaars` | ✅ |
| Детали базара | `GET /api/v1/bazaars/{id}` | ✅ |
| Карта базара | `GET /api/v1/bazaars/{id}/map` | ✅ |
| Магазины базара | `GET /api/v1/bazaars/{id}/shops` | ✅ |
| Детали магазина | `GET /api/v1/shops/{id}` | ✅ |
| Поиск магазинов | `GET /api/v1/shops/search` | ✅ |
| Категории магазинов | `GET /api/v1/shops/categories` | ✅ |

---

## 2. USER (обычный пользователь)

> Покупает купоны, управляет профилем и избранным.

### Аккаунт
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Мой профиль | `GET /api/v1/users/me` | ✅ |
| Редактировать профиль | `PUT /api/v1/users/me` | ✅ |
| Сменить пароль | `PUT /api/v1/auth/change-password` | ✅ |
| Выйти из системы | `POST /api/v1/auth/logout` | ✅ |
| Загрузить аватар | `POST /api/v1/media/upload` | ✅ |
| Удалить аккаунт | `DELETE /api/v1/users/me` | ❌ |
| Запрос кода верификации email | `POST /api/v1/auth/confirm/request` | ✅ |
| Подтверждение email | `POST /api/v1/auth/confirm/email` | ✅ |
| Верификация телефона | — | ❌ |

### Избранное
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Список избранного | `GET /api/v1/users/me/favorites` | ✅ |
| Добавить в избранное | `POST /api/v1/users/me/favorites` | ✅ |
| Убрать из избранного | `DELETE /api/v1/users/me/favorites/{id}` | ✅ |

### Корзина
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Просмотр корзины | `GET /api/v1/cart` | ✅ |
| Добавить в корзину | `POST /api/v1/cart/items` | ✅ |
| Изменить количество | `PATCH /api/v1/cart/items/{id}` | ✅ |
| Удалить из корзины | `DELETE /api/v1/cart/items/{id}` | ✅ |
| Очистить корзину | `DELETE /api/v1/cart` | ✅ |

### Заказы
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Оформить заказ (checkout) | `POST /api/v1/orders` | ✅ |
| Мои заказы | `GET /api/v1/orders` | ✅ |
| Детали заказа | `GET /api/v1/orders/{id}` | ✅ |

### Мои купоны
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Все мои купоны | `GET /api/v1/orders/my-coupons` | ✅ |
| Купоны конкретного заказа | `GET /api/v1/orders/{orderId}/coupons` | ✅ |
| QR-код купона | — (генерируется на фронте из `TOPDIM-QR:${qrToken}`) | ✅ |
| Подарить купон другу | — | ❌ |

### Платежи
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Создать платёж | `POST /api/v1/payments/create` | ✅ |
| Статус платежа | `GET /api/v1/payments/{id}/status` | ✅ |
| Платёж по заказу | `GET /api/v1/payments/order/{orderId}` | ✅ |

### Возвраты
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Запрос на возврат купленного купона | `POST /api/v1/refunds` | ✅ |
| Мои возвраты | `GET /api/v1/refunds/my` | ✅ |
| Legacy order-level возврат | `POST /api/v1/orders/{orderId}/refund` | ✅ |

### Уведомления
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Мои уведомления | `GET /api/v1/notifications` | ✅ |
| Прочитать уведомление | `PATCH /api/v1/notifications/{id}/read` | ✅ |
| Настройки уведомлений | `PUT /api/v1/users/me/notification-settings` | ❌ |

### Отзывы
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Оставить отзыв | `POST /api/v1/reviews` | ✅ |
| Одобренные отзывы купона | `GET /api/v1/reviews/coupon/{couponId}` | ✅ |
| Можно ли оставить отзыв | `GET /api/v1/reviews/coupon/{couponId}/eligibility` | ✅ |
| Мои отзывы | `GET /api/v1/reviews/my` | ✅ |

---

## 3. PARTNER (продавец / мерчант)

> Создаёт предложения, принимает или возвращает на доработку подготовленные купоны и видит свою статистику.

Внутри merchant context используются роли сотрудников `OWNER`, `MANAGER` и `CASHIER`.
Это не отдельные JWT-роли: все три входят как `PARTNER`, а фактические права
coupon-service получает из identity-service. Владелец и менеджер могут управлять
профилем компании через модерацию; кассир не видит этот раздел и не может вызвать
его API.

### Погашение купонов
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Погасить купон по PIN/code | `POST /api/v1/partner/redemptions` | ✅ |
| Погасить купон по QR token | `POST /api/v1/partner/redemptions/qr` | ✅ |
| Legacy погашение | `POST /api/v1/orders/redeem` | ✅ |

### Мои предложения
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Мои предложения | `GET /api/v1/partner/coupons` | ✅ |
| Создать предложение (на модерацию) | `POST /api/v1/partner/coupons` | ✅ |
| Редактировать предложение | `PUT /api/v1/partner/coupons/{id}` | ✅ |
| Одобрить подготовленное предложение | `POST /api/v1/partner/coupons/{id}/approve` | ✅ |
| Запросить правки | `POST /api/v1/partner/coupons/{id}/request-revision` | ✅ |
| Мой merchant context | `GET /api/v1/partner/merchant/me` | ✅ |

### Статистика
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Статистика (продажи, погашения, выручка) | `GET /api/v1/partner/stats` | ✅ |
| История погашений | `GET /api/v1/partner/redemptions` | ✅ |
| Dashboard партнёра | `GET /api/v1/partner/dashboard` | ✅ |

### Команда
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Мой access context | `GET /api/v1/partner/staff/me` | ✅ |
| Мои сотрудники | `GET /api/v1/partner/staff` | ✅ |
| Добавить сотрудника | `POST /api/v1/partner/staff` | ✅ |
| Удалить сотрудника | `DELETE /api/v1/partner/staff/{id}` | ✅ |

### Профиль компании
| Функция | Endpoint | Доступ | Статус |
|---------|----------|--------|--------|
| Опубликованный профиль и филиалы | `GET /api/v1/partner/merchant` | OWNER, MANAGER | ✅ |
| Список заявок на изменение | `GET /api/v1/partner/merchant/change-requests` | OWNER, MANAGER | ✅ |
| Создать черновик из опубликованной версии | `POST /api/v1/partner/merchant/change-requests` | OWNER, MANAGER | ✅ |
| Просмотреть / обновить заявку | `GET/PUT /api/v1/partner/merchant/change-requests/{id}` | OWNER, MANAGER своей компании | ✅ |
| Удалить черновик | `DELETE /api/v1/partner/merchant/change-requests/{id}` | OWNER, MANAGER своей компании | ✅ |
| Отправить на модерацию | `POST /api/v1/partner/merchant/change-requests/{id}/submit` | OWNER, MANAGER своей компании | ✅ |
| Отозвать заявку с причиной | `POST /api/v1/partner/merchant/change-requests/{id}/withdraw` | OWNER, MANAGER своей компании | ✅ |
| Скопировать терминальную заявку на актуальную версию | `POST /api/v1/partner/merchant/change-requests/{id}/copy` | OWNER, MANAGER своей компании | ✅ |

До одобрения опубликованные данные не меняются. Разрешено не более десяти активных
заявок компании суммарно в статусах `DRAFT`, `PENDING_REVIEW`, `IN_REVIEW` и
`REVISION_REQUESTED`. Логотип и обложка необязательны. В снимке должен быть ровно
один активный основной филиал; активному филиалу обязательны адрес и телефон.
Филиал с активным кассиром нельзя отключить — проверка identity-service работает
fail-closed.

---

## 4. MODERATOR (модератор)

> Работает с купонами в едином рабочем месте, модерирует отзывы, жалобы и заявки партнёров.

| Функция | Endpoint | Статус |
|---------|----------|--------|
| Рабочее место купонов `/coupons` | `GET /api/v1/admin/coupons` | ✅ |
| Взять лид в работу | `PATCH /api/v1/admin/coupons/{id}/take-to-work` | ✅ |
| Создать купон (без партнерки) | `POST /api/v1/admin/coupons` | ✅ |
| Редактировать свой черновик / купон с правками | `PUT /api/v1/admin/coupons/{id}` | ✅ |
| Отправить свой черновик / купон с правками на согласование | `POST /api/v1/admin/coupons/{id}/send-to-approval` | ✅ |
| Решение вместо партнёра для ожидающего купона | — (только просмотр) | ✅ |
| Список жалоб | `GET /api/v1/mod/complaints` | ✅ |
| Решение по жалобе | `PATCH /api/v1/mod/complaints/{id}/resolve` | ✅ |
| Решение по отзыву | `PATCH /api/v1/mod/reviews/{id}/review` | ✅ |
| Заявки на партнерство | `GET /api/v1/admin/partner-applications` | ✅ |
| Очередь изменений компаний | `GET /api/v1/admin/merchant-change-requests` | ✅ |
| Взять изменение компании в работу | `POST /api/v1/admin/merchant-change-requests/{id}/take-to-work` | ✅ |
| Одобрить / вернуть на доработку / отклонить | `POST .../{id}/approve`, `.../request-revision`, `.../reject` | ✅; только назначенный исполнитель, не автор |

Рабочее место `/coupons` содержит шесть вкладок: «Новые», «В работе», «Требуют изменений», «Ожидают партнёра», «Опубликованные» и «Архив». Таблица использует серверную пагинацию с размерами 20, 50 или 100; Kanban загружает по 20 записей на страницу в каждой рабочей колонке. Модератор не может принимать служебное решение за партнёра: у купона в `WAITING_FOR_MERCHANT` ему доступен только просмотр.

Очередь изменений компаний находится отдельно на `/merchants/profile-changes`.
Модератор может взять ожидающую заявку, сравнить снимок с опубликованным профилем
и принять решение только по назначенной ему заявке. Самомодерация запрещена.
Освобождение и переназначение чужой заявки доступны только `ADMIN/SUPER_ADMIN`.

---

## 5. ADMIN (администратор)

> Полное управление контентом и бизнес-процессами.

### Купоны
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Рабочее место купонов `/coupons` | `GET /api/v1/admin/coupons` | ✅ |
| Создать купон | `POST /api/v1/admin/coupons` | ✅ |
| Редактировать купон | `PUT /api/v1/admin/coupons/{id}` | ✅ |
| Взять лид в работу | `PATCH /api/v1/admin/coupons/{id}/take-to-work` | ✅ |
| Отправить подготовленный купон партнёру | `POST /api/v1/admin/coupons/{id}/send-to-approval` | ✅ |
| Служебное решение за партнёра | `PATCH /api/v1/mod/coupons/{id}/review` | ✅ |
| Приостановить, восстановить или архивировать купон | `PATCH /api/v1/admin/coupons/{id}/status`, `POST /api/v1/admin/coupons/{id}/archive` | ✅ |

ADMIN и SUPER_ADMIN имеют одинаковые права в рабочем месте. Служебное решение доступно только для купона, ожидающего партнёра, и требует непустую бизнес-причину; обычный путь остаётся решением партнёра в partner app или Telegram-боте.

### Мерчанты
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Список мерчантов | `GET /api/v1/admin/merchants` | ✅ |
| Пагинированный список мерчантов | `GET /api/v1/admin/merchants/page` | ✅ |
| Детали мерчанта | `GET /api/v1/admin/merchants/{id}` | ✅ |
| Создать мерчанта | `POST /api/v1/admin/merchants` | ✅ |
| Редактировать мерчанта | `PUT /api/v1/admin/merchants/{id}` | ✅ |
| Активировать/деактивировать мерчанта | `PATCH /api/v1/admin/merchants/{id}/active` | ✅ |
| Купоны мерчанта | `GET /api/v1/admin/merchants/{id}/coupons` | ✅ |
| Очередь изменений компаний | `GET /api/v1/admin/merchant-change-requests` | ✅ |
| Освободить заявку из работы | `POST /api/v1/admin/merchant-change-requests/{id}/release` | ✅ ADMIN/SUPER_ADMIN |
| Переназначить заявку | `POST /api/v1/admin/merchant-change-requests/{id}/reassign` | ✅ ADMIN/SUPER_ADMIN |

### Базары и магазины
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Создать базар | `POST /api/v1/admin/bazaars` | ✅ |
| Редактировать базар | `PUT /api/v1/admin/bazaars/{id}` | ✅ |
| Создать магазин | `POST /api/v1/admin/shops` | ✅ |
| Редактировать магазин | `PUT /api/v1/admin/shops/{id}` | ✅ |
| Загрузить карту базара | `POST /api/v1/admin/bazaars/{id}/maps` | ✅ |

### Возвраты
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Список возвратов | `GET /api/v1/admin/refunds` | ✅ |
| Принять возврат в обработку | `PATCH /api/v1/admin/refunds/{id}/approve` | ✅ |
| Отклонить возврат | `PATCH /api/v1/admin/refunds/{id}/reject` | ✅ |
| Завершить возврат | `PATCH /api/v1/admin/refunds/{id}/complete` | ✅ |
| Legacy решение по возврату | `PATCH /api/v1/admin/refunds/{id}` | Удалено: обходило canonical state machine |

### Категории
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Все категории, включая выключенные | `GET /api/v1/admin/categories` | ✅ |
| Категория по ID | `GET /api/v1/admin/categories/{id}` | ✅ |
| Создать категорию | `POST /api/v1/admin/categories` | ✅ |
| Обновить категорию | `PUT /api/v1/admin/categories/{id}` | ✅ |
| Удалить неиспользуемую категорию | `DELETE /api/v1/admin/categories/{id}` | ✅; 409, если есть купоны |
| Импортировать категории | `POST /api/v1/admin/categories/upload` | ✅ |

### Заказы
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Все заказы | `GET /api/v1/admin/orders` | ✅ |
| Детали заказа | `GET /api/v1/admin/orders/{id}` | ✅ |

### Управление пользователями
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Все пользователи | `GET /api/v1/admin/users` | ✅ |
| Детали пользователя | `GET /api/v1/admin/users/{id}` | ✅ |
| Заблокировать пользователя | `PATCH /api/v1/admin/users/{id}/block` | ✅ |

### Дополнительно
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Промокоды | `POST /api/v1/admin/promocodes` | ✅ |
| Dashboard (заказы, выручка, жалобы, 7 дней) | `GET /api/v1/admin/dashboard` | ✅ |

---

## 6. SUPER_ADMIN (супер-администратор)

> Управление системой, другими админами, финансы.

| Функция | Endpoint | Статус |
|---------|----------|--------|
| Управление админами | `POST/DELETE /api/v1/super/admins` | ✅ |
| Назначение ролей | `PATCH /api/v1/super/users/{id}/role` | ✅ |
| Системные настройки | `GET/PUT /api/v1/super/settings` | ❌ |
| Финансовая отчётность | `GET /api/v1/super/finance` | ❌ |
| Аудит логи | `GET /api/v1/super/audit-logs` | ✅ |

---

## Сводка: Текущее состояние

Ручные проценты готовности больше не поддерживаются в этом документе: они быстро устаревают и создают ложную уверенность. Актуальный статус по ролям:

| Роль | Состояние |
|------|-----------|
| **GUEST** | Каталог, категории, public reviews, directory и партнёрская заявка доступны без JWT |
| **USER** | Покупка, профиль, избранное, заказы, purchased coupons, reviews, refunds, complaints, notifications реализованы |
| **PARTNER** | Partner app, предложения, модерируемый профиль компании для OWNER/MANAGER, staff, stats, PIN/QR redemption реализованы |
| **MODERATOR** | Рабочее место купонов, изменения компаний, модерация отзывов и жалоб реализованы; решение за партнёра по купону недоступно |
| **ADMIN** | Merchant/catalog/order/support/user контуры реализованы; UI базаров/магазинов и промокодов остаётся отдельным roadmap-модулем |
| **SUPER_ADMIN** | Staff, roles, blocking и audit реализованы; системные настройки/финансы ещё вне MVP |

---

## ✅ Защита по ролям (Раньше была крит. проблема)

Все эндпоинты теперь защищены с помощью **`@PreAuthorize`**:
- Admin endpoints: `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`
- Partner endpoints: `@PreAuthorize("hasAnyRole('PARTNER', 'ADMIN', 'SUPER_ADMIN')")`
- Настройки дублируются в `SecurityConfig.java`.

---

## Приоритеты для MVP

| Приоритет | Что делать |
|-----------|-----------|
| ✅ **P0** | Защитить admin/partner endpoints `@PreAuthorize` (Выполнено) |
| ✅ **P0** | Добавить `SUPER_ADMIN`, `MODERATOR` в Role enum (Выполнено) |
| ✅ **P1** | PARTNER: погашения + статистика (Выполнено) |
| ✅ **P2** | MODERATOR: модерация купонов, отзывов и жалоб (Выполнено) |
| ✅ **P2** | USER: email confirmation, QR/PIN, отзывы, возвраты, жалобы, уведомления |
| 🟡 **P1** | USER: телефонная верификация и production payment provider |
| ⚪ **P3** | SUPER_ADMIN: аудит, финансы, системные настройки |
