# Order Service

> Корзина, заказы, купленные купоны, погашение, возврат

## Порт: 8084

## Стек
Spring Boot 3.4, Spring Data JPA, Redis, OpenFeign, MapStruct, RabbitMQ, PostgreSQL

## База данных
`topdim_order` — таблицы: `carts`, `cart_items`, `orders`, `order_items`, `purchased_coupons`, `redemptions`, `refund_requests`

## API

| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/cart` | ✅ | Получить корзину |
| POST | `/api/v1/cart/items` | ✅ | Добавить в корзину |
| DELETE | `/api/v1/cart/items/{id}` | ✅ | Удалить из корзины |
| POST | `/api/v1/orders` | ✅ | Оформить заказ |
| GET | `/api/v1/orders` | ✅ | Мои заказы |
| GET | `/api/v1/orders/{id}` | ✅ | Детали заказа |
| GET | `/api/v1/orders/my-coupons` | ✅ | Мои купоны (?status=ACTIVE) |
| POST | `/api/v1/orders/redeem` | ✅ | Погасить купон |
| GET | `/api/v1/orders/{id}/coupons` | ✅ | Купоны заказа |
| POST | `/api/v1/orders/{id}/refund` | ✅ | Запросить возврат |
| GET | `/api/v1/orders/refunds` | ✅ | Мои запросы на возврат |
| PATCH | `/api/v1/admin/refunds/{id}` | ✅ ADMIN | Одобрить/отклонить |

## Бизнес-логика

### Checkout Flow
```
Cart → POST /orders → Order (PENDING) → publish OrderCreatedEvent
  → payment-service создаёт Payment
  → PaymentCompletedEvent → generatePurchasedCoupons()
  → PurchasedCoupon[] (code + QR) → publish CouponPurchasedEvent
  → notification-service отправляет email/SMS
```

### Погашение купона
```
POST /orders/redeem { couponCode, merchantId, staffName }
  → PurchasedCoupon.status = USED
  → Создаётся запись Redemption
```

### Возврат
```
POST /orders/{id}/refund { reason } → RefundRequest (PENDING)
PATCH /admin/refunds/{id} { status: APPROVED/REJECTED, comment }
```

## OpenFeign клиенты
- `CouponClient` → coupon-service (getCouponById, getCouponOption)
- `UserClient` → user-service (getUserById)

## Swagger
http://localhost:8084/swagger-ui.html
