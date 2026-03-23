# TopDim Platform

> Платформа купонов и скидок для базаров Узбекистана

## Архитектура

```
┌─────────────┐     ┌──────────────┐     ┌────────────────────────────┐
│  Frontend   │────▶│  API Gateway │────▶│  Microservices (Eureka)    │
│  React/Vite │     │  :8080       │     │                            │
└─────────────┘     └──────────────┘     │  auth-service     :8081    │
                          │              │  user-service     :8082    │
                    JWT Validation       │  coupon-service   :8083    │
                                         │  order-service    :8084    │
                                         │  payment-service  :8085    │
                                         │  notification-svc :8086    │
                                         │  media-service    :8087    │
                                         │  bazaar-service   :8088    │
                                         └────────────────────────────┘
                                                    │
                               ┌────────────────────┼────────────────────┐
                               │                    │                    │
                          PostgreSQL            RabbitMQ             Redis
                          :5432                 :5672               :6379
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
| Frontend | React 19, Vite, Zustand, React Query, Leaflet |

## Быстрый старт

### Требования
- Java 21+
- Docker & Docker Compose
- Node.js 18+

### 1. Запуск инфраструктуры
```bash
docker compose up -d
```
Это поднимет: PostgreSQL, Redis, RabbitMQ, MinIO.

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
./gradlew :services:auth-service:bootRun
./gradlew :services:coupon-service:bootRun
./gradlew :services:order-service:bootRun
# ... и т.д.
```

### 4. Frontend
```bash
cd frontend/web-app
npm install
npm run dev     # http://localhost:5173
```

## Базы данных

| Сервис | База данных | Порт |
|---|---|---|
| auth-service | `topdim_auth` | 5432 |
| user-service | `topdim_user` | 5432 |
| coupon-service | `topdim_coupon` | 5432 |
| order-service | `topdim_order` | 5432 |
| payment-service | `topdim_payment` | 5432 |
| bazaar-service | `topdim_bazaar` | 5432 |

Все БД создаются автоматически через `docker/init-databases.sql`.

## Swagger UI

После запуска каждый сервис доступен:

| Сервис | Swagger URL |
|---|---|
| auth-service | http://localhost:8081/swagger-ui.html |
| user-service | http://localhost:8082/swagger-ui.html |
| coupon-service | http://localhost:8083/swagger-ui.html |
| order-service | http://localhost:8084/swagger-ui.html |
| payment-service | http://localhost:8085/swagger-ui.html |
| bazaar-service | http://localhost:8088/swagger-ui.html |

## Проектная структура

```
topdim/
├── infrastructure/
│   ├── discovery-server/     # Eureka
│   ├── api-gateway/          # Spring Cloud Gateway + JWT
│   └── config-server/        # Centralized config (не подключён)
├── services/
│   ├── auth-service/         # Аутентификация, JWT
│   ├── user-service/         # Профиль, избранное
│   ├── coupon-service/       # Купоны, категории, партнёры
│   ├── order-service/        # Корзина, заказы, погашение
│   ├── payment-service/      # Платежи
│   ├── notification-service/ # Email/SMS уведомления
│   ├── media-service/        # Загрузка файлов (MinIO)
│   └── bazaar-service/       # Базары, магазины, карта
├── shared/
│   ├── common-dto/           # ApiResponse, общие DTO
│   └── common-events/        # RabbitMQ events
├── frontend/
│   └── web-app/              # React + Vite
├── docker/
│   └── init-databases.sql    # Инициализация БД
├── docs/
│   ├── implementation_plan.md
│   └── TASKS.md
├── docker-compose.yml
├── start-all.sh
└── .gitignore
```
