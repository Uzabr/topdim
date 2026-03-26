# Роли и функционал — TopDim

> Этот документ — **единственный источник правды** по ролям и их правам.
> Все изменения в функционале должны сначала отражаться здесь.

---

## Иерархия ролей

```
GUEST → USER → PARTNER → MODERATOR → ADMIN → SUPER_ADMIN
```

Каждая следующая роль **наследует** все права предыдущей.

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
| Верификация email | `POST /api/v1/auth/verify-email` | ❌ |
| Верификация телефона | `POST /api/v1/auth/verify-phone` | ❌ |

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
| QR-код купона | — (генерируется на фронте) | ❌ |
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
| Запрос на возврат | `POST /api/v1/orders/{orderId}/refund` | ✅ |
| Мои возвраты | `GET /api/v1/orders/refunds` | ✅ |

### Уведомления
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Мои уведомления | `GET /api/v1/notifications` | ❌ |
| Прочитать уведомление | `PATCH /api/v1/notifications/{id}/read` | ❌ |
| Настройки уведомлений | `PUT /api/v1/users/me/notification-settings` | ❌ |

### Отзывы
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Оставить отзыв | `POST /api/v1/reviews` | ❌ |
| Мои отзывы | `GET /api/v1/reviews/me` | ❌ |

---

## 3. PARTNER (продавец / мерчант)

> Создаёт купоны, погашает их, видит свою статистику.

### Погашение купонов
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Погасить купон (QR/код) | `POST /api/v1/orders/redeem` | ✅ |

### Мои предложения → ❌ Нужно создать
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Мои купоны-предложения | `GET /api/v1/partner/coupons` | ❌ |
| Создать предложение (на модерацию) | `POST /api/v1/partner/coupons` | ❌ |
| Редактировать предложение | `PUT /api/v1/partner/coupons/{id}` | ❌ |
| Мой магазин | `GET /api/v1/partner/shop` | ❌ |

### Статистика → ❌ Нужно создать
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Статистика продаж | `GET /api/v1/partner/stats/sales` | ❌ |
| Статистика погашений | `GET /api/v1/partner/stats/redemptions` | ❌ |
| Выручка за период | `GET /api/v1/partner/stats/revenue` | ❌ |
| История погашений | `GET /api/v1/partner/redemptions` | ❌ |

### Команда → ❌ Нужно создать
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Мои сотрудники | `GET /api/v1/partner/staff` | ❌ |
| Добавить сотрудника | `POST /api/v1/partner/staff` | ❌ |
| Удалить сотрудника | `DELETE /api/v1/partner/staff/{id}` | ❌ |

---

## 4. MODERATOR → ❌ Новая роль

> Модерирует контент: купоны, отзывы, жалобы.

| Функция | Endpoint | Статус |
|---------|----------|--------|
| Купоны на модерации | `GET /api/v1/mod/coupons?status=PENDING` | ❌ |
| Одобрить/отклонить купон | `PATCH /api/v1/mod/coupons/{id}/review` | ❌ |
| Список жалоб | `GET /api/v1/mod/complaints` | ❌ |
| Решение по жалобе | `PATCH /api/v1/mod/complaints/{id}` | ❌ |
| Блокировка отзыва | `PATCH /api/v1/mod/reviews/{id}/block` | ❌ |
| Просмотр пользователей | `GET /api/v1/mod/users` | ❌ |

---

## 5. ADMIN (администратор)

> Полное управление контентом и бизнес-процессами.

### Купоны
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Все купоны | `GET /api/v1/admin/coupons` | ✅ |
| Создать купон | `POST /api/v1/admin/coupons` | ✅ |
| Изменить статус купона | `PATCH /api/v1/admin/coupons/{id}/status` | ✅ |
| Удалить купон | `DELETE /api/v1/admin/coupons/{id}` | ✅ |

### Мерчанты
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Список мерчантов | `GET /api/v1/admin/merchants` | ✅ |
| Детали мерчанта | `GET /api/v1/admin/merchants/{id}` | ✅ |
| Создать мерчанта | `POST /api/v1/admin/merchants` | ✅ |
| Редактировать мерчанта | `PUT /api/v1/admin/merchants/{id}` | ✅ |

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
| Решение по возврату | `PATCH /api/v1/admin/refunds/{id}` | ✅ |

### Управление → ❌ Нужно создать
| Функция | Endpoint | Статус |
|---------|----------|--------|
| Все пользователи | `GET /api/v1/admin/users` | ❌ |
| Заблокировать пользователя | `PATCH /api/v1/admin/users/{id}/block` | ❌ |
| Управление категориями | `POST/PUT/DELETE /api/v1/admin/categories` | ❌ |
| Промокоды | `POST /api/v1/admin/promo-codes` | ❌ |
| Dashboard (аналитика) | `GET /api/v1/admin/dashboard` | ❌ |

---

## 6. SUPER_ADMIN → ❌ Новая роль

> Управление системой, другими админами, финансы.

| Функция | Endpoint | Статус |
|---------|----------|--------|
| Управление админами | `POST/DELETE /api/v1/super/admins` | ❌ |
| Назначение ролей | `PATCH /api/v1/super/users/{id}/role` | ❌ |
| Системные настройки | `GET/PUT /api/v1/super/settings` | ❌ |
| Финансовая отчётность | `GET /api/v1/super/finance` | ❌ |
| Аудит логи | `GET /api/v1/super/audit-logs` | ❌ |

---

## Сводка: Текущее состояние

| Роль | ✅ Готово | ❌ Нет | % готовности |
|------|----------|--------|-------------|
| **GUEST** | 10 | 0 | **100%** |
| **USER** | 21 | 8 | **72%** |
| **PARTNER** | 1 | 12 | **8%** |
| **MODERATOR** | 0 | 6 | **0%** |
| **ADMIN** | 13 | 5 | **72%** |
| **SUPER_ADMIN** | 0 | 5 | **0%** |

---

## ⚠️ Критическая проблема: нет защиты по ролям

Сейчас **ни один endpoint не защищён `@PreAuthorize`**.
Любой авторизованный USER может вызвать **admin** endpoints.

### Нужно добавить:

```java
// Пример: AdminCouponController
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCouponController { ... }

// Пример: partner endpoints
@PreAuthorize("hasRole('PARTNER')")
@RestController
@RequestMapping("/api/v1/partner")
public class PartnerController { ... }
```

---

## Приоритеты для MVP

| Приоритет | Что делать |
|-----------|-----------|
| 🔴 **P0** | Защитить admin endpoints `@PreAuthorize` |
| 🔴 **P0** | Добавить `SUPER_ADMIN`, `MODERATOR` в Role enum |
| 🟡 **P1** | PARTNER: погашения + статистика |
| 🟡 **P1** | USER: верификация email/телефон |
| 🟢 **P2** | MODERATOR: модерация купонов и жалоб |
| 🟢 **P2** | USER: уведомления, отзывы |
| ⚪ **P3** | SUPER_ADMIN: аудит, финансы, backup |
