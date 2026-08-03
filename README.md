# sizbiz Platform

> Платформа купонов и скидок для базаров Узбекистана

> Публичный бренд — **sizbiz** (`sizbiz.uz`). `topdim` сохраняется как
> историческое внутреннее имя в пакетах, базах, инфраструктуре и командах.

## Документация / Documentation

- [Русская документация](docs/ru/README.md)
- [English documentation](docs/en/README.md)
- [Бизнес-документация для sales и сотрудников](docs/ru/business/README.md)
- [Business documentation for sales and employees](docs/en/business/README.md)

Обе версии имеют одинаковую структуру технических разделов, ADR,
C4/PlantUML и отдельного бизнес-комплекта. Существующие предметные, QA,
security и operational документы в `docs/` сохранены как дополнительные
источники.

## Архитектура

```
┌─────────────┐     ┌──────────────┐     ┌────────────────────────────────┐
│ Frontend x3 │────▶│  API Gateway │────▶│  Microservices (Eureka)        │
│ React/Vite  │     │  :8080       │     │                                │
└─────────────┘     └──────────────┘     │  identity-service   :8081      │
                          │              │  coupon-service     :8083      │
                    JWT Validation       │  order-service      :8084      │
                                         │  payment-service    :8085      │
                                         │  bazaar-service     :8086      │
                                         │  notification-svc   :8087      │
                                         │  media-service      :8088      │
                                         └────────────────────────────────┘
                                                    │
                               ┌────────────────────┼────────────────────┐
                               │                    │                    │
                          PostgreSQL            RabbitMQ             Redis
                          :5433                 :5673               :6380
                                                                   MinIO
                                                                   :9000
```

## Технологический стек

| Компонент | Технология |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Spring Cloud 2024.0 |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Message Broker | RabbitMQ 3.13 |
| Object Storage | MinIO |
| Service Discovery | Eureka |
| API Gateway | Spring Cloud Gateway |
| ORM | Spring Data JPA + Flyway |
| Mapping | MapStruct 1.6 |
| API Docs | SpringDoc OpenAPI (Swagger) |
| Frontend | React 19, Vite, Zustand, React Query |
| Map | 2GIS MapGL |

## Быстрый старт

### Требования
- Java 21+
- Docker & Docker Compose
- Node.js 22+ (рекомендуется для текущих Vite 8 приложений)

### 1. Запуск инфраструктуры
```bash
docker compose up -d
```
Это поднимет: PostgreSQL (:5433), Redis (:6380), RabbitMQ (:5673), MinIO (:9000).

### 2. Запуск всех сервисов (скрипт)
```bash
chmod +x start-all.sh
./start-all.sh start
```

### 3. Запуск вручную (по одному)
```bash
# Eureka Discovery
./gradlew :infrastructure:discovery-server:bootRun

# API Gateway
./gradlew :infrastructure:api-gateway:bootRun

# Backend services
./gradlew :services:identity-service:bootRun
./gradlew :services:coupon-service:bootRun
./gradlew :services:order-service:bootRun
# ... и т.д.
```

### 4. Frontend
```bash
cd frontend/web-app
npm install
npm run dev     # http://localhost:5173

cd ../admin-app
npm install
npm run dev     # http://localhost:3001

cd ../partner
npm install
npm run dev     # http://localhost:3002
```

## Базы данных

| Сервис | База данных | Порт |
|---|---|---|
| identity-service | `topdim_identity` | 5433 |
| coupon-service | `topdim_coupon` | 5433 |
| order-service | `topdim_order` | 5433 |
| payment-service | `topdim_payment` | 5433 |
| bazaar-service | `topdim_bazaar` | 5433 |
| notification-service | `topdim_notification` | 5433 |

Все БД создаются автоматически через `docker/init-databases.sql`.

## Swagger UI

После запуска каждый сервис доступен:

| Сервис | Swagger URL |
|---|---|
| identity-service | http://localhost:8081/swagger-ui.html |
| coupon-service | http://localhost:8083/swagger-ui.html |
| order-service | http://localhost:8084/swagger-ui.html |
| payment-service | http://localhost:8085/swagger-ui.html |
| bazaar-service | http://localhost:8086/swagger-ui.html |
| notification-service | http://localhost:8087/swagger-ui.html |
| media-service | http://localhost:8088/swagger-ui.html |

## Мониторинг

| Компонент | URL |
|---|---|
| Eureka Dashboard | http://localhost:8761 |
| RabbitMQ Management | http://localhost:15673 |
| MinIO Console | http://localhost:9001 |

## Проектная структура

```
topdim/
├── infrastructure/
│   ├── discovery-server/     # Eureka
│   ├── api-gateway/          # Spring Cloud Gateway + JWT
│   └── config-server/        # Centralized config
├── services/
│   ├── identity-service/     # Аутентификация, JWT, профиль, партнёрские заявки
│   ├── coupon-service/       # Купоны, категории, партнёры, справочник базаров
│   ├── order-service/        # Корзина, заказы, купленные купоны, погашение, возвраты, жалобы
│   ├── payment-service/      # Платежи, demo/provider mode
│   ├── notification-service/ # In-app уведомления, Email/SMS stub/real mode
│   ├── media-service/        # Загрузка файлов (MinIO)
│   └── bazaar-service/       # Базары, магазины, геолокация
├── shared/
│   ├── common-dto/           # ApiResponse<T>, общие DTO
│   └── common-events/        # RabbitMQ events
├── frontend/
│   ├── web-app/              # Покупательский React + Vite SPA
│   ├── admin-app/            # Админка и модерация
│   ├── partner/              # Партнёрский портал
│   └── web-app.bak/          # Backup, не считать текущим приложением
├── docker/
│   ├── init-databases.sql    # Инициализация БД
│   ├── postgresql.conf        # Оптимизация PostgreSQL
│   ├── prometheus.yml         # Конфиг Prometheus
│   └── loki.yml              # Конфиг Loki
├── docs/
│   ├── README.md             # Навигация по актуальной документации
│   ├── documentation-audit.md # Что было сверено и какие docs ещё рискованные
│   ├── backend/              # services overview, API contract, database
│   ├── frontend/             # web/admin/partner apps
│   ├── product/              # роли, PRD, бизнес-флоу
│   ├── qa/                   # ручные тест-планы и стратегия
│   ├── superpowers/          # рабочие планы для AI/agent
│   └── archive/              # исторические документы, не источник правды
├── docker-compose.yml
├── start-all.sh
└── .gitignore
```

## Production deploy через EasyPanel

Production Docker artifacts:

- `docker/backend/Dockerfile` — generic Spring Boot image, configured with `MODULE_PATH`.
- `docker/frontend/Dockerfile` — generic Vite/Nginx image, configured with `APP_PATH`.
- `telegram-bot/Dockerfile` — Telegram bot image.
- `docker-compose.prod.yml` — production Compose stack.
- `.env.prod.example` — production env template.

Минимальный порядок:

1. Скопировать `.env.prod.example` в EasyPanel Environment и заменить все `CHANGE_ME_*`.
2. Настроить домены:
   - `api.example.com` -> `api-gateway`, port `8080`
   - `app.example.com` -> `web-app`, port `80`
   - `admin.example.com` -> `admin-app`, port `80`
   - `partner.example.com` -> `partner-app`, port `80`
   - `bot.example.com` -> `telegram-bot`, port `3000`
3. Указать frontend build args:
   - `VITE_API_URL=https://api.example.com`
   - `VITE_API_BASE_URL=https://api.example.com`
4. В backend env использовать Docker service names:
   - `DB_HOST=postgres`
   - `REDIS_HOST=redis`
   - `RABBITMQ_HOST=rabbitmq`
   - `MINIO_URL=http://minio:9000`
   - `EUREKA_HOST=discovery-server`
5. Запустить stack и проверить:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```
