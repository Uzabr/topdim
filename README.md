# TopDim Platform

> Платформа купонов и скидок для базаров Узбекистана

## Архитектура

```
┌─────────────┐     ┌──────────────┐     ┌────────────────────────────────┐
│  Frontend   │────▶│  API Gateway │────▶│  Microservices (Eureka)        │
│  React/Vite │     │  :8080       │     │                                │
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
- Node.js 18+

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
│   ├── order-service/        # Корзина, заказы, погашение купонов, возвраты
│   ├── payment-service/      # Платежи (Payme, Click)
│   ├── notification-service/ # Email/SMS уведомления
│   ├── media-service/        # Загрузка файлов (MinIO)
│   └── bazaar-service/       # Базары, магазины, геолокация
├── shared/
│   ├── common-dto/           # ApiResponse<T>, общие DTO
│   └── common-events/        # RabbitMQ events
├── frontend/
│   └── web-app/              # React + Vite SPA
├── docker/
│   ├── init-databases.sql    # Инициализация БД
│   ├── postgresql.conf        # Оптимизация PostgreSQL
│   ├── prometheus.yml         # Конфиг Prometheus
│   └── loki.yml              # Конфиг Loki
├── docs/
│   ├── BACKEND.md            # Документация бекенд-сервисов
│   ├── FRONTEND.md           # Документация фронтенда
│   ├── DATABASE.md           # Схема БД и ERD
│   ├── ROLES.md              # Роли и права доступа
│   ├── API_CONTRACT.md       # API контракты
│   ├── COUPON_FLOW.md        # Жизненный цикл купона
│   ├── COUPON_CREATION_FLOW.md # Процесс создания купона
│   ├── TESTING.md            # Тест-планы
│   ├── PROGRESS.md           # Прогресс разработки
│   ├── TASKS.md              # Задачи
│   └── implementation_plan.md
├── docker-compose.yml
├── start-all.sh
└── .gitignore
```
