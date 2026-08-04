# Демо-рантбук (локальная презентация «новый интерфейс»)

> Локальный самодостаточный запуск на Mac для показа руководству. Прод (sizbiz.uz) не
> используется. Ветка фронта: `feature/redesign-desktop` (новый интерфейс).

## TL;DR — запустить

```bash
# 1) backend (docker-инфра + 9 сервисов, ~2–3 мин)
./scripts/dev/start-demo.sh

# 2) frontend (в отдельном окне)
cd frontend/web-app && npm run dev      # → http://localhost:5173
```

Открыть **http://localhost:5173** — каталог наполнен (13 категорий, ~250 активных купонов).

Остановить: `./scripts/dev/start-demo.sh --stop` (docker-инфра остаётся), фронт — Ctrl+C.

## Что запущено

| Слой | Как | Порт/URL |
|---|---|---|
| Postgres / Redis / RabbitMQ / MinIO | `docker compose` | 5433 / 6380 / 5673 / 9000 |
| discovery-server (Eureka) | gradle bootRun | http://localhost:8761 |
| api-gateway | gradle bootRun | http://localhost:8080 |
| identity / coupon / order / payment / bazaar / notification / media | gradle bootRun | 8081 / 8083 / 8084 / 8085 / 8086 / 8087 / 8088 |
| web-app (новый интерфейс) | vite dev | http://localhost:5173 |
| RabbitMQ UI / MinIO UI | — | http://localhost:15673 / http://localhost:9001 |

## Тестовые аккаунты (сид, метка `@demo.topdim.uz`), пароль у всех `Demo123!`

- **Покупатели (роль USER):** `buyer001@demo.topdim.uz` … `buyer005@demo.topdim.uz`
- **Владельцы/кассиры (роль PARTNER):** `owner001@demo.topdim.uz` …, `cashier001a@demo.topdim.uz` …
  (для partner-панели — она в этом демо **не поднята**, показываем клиентское приложение web-app).

Покупатели также могут регистрироваться сами на сайте.

## Что показывать (сценарий)

1. **Главная** — купон дня, сетка категорий с реальными счётчиками, тетрис-лента.
2. **Страница купона** (напр. `/ru/coupons/<id>`) — галерея, варианты (VIP/Стандарт), панель
   покупки, цепочка «Купил → QR в Telegram → Пришёл».
3. **Вход** покупателем (`buyer001@demo.topdim.uz` / `Demo123!`) → корзина → оформление →
   demo-оплата → «Мои купоны» с QR/PIN.
4. Переключение **RU/UZ**, избранное, поиск.

## Проверка здоровья (curl)

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/categories   # 200
curl -s "http://localhost:8080/api/v1/coupons?page=0&size=1" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['totalElements'])"  # ~250
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"buyer001@demo.topdim.uz","password":"Demo123!"}'  # 200
```

## Почему отдельный `start-demo.sh`, а не `start-all.sh` (важно для рестарта)

`start-all.sh` при холодном старте ненадёжен; `start-demo.sh` чинит четыре вещи, найденные при
подготовке (2026-07-15):

1. **`--no-daemon`.** Gradle-daemon матчится по версии JVM, а не по окружению, поэтому bootRun
   переиспользует чужой daemon и приложение **не получает `JWT_SECRET`/`DB_PASSWORD`** из `.env`
   → `password authentication failed` / `Could not resolve placeholder 'JWT_SECRET'`. С
   `--no-daemon` JVM форкается прямо из процесса с загруженным `.env`.
2. **Переопределение `JWT_SECRET` валидным Base64.** Значение в `.env` содержит символ `-`
   (не Base64), а `JwtService`/`JwtAuthenticationFilter` делают `Decoders.BASE64.decode(secret)`
   → логин отдаёт **HTTP 500**. Скрипт подставляет одноразовый локальный Base64-секрет
   (`logs/.demo-jwt-secret`, вне git), одинаковый для всех сервисов. `.env` не меняется.
3. **Повторное использование Docker-инфраструктуры между worktree.** Скрипт переиспользует
   уже существующие `topdim-postgres`, `topdim-redis`, `topdim-rabbitmq` и `topdim-minio`,
   запускает остановленные контейнеры и создаёт через стабильный Compose project `topdim`
   только отсутствующие сервисы. Это исключает конфликт `container name is already in use`.
4. **SMTP не ломает local health.** Для demo-процессов экспортируется
   `MANAGEMENT_HEALTH_MAIL_ENABLED=false`, поэтому отсутствие локального SMTP не переводит
   рабочие identity/notification API в состояние `DOWN`.

> Эти пункты — про **локальный запуск**, а не про production-конфигурацию. На проде секрет валидный и логин
> работает. Правки в `.env`/сервисы не вносились.

## Заметки / ограничения демо

- Часть категорий пустые (сид покрывает не все 13) — наполнены Beauty, Рестораны, Кофейни,
  Фитнес, SPA, Развлечения, Услуги (25–30 купонов каждая).
- partner.sizbiz.uz (погашение купона кассиром) в этом локальном демо не поднят — показываем
  клиентское приложение.
- Логи сервисов: `logs/<service>.log`.
