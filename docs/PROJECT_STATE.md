# PROJECT_STATE.md — topdim: живой контекст для агентов

> **Что это:** единый источник правды о ТЕКУЩЕМ состоянии проекта `topdim` для любого
> AI-агента (Claude, Codex, Gemini/Antigravity, и т.д.). **Читай это ПЕРВЫМ.**
> **Обновляй** сразу, когда меняешь инфраструктуру / деплой / архитектуру / секреты.
> Детали — в связанных доках (см. §9 «Карта документов»); сюда не дублируй, а ссылайся.
>
> **Дисциплина датирования:** волатильные факты помечены `[дата]`. Если факт устарел и
> противоречит коду — **доверяй коду**, потом обнови этот файл.
> **Не храни здесь секреты** (пароли/ключи/токены) — только где они лежат.

---

## 1. Продукт и стек
**topdim** — маркетплейс купонов со скидкой + онлайн-базар (Ташкент, узб. рынок).
- **Backend:** Java 21, Spring Boot 3.4, Spring Cloud Gateway (WebFlux/reactive),
  Eureka (service discovery), Flyway (миграции), JPA/Hibernate. Gradle-монорепо.
- **Инфра:** PostgreSQL 17, Redis 7, RabbitMQ (3-management), MinIO (S3-хранилище картинок).
- **Frontend:** React + Vite + TypeScript — 3 SPA (`web-app`, `admin-app`, `partner`),
  собираются в nginx-образ (`docker/frontend/Dockerfile`, общий `docker/frontend/nginx.conf`).
- **Деплой:** Docker Swarm через **EasyPanel** + `easypanel-traefik` (Let's Encrypt TLS).
  Образы в **GHCR** `ghcr.io/uzabr/topdim/<service>`. Хостинг — Contabo VPS, Ubuntu 24.04.

## 2. Сервисы, порты, БД, домены
Паттерн «база на сервис». Внутренний хост в Swarm = `topdim_<service>` (с подчёркиванием).

| Сервис | SERVER_PORT | БД | Публичный домен |
|---|---|---|---|
| discovery-server (Eureka) | 8761 | — | нет |
| api-gateway | 8080 | — | **api.sizbiz.uz** |
| identity-service | 8081 | topdim_identity | нет (через gateway) |
| coupon-service | 8083 | topdim_coupon | нет |
| order-service | 8084 | topdim_order | нет |
| payment-service | 8085 | topdim_payment | нет |
| notification-service | 8087 | topdim_notification | нет |
| media-service | 8088 | — (MinIO) | нет |
| web-app (frontend) | 80 (nginx) | — | **sizbiz.uz** |
| admin-app (frontend) | 80 | — | **admin.sizbiz.uz** |
| partner (frontend) | 80 | — | **partner.sizbiz.uz** |
| bazaar-service | 8086 | topdim_bazaar | ⚠️ **НЕ в CD/деплое** (есть в коде, не собирается) |

Инфра-сервисы (внутренние, без доменов): `topdim_postgres` (порт 5432, юзер `topdim`),
`topdim_redis` (6379, пароль), `topdim_rabbitmq` (5672, юзер `topdim`, том `/var/lib/rabbitmq`),
`topdim_minio` (9000/9001, бакет `topdim-media`).

## 3. Env-конвенции (как сервисы связаны) [важно]
- **`EUREKA_HOST=discovery-server`** — ⚠️ через **ДЕФИС**, не `topdim_discovery-server`.
  Java/Eureka строит URI по RFC → **подчёркивание в hostname запрещено** → сервис не
  регистрируется. EasyPanel даёт короткий алиас `<service>` без подчёркивания.
  (DB/Redis/Rabbit-клиенты подчёркивание терпят → `DB_HOST=topdim_postgres` ок.)
- **`SERVER_PORT` обязателен** и равен порту сервиса — иначе HEALTHCHECK образа
  (`curl 127.0.0.1:${SERVER_PORT:-8080}/actuator/health`) бьёт в 8080 и Swarm убивает
  контейнер как unhealthy.
- **identity + notification:** `MANAGEMENT_HEALTH_MAIL_ENABLED=false` (иначе health
  проверяет SMTP → DOWN → контейнер убивается).
- **notification:** `TELEGRAM_BOT_TOKEN=<секрет>` включает Telegram-доставку; пустой токен
  fail-closed и переводит доставку в retry, после исчерпания попыток используется подтверждённый email.
  `PARTNER_APP_URL` задаёт базовый адрес ссылок в Telegram и email.
- DB: `DB_HOST=topdim_postgres DB_PORT=5432 DB_USERNAME=topdim DB_PASSWORD=<секрет>`.
  Redis: `REDIS_HOST=topdim_redis REDIS_PORT=6379 SPRING_DATA_REDIS_PASSWORD=<секрет>`.
  Rabbit: `RABBITMQ_HOST=topdim_rabbitmq RABBITMQ_PORT=5672 RABBITMQ_USERNAME=topdim RABBITMQ_PASSWORD=<секрет>`.
  Media: `MINIO_URL=http://topdim_minio:9000 MINIO_ACCESS_KEY=topdim MINIO_SECRET_KEY=<minio-root-pw> MINIO_BUCKET=topdim-media`.
  coupon: `BOT_API_KEY=` (пусто → bot-эндпоинт fail-closed 401; в репо есть утёкший дефолт — не использовать).

## 4. Текущее состояние [2026-07-03]
- **Сайт ЖИВОЙ и защищённый:** sizbiz.uz + api.sizbiz.uz + admin.sizbiz.uz работают, TLS ок.
- **История:** сервер был root-скомпрометирован (криптомайнер+руткит, ~июнь 2026) → полная
  переустановка ОС с нуля → максимальный хардненинг → пересборка стека. Все старые секреты
  ротированы. Детали: `SERVER_SECURITY_PLAN.md`, `REBUILD_RUNBOOK.md` + личная память Claude.
- **Безопасность приложения:** весь код-аудит закрыт (C1, H1–H3, M1–M6, L3–L6) + live-пентест
  (N-H1/N-M1/N-L2 закрыты; N-C2 уже защищён; N-C1/N-H2/N-M2/N-M3/N-L1 — открыты). См.
  `SECURITY_HARDENING.md`.
- **CI/CD (3 фазы):** Фаза 1 (S3-бэкапы) ✅, Фаза 2 (авто-сборка в CI) ✅,
  Фаза 3 (авто-деплой изменённых сервисов через EasyPanel-webhook + пред-деплой бэкап)
  ✅ **live** (PR #56, проверено e2e). CI после сборки по SSH (forced-command, deploy-юзер,
  порт 49222) → бэкап в S3 → webhook только по изменённым. Настройка — `REBUILD_RUNBOOK.md`
  STAGE J. ⚠️ GHCR: EasyPanel тянет приватные образы через **per-service** creds
  (username `uzabr` + PAT `read:packages`, НЕ account-пароль — иначе 403).

## 5. Деплой и CI/CD
- **Build:** `.github/workflows/cd.yml` — `on: push: main` собирает+пушит ВСЕ образы в GHCR
  (`:latest` + `:sha-<commit>`), нативно amd64, `provenance: false`. Через `GITHUB_TOKEN`.
- **Deploy (текущий, ручной):** в EasyPanel «Redeploy», ИЛИ на сервере
  `docker service update --force --image ...@sha256:<digest> --with-registry-auth topdim_<svc>`.
- **Deploy (Фаза 3, ✅ live):** `cd.yml` job `detect-deploy` (dorny/paths-filter)
  считает **изменённые** сервисы (shared-код → весь tier) → job `deploy` по SSH
  (forced-command, порт 49222) запускает `/home/deploy/deploy.sh` → пред-деплой бэкап →
  `curl localhost:3000/api/deploy/<token>` только по изменённым → EasyPanel тянет `:latest`.
  Токены webhook **только на сервере** (не в GitHub). Webhook на 3000 закрыт снаружи → только
  через SSH. Скрипт-источник: `scripts/deploy/deploy.sh`. Серверная настройка: STAGE J.
- **Откат:** `:sha`-теги образов / EasyPanel deploy-история / восстановление из S3.

## 6. Бэкапы [Фаза 1, ✅]
- `/root/backup/topdim-backup.sh` → `pg_dumpall --globals-only` + `pg_dump -Fc` 5 БД → tar.gz →
  **AWS S3 `topdim-db-backups-2026`** (eu-north-1, SSE-AES256). Креды в `/root/backup/backup.env` (600).
- systemd `topdim-backup.timer` — ежедневно 03:00. S3 lifecycle — удаление >30 дней.
- IAM-юзер `topdim-backup` (least-privilege, только этот бакет). **Восстановление проверено.**

## 7. Безопасность сервера [✅]
SSH: только ключ, без пароля, без root, порт **49222**, `AllowUsers`, MaxAuthTries 3, fail2ban.
ufw default-deny (наружу только 22-новый-порт-факт + 80/443). sysctl-хардненинг, auditd, AIDE, rkhunter.
EasyPanel-панель (порт 3000) закрыта снаружи (systemd `block-internal-ports.service`), доступ через
SSH-туннель `ssh -L 3000:localhost:3000 -p 49222`. Rabbit 5672 тоже закрыт снаружи.
Секреты — в EasyPanel env + AWS/GitHub, НЕ в git (стоит gitleaks).

## 8. Грабли / уроки (чтобы не повторять) [важно]
- **Eureka + подчёркивание:** `EUREKA_HOST` только с дефисом (`discovery-server`). См. §3.
- **SERVER_PORT** обязателен под каждый сервис (иначе healthcheck убивает). См. §3.
- **Сборка фронт-образов локально на ARM-Mac под amd64-сервер = кэш-ад.** Рабочий рецепт:
  `docker buildx build --no-cache --platform linux/amd64 --provenance=false --push -t ...`.
  Проверять GHCR через `docker buildx imagetools inspect` (локальный `docker pull` врёт из-за
  кэша манифестов). На сервере обновлять по **digest**, не `:latest`. **Но лучше — не собирать
  локально, а через CI** (нативный amd64, без этих проблем).
- **EasyPanel-шаблоны авто-добавляют публичный `*.easypanel.host` домен** (вектор H1) → у КАЖДОГО
  внутреннего сервиса проверяй, что вкладка «Домены» пуста. Публичные домены только у фронтов+gateway.
- **Docker обходит ufw** — published-порты закрывать через цепочку `DOCKER-USER`, не ufw.
- **App-сервис EasyPanel криво тянет приватные GHCR-образы** (401/500) → предварительно
  `docker pull` на хост, потом деплой из кэша. Публичные образы (rabbit/minio) — через шаблон.
- **PAYMENT_MODE=demo** сейчас в проде (реальной ПС нет) → `/demo-complete` эндпоинт НУЖЕН,
  не отключать (N-L3).

## 9. Карта документов (single source of truth)
- **Этот файл** — верхний контекст, читать первым.
- `CLAUDE.md` — правила работы для Claude (роль, стиль, git-правила).
- `AGENTS.md` — роль агента-ревьюера (QA/бизнес-логика).
- `AI-Team-Playbook.md` — методология «AI-команды» (оркестрация).
- `CONTRIBUTING.md` — git-workflow (trunk-based, PR → main).
- `docs/SERVER_SECURITY_PLAN.md` — план безопасности сервера (6 фаз).
- `docs/REBUILD_RUNBOOK.md` — пошаговый rebuild сервера.
- `docs/SECURITY_HARDENING.md` — статусы всех security-находок (аудит + пентест).

## 10. Как обновлять этот файл
- Изменил инфру/деплой/архитектуру/секреты-местоположение → **обнови соответствующую секцию + дату** в §4.
- Новый урок/грабли → добавь в §8.
- Не пиши сюда секреты. Не дублируй детали из §9-доков — ссылайся.
- Держи кратко и сканируемо: агент должен схватить контекст за 2 минуты.
