# Bazaar Service

> Базары, магазины, карта

## Порт: 8088

## Стек
Spring Boot 3.4, Spring Data JPA, MapStruct, OpenFeign, PostgreSQL

## База данных
`topdim_bazaar` — таблицы: `bazaars`, `shops`, `shop_categories`, `shop_product_tags`

## API

| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/bazaars` | ❌ | Все базары |
| GET | `/api/v1/bazaars/{id}` | ❌ | Детали базара |
| GET | `/api/v1/bazaars/{id}/shops` | ❌ | Магазины базара |
| GET | `/api/v1/bazaars/categories` | ❌ | Категории магазинов |
| POST | `/api/v1/admin/bazaars` | ✅ ADMIN | Создать базар |
| PUT | `/api/v1/admin/bazaars/{id}` | ✅ ADMIN | Обновить базар |
| POST | `/api/v1/admin/bazaars/{id}/shops` | ✅ ADMIN | Добавить магазин |

## Модели
- `Bazaar` (name, address, lat, lng, description, imageUrl, workingHours) → `shops[]`
- `Shop` (name, floor, section, phone, bazaar, category) → `productTags[]`
- `ShopCategory` (name, slug)
- `ShopProductTag` (tag, tagUz)

## OpenFeign
- `CouponClient` → coupon-service (getCouponOfferById)

## Swagger
http://localhost:8088/swagger-ui.html
