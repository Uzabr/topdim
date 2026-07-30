# ADR-001: Микросервисы и database-per-service

## Status
Accepted.

## Context
Identity, catalog, orders, payments, notifications и bazaar имеют разные модели и эксплуатационные границы.

## Decision
Использовать Spring Boot services, Eureka и отдельную PostgreSQL database на stateful service; общие типы ограничить `shared`.

## Alternatives considered
Модульный монолит; общая database/schema.

## Consequences
Плюсы: изоляция владения и независимая доставка. Минусы: eventual consistency, logical cross-service IDs, operational complexity.

## Evidence
`settings.gradle`, service datasource URLs, `docker/init-databases.sql`.
