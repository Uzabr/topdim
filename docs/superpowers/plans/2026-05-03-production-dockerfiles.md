# Production Dockerfiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare production Docker artifacts so the TopDim monorepo can be deployed from Git in EasyPanel.

**Architecture:** Use one reusable Gradle multi-stage Dockerfile for all Spring Boot services, one reusable Vite/Nginx Dockerfile for all frontend apps, and one Python Dockerfile for the Telegram bot. Add a production compose file that wires infrastructure, backend services, frontends, and bot with Docker service names instead of `localhost`.

**Tech Stack:** Java 21, Spring Boot 3.4, Gradle, React/Vite, Nginx, Python 3.12, PostgreSQL 16, Redis 7, RabbitMQ 3.13, MinIO, Docker Compose, EasyPanel.

---

## File Map

- Create `.dockerignore`: keep Docker build contexts small and avoid copying local build/cache files.
- Create `docker/backend/Dockerfile`: generic production image for Gradle/Spring Boot modules.
- Create `docker/frontend/Dockerfile`: generic production image for Vite SPAs served by Nginx.
- Create `docker/frontend/nginx.conf`: SPA-safe Nginx config with `try_files`.
- Create `telegram-bot/Dockerfile`: production image for the aiohttp bot.
- Create `docker-compose.prod.yml`: production stack for EasyPanel/Compose deployment.
- Create `.env.prod.example`: production environment template with service-name hosts.
- Modify `README.md`: add a short deployment section pointing to production files.

## Assumptions

- EasyPanel will build from this Git repository or use `docker-compose.prod.yml` as a Compose service.
- Public traffic should go only to frontends, API gateway, and Telegram bot.
- Internal services should communicate by Docker service names: `postgres`, `redis`, `rabbitmq`, `minio`, `discovery-server`, `api-gateway`.
- Existing Spring services already expose `/actuator/health`; health checks can use that endpoint.
- The `config-server` module exists but is not started by `start-all.sh`; do not include it in production compose unless the application is later changed to depend on it.

---

### Task 1: Add Docker Ignore

**Files:**
- Create: `.dockerignore`

- [ ] **Step 1: Create `.dockerignore`**

```dockerignore
.git
.idea
.vscode
.gradle
build
**/build
**/out
**/target

node_modules
**/node_modules
**/dist

logs
*.log
*.pid

.env
.env.*
!.env.example
!.env.prod.example

.DS_Store
```

- [ ] **Step 2: Verify ignored files**

Run:

```bash
docker buildx build --no-cache -f docker/backend/Dockerfile --build-arg MODULE_PATH=infrastructure:discovery-server --target build .
```

Expected after Task 2 exists: Docker should not copy local `node_modules`, `build`, `.gradle`, or `.env` into the build context.

- [ ] **Step 3: Commit**

```bash
git add .dockerignore
git commit -m "chore: add docker ignore rules"
```

---

### Task 2: Add Generic Backend Dockerfile

**Files:**
- Create: `docker/backend/Dockerfile`

- [ ] **Step 1: Create backend Dockerfile**

```dockerfile
# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY infrastructure ./infrastructure
COPY services ./services

ARG MODULE_PATH
RUN test -n "$MODULE_PATH"
RUN chmod +x ./gradlew \
    && ./gradlew ":${MODULE_PATH}:bootJar" --no-daemon --console=plain \
    && MODULE_DIR="$(echo "$MODULE_PATH" | tr ':' '/')" \
    && JAR_PATH="$(find "$MODULE_DIR/build/libs" -type f -name "*.jar" ! -name "*plain.jar" | head -n 1)" \
    && test -n "$JAR_PATH" \
    && cp "$JAR_PATH" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S topdim \
    && adduser -S topdim -G topdim

WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar

USER topdim

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

- [ ] **Step 2: Build discovery server image**

Run:

```bash
docker build -f docker/backend/Dockerfile \
  --build-arg MODULE_PATH=infrastructure:discovery-server \
  -t topdim/discovery-server:test .
```

Expected: image builds successfully and contains `/app/app.jar`.

- [ ] **Step 3: Build API gateway image**

Run:

```bash
docker build -f docker/backend/Dockerfile \
  --build-arg MODULE_PATH=infrastructure:api-gateway \
  -t topdim/api-gateway:test .
```

Expected: image builds successfully.

- [ ] **Step 4: Build one domain service image**

Run:

```bash
docker build -f docker/backend/Dockerfile \
  --build-arg MODULE_PATH=services:identity-service \
  -t topdim/identity-service:test .
```

Expected: image builds successfully.

- [ ] **Step 5: Commit**

```bash
git add docker/backend/Dockerfile
git commit -m "chore: add generic backend dockerfile"
```

---

### Task 3: Add Generic Frontend Dockerfile And Nginx Config

**Files:**
- Create: `docker/frontend/Dockerfile`
- Create: `docker/frontend/nginx.conf`

- [ ] **Step 1: Create frontend Dockerfile**

```dockerfile
# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS build

WORKDIR /app

ARG APP_PATH
ARG VITE_API_URL
ARG VITE_API_BASE_URL

RUN test -n "$APP_PATH"

COPY ${APP_PATH}/package*.json ./
RUN npm ci

COPY ${APP_PATH}/ ./
RUN VITE_API_URL="${VITE_API_URL}" \
    VITE_API_BASE_URL="${VITE_API_BASE_URL:-$VITE_API_URL}" \
    npm run build

FROM nginx:1.27-alpine

COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
```

- [ ] **Step 2: Create Nginx SPA config**

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml image/svg+xml;

    location /assets/ {
        try_files $uri =404;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    location / {
        try_files $uri /index.html;
    }
}
```

- [ ] **Step 3: Build buyer web app**

Run:

```bash
docker build -f docker/frontend/Dockerfile \
  --build-arg APP_PATH=frontend/web-app \
  --build-arg VITE_API_URL=https://api.example.com \
  --build-arg VITE_API_BASE_URL=https://api.example.com \
  -t topdim/web-app:test .
```

Expected: image builds successfully.

- [ ] **Step 4: Build admin app**

Run:

```bash
docker build -f docker/frontend/Dockerfile \
  --build-arg APP_PATH=frontend/admin-app \
  --build-arg VITE_API_URL=https://api.example.com \
  --build-arg VITE_API_BASE_URL=https://api.example.com \
  -t topdim/admin-app:test .
```

Expected: image builds successfully.

- [ ] **Step 5: Build partner app**

Run:

```bash
docker build -f docker/frontend/Dockerfile \
  --build-arg APP_PATH=frontend/partner \
  --build-arg VITE_API_URL=https://api.example.com \
  --build-arg VITE_API_BASE_URL=https://api.example.com \
  -t topdim/partner-app:test .
```

Expected: image builds successfully.

- [ ] **Step 6: Commit**

```bash
git add docker/frontend/Dockerfile docker/frontend/nginx.conf
git commit -m "chore: add frontend dockerfile"
```

---

### Task 4: Add Telegram Bot Dockerfile

**Files:**
- Create: `telegram-bot/Dockerfile`

- [ ] **Step 1: Create bot Dockerfile**

```dockerfile
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

RUN addgroup --system topdim \
    && adduser --system --ingroup topdim topdim

COPY telegram-bot/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY telegram-bot ./

USER topdim

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:3000/health', timeout=3).read()" || exit 1

CMD ["python", "-m", "src.main"]
```

- [ ] **Step 2: Build bot image**

Run:

```bash
docker build -f telegram-bot/Dockerfile -t topdim/telegram-bot:test .
```

Expected: image builds successfully.

- [ ] **Step 3: Commit**

```bash
git add telegram-bot/Dockerfile
git commit -m "chore: add telegram bot dockerfile"
```

---

### Task 5: Add Production Environment Example

**Files:**
- Create: `.env.prod.example`

- [ ] **Step 1: Create `.env.prod.example`**

```env
# Copy to .env.prod or configure these values in EasyPanel.

DB_USERNAME=topdim
DB_PASSWORD=CHANGE_ME_STRONG_DB_PASSWORD

REDIS_HOST=redis
REDIS_PORT=6379

RABBITMQ_USERNAME=topdim
RABBITMQ_PASSWORD=CHANGE_ME_STRONG_RABBITMQ_PASSWORD

MINIO_ACCESS_KEY=topdim
MINIO_SECRET_KEY=CHANGE_ME_STRONG_MINIO_PASSWORD
MINIO_BUCKET=topdim-media

JWT_SECRET=CHANGE_ME_BASE64_32_BYTES_OR_LONG_RANDOM_SECRET

SPRING_PROFILES_ACTIVE=prod
EUREKA_HOST=discovery-server

CORS_ORIGIN_1=https://app.example.com
CORS_ORIGIN_2=https://admin.example.com
CORS_ORIGIN_3=https://partner.example.com

VITE_API_URL=https://api.example.com
VITE_API_BASE_URL=https://api.example.com

PAYMENT_MODE=demo

MAIL_USERNAME=
MAIL_PASSWORD=

BOT_API_KEY=CHANGE_ME_BOT_API_KEY
BOT_PREVIEW_TOKEN=CHANGE_ME_PREVIEW_TOKEN
BOT_PREVIEW_URL=https://bot.example.com/webhook/preview

TELEGRAM_BOT_TOKEN=CHANGE_ME_TELEGRAM_BOT_TOKEN
TELEGRAM_WEBHOOK_URL=https://bot.example.com/webhooks/telegram
TELEGRAM_WEBHOOK_SECRET=CHANGE_ME_TELEGRAM_WEBHOOK_SECRET
PREVIEW_WEBHOOK_TOKEN=CHANGE_ME_PREVIEW_TOKEN
FRONTEND_WEB_APP_URL=https://app.example.com
```

- [ ] **Step 2: Verify no real secrets were added**

Run:

```bash
rg -n "CHANGE_ME|example.com" .env.prod.example
```

Expected: all sensitive values are placeholders.

- [ ] **Step 3: Commit**

```bash
git add .env.prod.example
git commit -m "chore: add production env example"
```

---

### Task 6: Add Production Compose File

**Files:**
- Create: `docker-compose.prod.yml`

- [ ] **Step 1: Create `docker-compose.prod.yml`**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: topdim_auth
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docker/init-databases.sql:/docker-entrypoint-initdb.d/init-databases.sql:ro
      - ./docker/postgresql.conf:/etc/postgresql/postgresql.conf:ro
    command: postgres -c config_file=/etc/postgresql/postgresql.conf
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME}"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks:
      - backend

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks:
      - backend

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USERNAME}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 30s
      timeout: 10s
      retries: 10
    networks:
      - backend

  minio:
    image: minio/minio:latest
    restart: unless-stopped
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    volumes:
      - minio_data:/data
    command: server /data --console-address ":9001"
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 30s
      timeout: 10s
      retries: 10
    networks:
      - backend

  discovery-server:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        MODULE_PATH: infrastructure:discovery-server
    restart: unless-stopped
    environment:
      SERVER_PORT: 8761
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
    expose:
      - "8761"
    networks:
      - backend

  api-gateway:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        MODULE_PATH: infrastructure:api-gateway
    restart: unless-stopped
    environment:
      SERVER_PORT: 8080
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
      EUREKA_HOST: discovery-server
      JWT_SECRET: ${JWT_SECRET}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      CORS_ORIGIN_1: ${CORS_ORIGIN_1}
      CORS_ORIGIN_2: ${CORS_ORIGIN_2}
      CORS_ORIGIN_3: ${CORS_ORIGIN_3}
    depends_on:
      discovery-server:
        condition: service_started
      redis:
        condition: service_healthy
    ports:
      - "8080:8080"
    networks:
      - backend
      - edge

  identity-service:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        MODULE_PATH: services:identity-service
    restart: unless-stopped
    environment: &backend_env
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
      EUREKA_HOST: discovery-server
      DB_HOST: postgres
      DB_PORT: 5432
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    depends_on: &backend_depends_on
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      discovery-server:
        condition: service_started
    expose:
      - "8081"
    networks:
      - backend

  coupon-service:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        MODULE_PATH: services:coupon-service
    restart: unless-stopped
    environment:
      <<: *backend_env
      BOT_API_KEY: ${BOT_API_KEY}
      BOT_PREVIEW_URL: ${BOT_PREVIEW_URL}
      BOT_PREVIEW_TOKEN: ${BOT_PREVIEW_TOKEN}
    depends_on: *backend_depends_on
    expose:
      - "8083"
    networks:
      - backend

  order-service:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        MODULE_PATH: services:order-service
    restart: unless-stopped
    environment: *backend_env
    depends_on: *backend_depends_on
    expose:
      - "8084"
    networks:
      - backend

  payment-service:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        MODULE_PATH: services:payment-service
    restart: unless-stopped
    environment:
      <<: *backend_env
      PAYMENT_MODE: ${PAYMENT_MODE}
    depends_on: *backend_depends_on
    expose:
      - "8085"
    networks:
      - backend

  bazaar-service:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        MODULE_PATH: services:bazaar-service
    restart: unless-stopped
    environment: *backend_env
    depends_on: *backend_depends_on
    expose:
      - "8086"
    networks:
      - backend

  notification-service:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        MODULE_PATH: services:notification-service
    restart: unless-stopped
    environment:
      <<: *backend_env
      MAIL_USERNAME: ${MAIL_USERNAME}
      MAIL_PASSWORD: ${MAIL_PASSWORD}
    depends_on: *backend_depends_on
    expose:
      - "8087"
    networks:
      - backend

  media-service:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        MODULE_PATH: services:media-service
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
      EUREKA_HOST: discovery-server
      MINIO_URL: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
      MINIO_BUCKET: ${MINIO_BUCKET}
    depends_on:
      minio:
        condition: service_healthy
      discovery-server:
        condition: service_started
    expose:
      - "8088"
    networks:
      - backend

  web-app:
    build:
      context: .
      dockerfile: docker/frontend/Dockerfile
      args:
        APP_PATH: frontend/web-app
        VITE_API_URL: ${VITE_API_URL}
        VITE_API_BASE_URL: ${VITE_API_BASE_URL}
    restart: unless-stopped
    ports:
      - "3000:80"
    networks:
      - edge

  admin-app:
    build:
      context: .
      dockerfile: docker/frontend/Dockerfile
      args:
        APP_PATH: frontend/admin-app
        VITE_API_URL: ${VITE_API_URL}
        VITE_API_BASE_URL: ${VITE_API_BASE_URL}
    restart: unless-stopped
    ports:
      - "3001:80"
    networks:
      - edge

  partner-app:
    build:
      context: .
      dockerfile: docker/frontend/Dockerfile
      args:
        APP_PATH: frontend/partner
        VITE_API_URL: ${VITE_API_URL}
        VITE_API_BASE_URL: ${VITE_API_BASE_URL}
    restart: unless-stopped
    ports:
      - "3002:80"
    networks:
      - edge

  telegram-bot:
    build:
      context: .
      dockerfile: telegram-bot/Dockerfile
    restart: unless-stopped
    environment:
      PORT: 3000
      NODE_ENV: production
      TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN}
      TELEGRAM_WEBHOOK_URL: ${TELEGRAM_WEBHOOK_URL}
      TELEGRAM_WEBHOOK_SECRET: ${TELEGRAM_WEBHOOK_SECRET}
      USER_SERVICE_BASE_URL: http://api-gateway:8080
      COUPON_SERVICE_BASE_URL: http://api-gateway:8080
      COUPON_SERVICE_BOT_API_KEY: ${BOT_API_KEY}
      PREVIEW_WEBHOOK_TOKEN: ${PREVIEW_WEBHOOK_TOKEN}
      FRONTEND_WEB_APP_URL: ${FRONTEND_WEB_APP_URL}
      BACKEND_TIMEOUT_MS: 10000
      SESSION_TTL_MINUTES: 60
    depends_on:
      api-gateway:
        condition: service_started
    ports:
      - "3010:3000"
    networks:
      - edge
      - backend

networks:
  edge:
  backend:

volumes:
  postgres_data:
  redis_data:
  rabbitmq_data:
  minio_data:
```

- [ ] **Step 2: Validate compose syntax**

Run:

```bash
docker compose --env-file .env.prod.example -f docker-compose.prod.yml config >/tmp/topdim-compose-prod.yml
```

Expected: command exits with status `0`.

- [ ] **Step 3: Build all compose images**

Run:

```bash
docker compose --env-file .env.prod.example -f docker-compose.prod.yml build
```

Expected: all services build successfully.

- [ ] **Step 4: Start infrastructure and discovery first**

Run:

```bash
docker compose --env-file .env.prod.example -f docker-compose.prod.yml up -d postgres redis rabbitmq minio discovery-server
```

Expected: containers start; `postgres`, `redis`, `rabbitmq`, and `minio` become healthy.

- [ ] **Step 5: Start the full stack**

Run:

```bash
docker compose --env-file .env.prod.example -f docker-compose.prod.yml up -d
```

Expected: stack starts. Some app-level failures may occur with placeholder secrets/domains, but containers should build and dependency wiring should use Docker service names, not `localhost`.

- [ ] **Step 6: Inspect logs for connection errors**

Run:

```bash
docker compose --env-file .env.prod.example -f docker-compose.prod.yml logs --tail=100 api-gateway identity-service coupon-service order-service payment-service bazaar-service notification-service media-service telegram-bot
```

Expected: no errors like `Connection refused localhost`, `UnknownHostException`, or database host pointing to `localhost`.

- [ ] **Step 7: Commit**

```bash
git add docker-compose.prod.yml
git commit -m "chore: add production compose stack"
```

---

### Task 7: Add README Deployment Notes

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add deployment section near the quick start section**

Add this section:

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add production deployment notes"
```

---

### Task 8: Final Verification

**Files:**
- Verify all files created in previous tasks.

- [ ] **Step 1: Check production files exist**

Run:

```bash
test -f .dockerignore
test -f docker/backend/Dockerfile
test -f docker/frontend/Dockerfile
test -f docker/frontend/nginx.conf
test -f telegram-bot/Dockerfile
test -f docker-compose.prod.yml
test -f .env.prod.example
```

Expected: all commands exit with status `0`.

- [ ] **Step 2: Validate compose config**

Run:

```bash
docker compose --env-file .env.prod.example -f docker-compose.prod.yml config >/tmp/topdim-compose-prod.yml
```

Expected: command exits with status `0`.

- [ ] **Step 3: Build all images**

Run:

```bash
docker compose --env-file .env.prod.example -f docker-compose.prod.yml build
```

Expected: all images build successfully.

- [ ] **Step 4: Run existing backend tests**

Run:

```bash
./gradlew test
```

Expected: Gradle test task passes.

- [ ] **Step 5: Run frontend builds locally**

Run:

```bash
cd frontend/web-app && npm ci && npm run build
cd ../admin-app && npm ci && npm run build
cd ../partner && npm ci && npm run build
```

Expected: all three Vite apps build successfully.

- [ ] **Step 6: Final status**

Run:

```bash
git status --short
```

Expected: clean worktree after commits, or only intentional uncommitted files if the user asked not to commit.

---

## Self-Review Checklist

- [ ] Dockerfiles do not bake secrets into images.
- [ ] Production compose does not use `localhost` for inter-container dependencies.
- [ ] Only gateway, frontends, and bot expose public ports.
- [ ] Backend services use `SPRING_PROFILES_ACTIVE=prod`.
- [ ] Frontend apps receive `VITE_API_URL` and `VITE_API_BASE_URL` at build time.
- [ ] Telegram bot points to `http://api-gateway:8080` internally and public webhook URL externally.
- [ ] `.env.prod.example` contains placeholders, not real secrets.
- [ ] README explains EasyPanel target services and ports.
