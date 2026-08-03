# ADR-003: RabbitMQ event workflows

## Status
Accepted.

## Context
Order, payment, coupon, and notification react to lifecycle changes without a shared transaction.

## Decision
Use topic exchanges and shared DTOs for order.created, payment.completed, coupon.purchased/redeemed, and notification.sent.

## Alternatives considered
Fully synchronous REST; Kafka; shared database triggers.

## Consequences
Lower coupling and asynchronous processing, but delivery/retry/DLQ/outbox are not formalized.

## Evidence
Service `RabbitMQConfig.java`, listeners, `shared/common-events`.
