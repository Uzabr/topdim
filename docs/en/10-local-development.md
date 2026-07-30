# 10. Local development

## Prerequisites

Java 21, Node.js 22/npm, Docker with Compose, Bash/zsh, `lsof`, and Git.

## Backend

```bash
git clone <repository-url>
cd topdim
cp .env.example .env   # when the template is present in the checkout
docker compose up -d postgres redis rabbitmq minio
./scripts/dev/start-demo.sh
```

The demo launcher starts Eureka/gateway and all seven services with Gradle `--no-daemon`, creates a local throwaway Base64 JWT key, and stores logs/PIDs in `logs/`.

Alternative:

```bash
./start-all.sh start
./start-all.sh status
./start-all.sh stop
```

Manual order is PostgreSQL/Redis/RabbitMQ/MinIO → discovery → gateway → identity/coupon → order/payment → notification/media/bazaar.

## Frontends

```bash
cd frontend/web-app && npm ci && npm run dev
cd frontend/admin-app && npm ci && npm run dev
cd frontend/partner && npm ci && npm run dev
```

Expected ports are 5173, 3001, and 3002; actual Vite configuration takes precedence.

## Telegram bot

```bash
cd telegram-bot
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
./run.sh
```

## Build and tests

```bash
./gradlew test
./gradlew :services:identity-service:test
./gradlew :services:coupon-service:test
cd frontend/web-app && npm run lint && npm run test && npm run build
```

Admin and partner define lint/build but no test script.

Gateway is at `http://localhost:8080`, Eureka at 8761, Rabbit management at 15673, MinIO console at 9001, and service Swagger at `http://localhost:<port>/swagger-ui.html`.

Local demo data is provided by `scripts/dev/seed-demo.sh` and `scripts/dev/sql/*`. Seed credentials are not production accounts.

Evidence: `scripts/dev/start-demo.sh`, `start-all.sh`, `docker-compose.yml`, frontend package files.
