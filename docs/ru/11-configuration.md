# 11. Конфигурация

Значения в таблице безопасные placeholders. Не копируйте tracked fallback secrets в production.

| Variable | Required | Default | Назначение | Пример | Sensitive |
|---|---:|---|---|---|---:|
| `SPRING_PROFILES_ACTIVE` | prod: да | — | Spring profile | `prod` | нет |
| `SERVER_PORT` | prod: да | service yaml | health/runtime port | `8083` | нет |
| `EUREKA_HOST` | да | `localhost` | discovery hostname | `discovery-server` | нет |
| `DB_HOST`/`DB_PORT` | stateful | localhost/5433 | PostgreSQL | `postgres`/`5432` | нет |
| `DB_USERNAME` | stateful | local dev value | DB principal | `topdim_app` | да |
| `DB_PASSWORD` | stateful | unsafe local fallback | DB password | `<secret>` | да |
| `REDIS_HOST`/`REDIS_PORT` | gateway/identity/coupon/order | localhost/6380 | Redis | `redis`/`6379` | нет |
| `SPRING_DATA_REDIS_PASSWORD` | production | none in local compose | Redis password | `<secret>` | да |
| `RABBITMQ_HOST`/`PORT` | async services | localhost/5673 | broker | `rabbitmq`/`5672` | нет |
| `RABBITMQ_USERNAME/PASSWORD` | async services | local fallback | broker auth | `<secret>` | да |
| `JWT_SECRET` | gateway/identity | unsafe dev fallback | Base64 HMAC key | `<base64-secret>` | да |
| `INTERNAL_AUTH_SECRET` | production | empty | trusted gateway/Feign header | `<secret>` | да |
| `MAIL_USERNAME/PASSWORD` | email | empty | SMTP | `<smtp-user>` | да |
| `MANAGEMENT_HEALTH_MAIL_ENABLED` | identity/notification prod | framework | exclude SMTP from health | `false` | нет |
| `MINIO_URL` | media | local 9000 | S3 endpoint | `http://minio:9000` | нет |
| `MINIO_ACCESS_KEY/SECRET_KEY` | media | unsafe local fallback | object credentials | `<secret>` | да |
| `MINIO_BUCKET` | media | `topdim-media` | object bucket | `topdim-media` | нет |
| `PAYMENT_MODE` | payment | `demo` | `demo` or `provider` | `demo` | нет |
| `BOT_API_KEY` | coupon/bot | tracked fallback risk | bot authorization | `<secret>` | да |
| `BOT_PREVIEW_URL` | coupon | local partner URL | preview webhook | `https://bot.example/webhook/preview` | нет |
| `BOT_PREVIEW_TOKEN` | coupon | tracked fallback risk | preview auth | `<secret>` | да |
| `VITE_API_URL` | web/admin build | empty/local | gateway URL | `https://api.example.com` | нет |
| `VITE_API_BASE_URL` | partner/build | localhost | gateway URL | `https://api.example.com` | нет |
| `CORS_ORIGIN_1..3` | gateway | local origins | allowed SPAs | `https://app.example.com` | нет |
| `TELEGRAM_BOT_TOKEN` | bot/login | empty | Telegram credential | `<secret>` | да |
| `TELEGRAM_WEBHOOK_URL` | bot | empty | public webhook | `https://bot.example/webhooks/telegram` | нет |
| `TELEGRAM_WEBHOOK_SECRET` | bot | none | Telegram header secret | `<secret>` | да |
| `PREVIEW_WEBHOOK_TOKEN` | bot | none | backend webhook secret | `<secret>` | да |
| `USER_SERVICE_BASE_URL` | bot | none | onboarding API base | `http://api-gateway:8080` | нет |
| `COUPON_SERVICE_BASE_URL` | bot | none | coupon bot API base | `http://api-gateway:8080` | нет |
| `COUPON_SERVICE_BOT_API_KEY` | bot | empty | bot API header | `<secret>` | да |
| `BACKEND_TIMEOUT_MS` | bot | 10000 | HTTP timeout | `10000` | нет |
| `SESSION_TTL_MINUTES` | bot | 60 | in-memory FSM TTL | `60` | нет |
| `FRONTEND_WEB_APP_URL` | bot | localhost 5173 | links in bot | `https://app.example.com` | нет |

Дополнительные production variables находятся в `docker-compose.prod.yml`, `.github/workflows/cd.yml` и `scripts/deploy/webhook-tokens.env.example`. GitHub deploy secrets: `DEPLOY_SSH_KEY/HOST/PORT/USER/KNOWN_HOSTS`; notification secrets: Telegram token/chat ID.

Важно: `EUREKA_HOST` должен быть DNS-compatible (`discovery-server`, не hostname с underscore). Каждый production backend container должен иметь корректный `SERVER_PORT`.
