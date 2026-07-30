# ADR-001: Microservices and database-per-service

## Status
Accepted.

## Context
Identity, catalog, orders, payments, notifications, and bazaar have distinct models and operational boundaries.

## Decision
Use Spring Boot services, Eureka discovery, and a separate PostgreSQL database per stateful service; restrict shared code to common contracts.

## Alternatives considered
Modular monolith; shared database/schema.

## Consequences
Clear ownership and independent delivery, at the cost of eventual consistency, logical cross-service IDs, and operational complexity.

## Evidence
`settings.gradle`, service datasource URLs, `docker/init-databases.sql`.
