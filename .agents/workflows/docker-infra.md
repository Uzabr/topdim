---
description: How to manage Docker infrastructure (databases, message broker, storage)
---

## Запуск инфраструктуры

// turbo-all

1. Запустить все контейнеры:
```bash
cd /Users/abror/Projects/topdim && docker compose up -d
```

2. Проверить статус:
```bash
cd /Users/abror/Projects/topdim && docker compose ps
```

3. Посмотреть логи:
```bash
cd /Users/abror/Projects/topdim && docker compose logs -f
```

## Компоненты

| Сервис | Порт | UI |
|---|---|---|
| PostgreSQL 16 | 5432 | — |
| Redis 7 | 6379 | — |
| RabbitMQ 3.13 | 5672 | http://localhost:15672 (guest/guest) |
| MinIO | 9000 | http://localhost:9001 (minioadmin/minioadmin) |

## Остановка

```bash
cd /Users/abror/Projects/topdim && docker compose down
```

## Полная очистка (с удалением данных)

```bash
cd /Users/abror/Projects/topdim && docker compose down -v
```
