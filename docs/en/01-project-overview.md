# 01. Project overview

## Purpose

sizbiz is a coupon and discount platform for the Uzbekistan market, with an
additional bazaar and shop directory. It connects buyers, merchant
owners/cashiers, moderators, and administrators. `topdim` is the historical
internal implementation name.

Implemented capabilities include identity and JWT sessions, coupon discovery and moderation, merchant onboarding, cart/order/payment flows, QR/PIN redemption, refunds, complaints, reviews, questions, notifications, media storage, bazaar discovery, and a Telegram concierge bot.

## Users and status

Backend roles are `GUEST`, `USER`, `PARTNER`, `MODERATOR`, `ADMIN`, and `SUPER_ADMIN`. A cashier is not a separate JWT role; it is a staff access context carried by a `PARTNER` user.

According to the current operational state, buyer/admin/partner SPAs and the API gateway are public. `bazaar-service` exists in source but is absent from the CD build/deploy matrix. Production payment behavior is currently demo mode; a live provider integration is not evidenced.

## Technology stack

| Area | Implementation |
|---|---|
| Backend | Java 21, Spring Boot 3.4.4, Spring Cloud 2024, Gradle |
| Edge | Spring Cloud Gateway, Eureka, OpenFeign, JWT/JJWT, Spring Security |
| Data | PostgreSQL 16 local / 17 documented production, JPA/Hibernate, Flyway |
| Async/cache | RabbitMQ 3.13, Redis 7 |
| Media | MinIO |
| Frontend | React 19, TypeScript 5.9, Vite 8, Axios, Zustand; React Query in web/admin |
| Observability | Actuator, Prometheus, Grafana, Loki |
| Delivery | Docker, GHCR, GitHub Actions, EasyPanel/Docker Swarm, Traefik |
| Bot | Python 3, aiohttp, Telegram Bot API |

## Repository

```text
infrastructure/   gateway, discovery, runtime-unused config-server
services/         identity, coupon, order, payment, bazaar, notification, media
shared/           shared response and event types
frontend/         web-app, admin-app, partner, historical web-app.bak
telegram-bot/     webhook/FSM concierge bot
docker/           images and monitoring configuration
scripts/          local demo and production deploy helpers
docs/             current, historical, and bilingual documentation
```

## Architecture and constraints

This is a microservice monorepo with database-per-service, synchronous REST, and RabbitMQ events. Major constraints are:

- no distributed transaction or transactional outbox;
- no documented retry/DLQ contract for Rabbit listeners;
- duplicate bazaar/shop domains in coupon-service and bazaar-service;
- conditional Elasticsearch code is not connected to the catalog flow;
- no evidenced staging environment, formal SLO, or retention policy;
- the gateway does not route `/api/v1/directory/**`, although the buyer SPA calls it.

Evidence:
- `settings.gradle`
- `infrastructure/api-gateway/src/main/resources/application.yml`
- `frontend/web-app/src/api/bazaars.ts`
- `.github/workflows/cd.yml`
