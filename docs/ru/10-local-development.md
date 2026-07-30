# 10. Локальная разработка

## Требования

- Java 21;
- Node.js 22 и npm;
- Docker Engine/Desktop с Compose;
- Bash/zsh; `lsof` для launcher scripts;
- Git.

## Запуск backend

```bash
git clone <repository-url>
cd topdim
cp .env.example .env   # если template присутствует в checkout
docker compose up -d postgres redis rabbitmq minio
./scripts/dev/start-demo.sh
```

`start-demo.sh` запускает Eureka/gateway и все семь сервисов через Gradle `--no-daemon`, создаёт локальный throwaway Base64 JWT secret и пишет PID/logs в `logs/`.

Альтернатива:

```bash
./start-all.sh start
./start-all.sh status
./start-all.sh stop
```

Ручной порядок: PostgreSQL/Redis/RabbitMQ/MinIO → discovery → gateway → identity/coupon → order/payment → notification/media/bazaar.

## Frontend

В отдельных терминалах:

```bash
cd frontend/web-app && npm ci && npm run dev
cd frontend/admin-app && npm ci && npm run dev
cd frontend/partner && npm ci && npm run dev
```

Ожидаемые local ports: web 5173, admin 3001, partner 3002; Vite config имеет окончательный приоритет.

## Bot

```bash
cd telegram-bot
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
./run.sh
```

Bot требует Telegram/webhook/backend variables из [11-configuration.md](11-configuration.md).

## Build и tests

```bash
./gradlew test
./gradlew :services:identity-service:test
./gradlew :services:coupon-service:test

cd frontend/web-app
npm run lint
npm run test
npm run build
```

Admin/partner имеют lint/build, но не имеют `test` script.

## URLs

| Компонент | URL |
|---|---|
| Gateway | `http://localhost:8080` |
| Eureka | `http://localhost:8761` |
| Swagger | `http://localhost:<service-port>/swagger-ui.html` |
| Rabbit management | `http://localhost:15673` |
| MinIO console | `http://localhost:9001` |
| Prometheus/Grafana | `http://localhost:9090`, `http://localhost:3000` если подняты |

## Demo data

`scripts/dev/seed-demo.sh` и `scripts/dev/sql/*` создают local-only данные. Не считать credentials из seed production accounts и не переносить их в shared environment.

Evidence:
- `scripts/dev/start-demo.sh`
- `start-all.sh`
- `docker-compose.yml`
- frontend `package.json`
