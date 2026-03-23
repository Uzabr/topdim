---
description: How to build and run the backend services
---

## Сборка бекенда

// turbo-all

1. Собрать все модули:
```bash
cd /Users/abror/Projects/topdim && ./gradlew compileJava
```

2. Запустить Docker-инфраструктуру (PostgreSQL, Redis, RabbitMQ, MinIO):
```bash
cd /Users/abror/Projects/topdim && docker compose up -d
```

3. Запустить сервисы по порядку:

   a. Discovery Server (порт 8761):
   ```bash
   cd /Users/abror/Projects/topdim && ./gradlew :infrastructure:discovery-server:bootRun
   ```

   b. Config Server (порт 8888):
   ```bash
   cd /Users/abror/Projects/topdim && ./gradlew :infrastructure:config-server:bootRun
   ```

   c. API Gateway (порт 8080):
   ```bash
   cd /Users/abror/Projects/topdim && ./gradlew :infrastructure:api-gateway:bootRun
   ```

   d. Auth Service (порт 8081):
   ```bash
   cd /Users/abror/Projects/topdim && ./gradlew :services:auth-service:bootRun
   ```

   e. Другие сервисы аналогично.

## Порты сервисов

| Сервис | Порт |
|---|---|
| API Gateway | 8080 |
| Auth Service | 8081 |
| User Service | 8082 |
| Coupon Service | 8083 |
| Order Service | 8084 |
| Payment Service | 8085 |
| Bazaar Service | 8086 |
| Notification Service | 8087 |
| Media Service | 8088 |
| Eureka Dashboard | 8761 |
| Config Server | 8888 |
