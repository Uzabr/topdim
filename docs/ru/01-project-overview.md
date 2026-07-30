# 01. Обзор проекта

## Назначение

sizbiz — платформа купонов и скидок для рынка Узбекистана, дополненная
справочником базаров и магазинов. Она соединяет покупателей,
партнёров/кассиров, модераторов и администраторов. `topdim` — историческое
внутреннее имя реализации.

Основные возможности:

- регистрация, JWT-сессии, профиль, избранное и Telegram Login;
- каталог, подборки, купоны, вопросы, отзывы и промокоды;
- партнёрский onboarding и согласование купона;
- корзина, заказ, демонстрационная оплата, выдача QR/PIN, погашение;
- возвраты, жалобы и in-app/email/SMS уведомления;
- административная модерация и аудит;
- загрузка изображений в MinIO;
- отдельный каталог базаров/магазинов и Telegram concierge bot.

## Пользователи и статус

Роли backend: `GUEST`, `USER`, `PARTNER`, `MODERATOR`, `ADMIN`, `SUPER_ADMIN`. Кассир не является отдельной JWT-ролью: это staff-контекст пользователя с ролью `PARTNER`.

Production-контекст из `docs/PROJECT_STATE.md`: публичны buyer, admin, partner SPA и API gateway; bazaar-service присутствует в коде, но отсутствует в CD-матрице. Платёжный режим production на текущем срезе — demo, реальный провайдер не подтверждён.

## Технологии

| Область | Реализация |
|---|---|
| Backend | Java 21, Spring Boot 3.4.4, Spring Cloud 2024, Gradle |
| Доступ | Spring Cloud Gateway, Eureka, OpenFeign, JWT/JJWT, Spring Security |
| Данные | PostgreSQL 16 local / 17 documented production, JPA/Hibernate, Flyway |
| Async/cache | RabbitMQ 3.13, Redis 7 |
| Медиа | MinIO |
| Frontend | React 19, TypeScript 5.9, Vite 8, Axios, Zustand; React Query в web/admin |
| Наблюдаемость | Actuator, Prometheus, Grafana, Loki |
| Доставка | Docker, GHCR, GitHub Actions, EasyPanel/Docker Swarm, Traefik |
| Bot | Python 3, aiohttp, Telegram Bot API |

## Структура

```text
infrastructure/   gateway, discovery, неиспользуемый в runtime config-server
services/         identity, coupon, order, payment, bazaar, notification, media
shared/           общие API response и события
frontend/         web-app, admin-app, partner, исторический web-app.bak
telegram-bot/     webhook/FSM concierge bot
docker/           образы и monitoring-конфигурация
scripts/          локальный demo и production deploy helper
docs/             текущие, исторические и новые двуязычные документы
```

## Архитектурный стиль и ограничения

Это микросервисный монорепозиторий с database-per-service, синхронным REST и RabbitMQ-событиями. Ключевые ограничения:

- нет распределённых транзакций и transactional outbox;
- Rabbit listeners ловят часть исключений без DLQ/retry-контракта;
- каталог базаров дублируется в coupon-service и bazaar-service;
- Elasticsearch-код условный и не подключён к основному каталогу;
- staging и формальные SLO/retention policy не обнаружены;
- gateway route для `/api/v1/directory/**` отсутствует, хотя buyer SPA его вызывает.

Evidence:
- `settings.gradle`
- `infrastructure/api-gateway/src/main/resources/application.yml`
- `frontend/web-app/src/api/bazaars.ts`
- `.github/workflows/cd.yml`
