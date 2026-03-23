# Payment Service

> Обработка платежей

## Порт: 8085

## Стек
Spring Boot 3.4, Spring Data JPA, MapStruct, RabbitMQ, PostgreSQL

## База данных
`topdim_payment` — таблица: `payments`

## Бизнес-логика
1. Слушает `OrderCreatedEvent` → создаёт `Payment` (PENDING)
2. Обрабатывает платёж (интеграция с Payme/Click — в разработке)
3. Публикует `PaymentCompletedEvent` → order-service генерирует купоны

## Статусы
```
PENDING → PROCESSING → COMPLETED / FAILED / REFUNDED
```

## Модель
- `Payment` (orderId, userId, amount, currency, provider, status, transactionId, paymentUrl)

## API

| Method | URL | Auth | Описание |
|---|---|---|---|
| GET | `/api/v1/payments/{orderId}/status` | ✅ | Статус платежа |
| POST | `/api/v1/payments/callback` | ❌ | Webhook от платёжной системы |

## Swagger
http://localhost:8085/swagger-ui.html
