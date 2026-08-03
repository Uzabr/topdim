# ADR-003: RabbitMQ для purchase/payment flows

## Status
Accepted.

## Context
Order, payment, coupon и notification должны реагировать на lifecycle без прямой общей транзакции.

## Decision
Использовать topic exchanges и shared event DTO: order.created, payment.completed, coupon.purchased/redeemed, notification.sent.

## Alternatives considered
Полностью synchronous REST; Kafka; shared database triggers.

## Consequences
Слабее coupling и асинхронность, но delivery/retry/DLQ/outbox не формализованы и создают риск расхождения.

## Evidence
service `RabbitMQConfig.java`, listeners, `shared/common-events`.
