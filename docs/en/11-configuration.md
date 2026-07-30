# 11. Configuration

Examples are safe placeholders; tracked fallback secrets must not be reused.

| Variable | Required | Default | Purpose | Example | Sensitive |
|---|---:|---|---|---|---:|
| `SPRING_PROFILES_ACTIVE` | production | — | Spring profile | `prod` | no |
| `SERVER_PORT` | production | service YAML | runtime/health port | `8083` | no |
| `EUREKA_HOST` | yes | localhost | discovery host | `discovery-server` | no |
| `DB_HOST/DB_PORT` | stateful | localhost/5433 | PostgreSQL | `postgres`/`5432` | no |
| `DB_USERNAME/DB_PASSWORD` | stateful | unsafe local fallback | DB credential | `<secret>` | yes |
| `REDIS_HOST/REDIS_PORT` | selected services | localhost/6380 | Redis | `redis`/`6379` | no |
| `SPRING_DATA_REDIS_PASSWORD` | production | none locally | Redis auth | `<secret>` | yes |
| `RABBITMQ_HOST/PORT` | async services | localhost/5673 | broker | `rabbitmq`/`5672` | no |
| `RABBITMQ_USERNAME/PASSWORD` | async services | local fallback | broker auth | `<secret>` | yes |
| `JWT_SECRET` | gateway/identity | unsafe dev fallback | Base64 HMAC key | `<base64-secret>` | yes |
| `INTERNAL_AUTH_SECRET` | production | empty | trusted internal header | `<secret>` | yes |
| `MAIL_USERNAME/PASSWORD` | email | empty | SMTP | `<secret>` | yes |
| `MANAGEMENT_HEALTH_MAIL_ENABLED` | identity/notification prod | framework | remove SMTP from health | `false` | no |
| `MINIO_URL` | media | local 9000 | S3 endpoint | `http://minio:9000` | no |
| `MINIO_ACCESS_KEY/SECRET_KEY` | media | unsafe local fallback | object auth | `<secret>` | yes |
| `MINIO_BUCKET` | media | `topdim-media` | bucket | `topdim-media` | no |
| `PAYMENT_MODE` | payment | `demo` | demo/provider | `demo` | no |
| `BOT_API_KEY` | coupon/bot | tracked fallback risk | bot authorization | `<secret>` | yes |
| `BOT_PREVIEW_URL/TOKEN` | coupon | local/risky fallback | preview integration | `<secret>` | token |
| `VITE_API_URL` | web/admin build | empty/local | gateway URL | `https://api.example.com` | no |
| `VITE_API_BASE_URL` | partner/build | localhost | gateway URL | `https://api.example.com` | no |
| `CORS_ORIGIN_1..3` | gateway | local origins | SPA origins | `https://app.example.com` | no |
| `TELEGRAM_BOT_TOKEN` | bot/login | empty | Telegram credential | `<secret>` | yes |
| `TELEGRAM_WEBHOOK_URL/SECRET` | bot | empty/none | webhook endpoint/auth | `<secret>` | secret |
| `PREVIEW_WEBHOOK_TOKEN` | bot | none | backend webhook auth | `<secret>` | yes |
| `USER_SERVICE_BASE_URL` | bot | none | onboarding API | `http://api-gateway:8080` | no |
| `COUPON_SERVICE_BASE_URL` | bot | none | coupon API | `http://api-gateway:8080` | no |
| `COUPON_SERVICE_BOT_API_KEY` | bot | empty | bot header | `<secret>` | yes |
| `BACKEND_TIMEOUT_MS` | bot | 10000 | HTTP timeout | `10000` | no |
| `SESSION_TTL_MINUTES` | bot | 60 | in-memory FSM TTL | `60` | no |
| `FRONTEND_WEB_APP_URL` | bot | localhost 5173 | generated links | `https://app.example.com` | no |

Deployment variables and secrets are also referenced by `docker-compose.prod.yml`, `.github/workflows/cd.yml`, and `scripts/deploy/webhook-tokens.env.example`.

`EUREKA_HOST` must be DNS-compatible (`discovery-server`, not an underscored host). Every production backend container needs the correct `SERVER_PORT`.
