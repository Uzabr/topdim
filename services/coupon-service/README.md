# Coupon Service

> Каталог купонов, категории, партнёры

## Порт: 8083

## Стек
Spring Boot 3.4, Spring Data JPA, Redis Cache, MapStruct, RabbitMQ, PostgreSQL

## База данных
`topdim_coupon` — таблицы: `coupon_offers`, `coupon_options`, `coupon_images`, `categories`, `merchants`

## API

| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/categories` | ❌ | Активные категории |
| GET | `/api/v1/coupons` | ❌ | Каталог (page, size, categoryId, search, sortBy) |
| GET | `/api/v1/coupons/{id}` | ❌ | Детали купона |
| GET | `/api/v1/coupons/top-selling` | ❌ | Топ продаж |
| GET | `/api/v1/merchants` | ❌ | Список партнёров |
| POST | `/api/v1/admin/coupons` | ✅ ADMIN | Создать купон |
| PATCH | `/api/v1/admin/coupons/{id}/status` | ✅ ADMIN | Изменить статус |
| DELETE | `/api/v1/admin/coupons/{id}` | ✅ ADMIN | Удалить |
| POST | `/api/v1/admin/merchants` | ✅ ADMIN | Создать партнёра |
| GET | `/api/v1/admin/categories` | ✅ ADMIN | Все категории, включая выключенные |
| GET | `/api/v1/admin/categories/{id}` | ✅ ADMIN | Категория по ID |
| POST | `/api/v1/admin/categories` | ✅ ADMIN | Создать категорию |
| PUT | `/api/v1/admin/categories/{id}` | ✅ ADMIN | Обновить/деактивировать категорию |
| DELETE | `/api/v1/admin/categories/{id}` | ✅ ADMIN | Удалить, если категория не используется |
| POST | `/api/v1/admin/categories/upload` | ✅ ADMIN | Импортировать Excel |

## Кэширование (Redis)

| Кэш | Метод | TTL |
|---|---|---|
| `categories` | `getAllCategories()` | 1 час |
| `catalog` | `getCatalog()` | 3 мин |
| `topSelling` | `getTopSelling()` | 15 мин |

При создании/обновлении/удалении — `@CacheEvict` сбрасывает кэш.

## Модели
- `CouponOffer` — основной купон (title, prices, discount, status: DRAFT/ACTIVE/PAUSED/ENDED)
- `CouponOption` — вариант купона (size/type с ценой)
- `Category` — категория (name, slug, icon, sortOrder)
- `Merchant` — партнёр (name, logo, address, phone)

## Swagger
http://localhost:8083/swagger-ui.html
