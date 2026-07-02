# Security Code-Hardening — план задач

Оставшиеся **код**-правки из security-аудита (2026-06-20). Серверная часть
(фаервол, JWT в проде, RabbitMQ, H1), CI/CD и защита от утечки секретов уже
закрыты вручную/отдельными PR — здесь только то, что чинится в коде.

## Статус (обновлено 2026-06-21)

| Тикет | Статус |
|---|---|
| C1 — убрать дефолт `jwt.secret` | ✅ сделано (PR #42), в проде |
| M2 — actuator наружу | ⚠️ частично (PR #44): health-детали скрыты (`show-details: never`), info/env/beans/prometheus наружу = 404. **НО `/actuator/metrics` всё ещё 200 снаружи** — см. follow-up ниже |
| M3 — DEBUG-логи в проде | ✅ сделано (PR #44), в проде |
| L3, L4, L5, L6 — authz consistency | ✅ сделано (PR #46), в проде (L4 заодно закрыл H2) |
| H3 — XFF rate-limit | ✅ сделано (PR #47), в проде |
| **M1** — дефолт пароля БД | 🔲 осталось |
| **M5** — user enumeration | 🔲 осталось |
| **M6** — Redis fail-open для admin | 🔲 осталось |
| **M4** — токены в localStorage → cookie | 🔲 осталось (большой) |

### Follow-up M2-metrics (новый)
`/actuator/metrics` отдаёт **200 снаружи** на `api.sizbiz.uz`. Причина: собственный
actuator gateway обслуживается **вне** `JwtAuthenticationFilter`, поэтому удаление
`/actuator` из `OPEN_ENDPOINTS` его не блокирует. **Фикс:** у `api-gateway` убрать
`metrics` из `management.endpoints.web.exposure.include` (оставить `health`; его
`/actuator/prometheus` и так 404 — registry нет). Либо вынести actuator на
`management.server.port` (внутренняя сеть), либо закрыть `/actuator/**` на traefik
для `api.sizbiz.uz`. Ветка: `fix/actuator-metrics-internal`.

## Правила выполнения

- Ветка от свежего `main`: `git checkout main && git pull` → `git checkout -b fix/<...>`.
- **Маленькие сфокусированные PR** (см. группировку), не всё в одном.
- PR → CI зелёный (gitleaks + Backend tests + Frontend) → squash-merge.
- Не коммитить секреты. Не добавлять слабые дефолты в `application.yml`.
- На каждое изменение логики — тест (unit, при наличии — slice). Для auth —
  обязательно негативные кейсы.

## Группировка по PR

1. `fix/actuator-and-logging` → M2, M3
2. `fix/authz-consistency` → L3, L4, L5, L6
3. `fix/rate-limit-xff` → H3
4. `fix/redis-fail-closed-admin` → M6
5. `fix/register-user-enumeration` → M5
6. `fix/refresh-token-cookie` → M4 (большой, фронт+бэк, отдельно)

---

## H3 — Обход rate-limit через `X-Forwarded-For` (HIGH)

- **Файл:** `infrastructure/api-gateway/src/main/java/uz/topdim/gateway/config/RateLimitConfig.java` (`ipKeyResolver`)
- **Проблема:** ключ лимита берётся из первого (клиентского) значения `X-Forwarded-For` → подделкой XFF каждый запрос выглядит «новым IP» → лимит на `/login`/`/register` не работает → брутфорс.
- **Сделать:**
  - Доверять `X-Forwarded-For` только от traefik и брать правый доверенный hop (последний прокси), а не левый клиентский. Лучше — настроить traefik перезаписывать XFF (forwardedHeaders/trustedIPs) и брать реальный peer.
  - Доп. защита: вторичный лимит логина по `email` (связать с `LoginAttemptService`).
- **Тесты:** запросы с поддельным `X-Forwarded-For` не обходят лимит на `/login`.
- **Приёмка:** после N попыток с разными подделанными XFF приходит `429`.

---

## M2 — Actuator наружу + `show-details: always` (MEDIUM)

- **Файлы:** `application.yml` всех сервисов (блок `management`); `infrastructure/api-gateway/.../filter/JwtAuthenticationFilter.java` (`OPEN_ENDPOINTS` содержит `/actuator`).
- **Проблема:** снаружи (`api.sizbiz.uz/actuator/health`) видны детали health + `prometheus`/`metrics`.
- **Сделать:**
  - `management.endpoint.health.show-details: when-authorized` (или `never`).
  - `management.endpoints.web.exposure.include: health` (убрать `prometheus,metrics,info` наружу; нужен Prometheus — вынести на `management.server.port` во внутренней сети).
  - В gateway убрать `/actuator` из `OPEN_ENDPOINTS`.
- **Приёмка:** `https://api.sizbiz.uz/actuator/health` → `404/401` или без деталей; `/actuator/prometheus` снаружи недоступен.

## M3 — Прод-логи на DEBUG (MEDIUM)

- **Файлы:** `services/identity-service/src/main/resources/application.yml` (`logging.level.uz.topdim: DEBUG`) + `application-prod.yml` (не понижает). Проверить остальные сервисы.
- **Сделать:** в `application-prod.yml` каждого сервиса `logging.level.uz.topdim: INFO`.
- **Приёмка:** при `SPRING_PROFILES_ACTIVE=prod` уровень `uz.topdim` = INFO.

## M5 — User enumeration при регистрации (MEDIUM)

- **Файл:** `services/identity-service/src/main/java/uz/topdim/identity/service/AuthService.java` (`register()` — отдельные сообщения про email/телефон). Проверить password-reset.
- **Сделать:** единый нейтральный ответ; раскрытие занятости — не на этапе ответа, а через email/SMS-верификацию.
- **Тесты:** занятый email и занятый телефон → одинаковый клиентский ответ.
- **Приёмка:** по ответу нельзя отличить «email занят» от «телефон занят».

## M6 — Redis fail-open для admin/super (MEDIUM)

- **Файл:** `infrastructure/api-gateway/src/main/java/uz/topdim/gateway/service/ReactiveTokenValidationService.java` (`onErrorResume → Mono.just(false)`).
- **Проблема:** при недоступном Redis отозванные/заблокированные токены проходят; блокировка скомпрометированного админа не срабатывает.
- **Сделать:** fail-closed для `/api/v1/admin/**` и `/api/v1/super/**` (при ошибке Redis — отклонять 401/503); для остальных путей оставить fail-open. Признак пути передать из `JwtAuthenticationFilter`.
- **Тесты:** при падении Redis запрос к `/api/v1/super/**` отклонён, к обычному — пропущен.
- **Приёмка:** негативный тест с замоканным падением Redis.

## M4 — Токены в `localStorage` → httpOnly cookie (MEDIUM, большой)

- **Файлы:** фронт `frontend/web-app/src/store/authStore.ts`, `frontend/web-app/src/api/client.ts`, `frontend/partner/src/App.tsx`; бэк `identity` AuthController/AuthService.
- **Сделать:** refresh-token в `httpOnly`+`Secure`+`SameSite` cookie (бэк отдаёт `Set-Cookie`, читает из cookie на `/refresh` и `/logout`). Access-token — в памяти JS, не в localStorage. Усилить CSP (без `unsafe-inline`).
- **Замечание:** затрагивает CORS (`allow-credentials` уже true) и gateway. Проверить логин/refresh/logout end-to-end.
- **Приёмка:** refresh-token не виден из JS (`document.cookie`); логин/refresh/logout работают.

---

## LOW

- **L3 — prefix-matching open-эндпоинтов.** `JwtAuthenticationFilter.isOpenEndpoint` использует `startsWith` → `"/api/v1/coupons"` совпадёт с `"/api/v1/couponsX"`. Сделать: точное сравнение или граница по `/` (`path.equals(x) || path.startsWith(x + "/")`).
- **L4 — bot-ключ не constant-time + fail-open.** `services/coupon-service/.../controller/BotWebhookController.java` (`botApiKey.equals(apiKey)`; при пустом ключе пропускает). Сделать: `MessageDigest.isEqual(...)` и fail-closed при пустом ключе. (H2/bot отложен — Telegram не интегрируется — но fix дешёвый.)
- **L5 — несогласованность ролей.** Gateway пускает `MODERATOR` в `/api/v1/admin/**`, сервисы требуют `ADMIN/SUPER_ADMIN`. Привести к одному правилу.
- **L6 — CORS дефолты localhost.** Убедиться, что в проде origin задаются через env и нет `*` с credentials.

---

## Не код (инфра, делаем отдельно, не агент)

- 4 «пустых» EasyPanel-сервиса (order/payment/notification/media) → заимпортить
  в EasyPanel или перейти на декларативный `docker-compose.prod.yml` (единый
  владелец конфига).

## Уже сделано вне кода (не повторять)

- Фаервол (2377/7946/5672), JWT ротация в проде, RabbitMQ durability, H1
  (публичные роутеры внутренних сервисов убраны), CI/CD (trunk-based manual
  deploy, пин экшенов к SHA, dependabot), gitleaks, CONTRIBUTING.md.
- C1 (слабый JWT-дефолт) уже убран из кода (`${JWT_SECRET}`).
- TODO владельца (не код): ограничить 2GIS-ключ по домену в кабинете 2GIS;
  сменить DB-пароль; включить хук `git config core.hooksPath .githooks`.

---

## Live-пентест 2026-07-01 — новые находки

Проведён white-box пентест продакшн-сайта `https://sizbiz.uz/uz/` и API
`https://api.sizbiz.uz`. Ниже — уязвимости, **не закрытые** предыдущим аудитом.

### Итоговая таблица новых находок

| ID | Уязвимость | Серьёзность | Статус |
|---|---|---|---|
| N-C1 | 2GIS API ключ в публичном JS бандле | 🔴 Критическая | 🔲 открыто |
| N-C2 | Регистрация с существующим email создаёт дубликат-аккаунт | 🔴 Критическая | 🔲 открыто |
| N-H1 | `window.onerror` показывает стектрейс через `alert()` | 🟠 Высокая | ✅ фикс — ветка `fix/pentest-frontend-hardening` |
| N-H2 | Отсутствие email/SMS верификации при регистрации | 🟠 Высокая | 🔲 открыто (большая фича — отдельно) |
| N-M1 | Полное отсутствие security-заголовков на фронтенде (nginx) | 🟡 Средняя | ⚠️ частично — 5 заголовков добавлены; CSP отложен (сломает 2GIS-карту) |
| N-M2 | CSP на api.sizbiz.uz в режиме `report-only` (не блокирует) | 🟡 Средняя | 🔲 открыто |
| N-M3 | Rate-limit на `/auth/login` непоследователен (bypass) | 🟡 Средняя | 🔲 открыто |
| N-L1 | 500 на `/complaints` и `/refunds` для роли USER | 🔵 Низкая | 🔲 открыто |
| N-L2 | Версия nginx раскрыта в заголовке `Server` | 🔵 Низкая | ✅ фикс — `server_tokens off` (ветка `fix/pentest-frontend-hardening`) |
| N-L3 | Demo-complete эндпоинт присутствует в продакшн-коде | 🔵 Низкая | 🔲 открыто |

> **Подтверждено ранее, статус не изменился:**
> M4 (PII в localStorage) — **открыто**; M2 (actuator `/actuator/health` 200 снаружи) — **открыто**;
> H3 (rate-limit) — PR #47 слит, но N-M3 показывает что bypass всё ещё воспроизводится.

---

### N-C1 — Hardcoded 2GIS API ключ в публичном JS бандле (КРИТИЧЕСКАЯ)

- **Файл:** `frontend/web-app` → сборка `/assets/index-BRtfpR5-.js`
- **Ключ:** `3b3d04f2-7dc0-46bc-899b-2c8652fd4813` (вшит в открытом виде)
- **Проблема:** Ключ виден любому в DevTools. Злоумышленник может использовать
  его для вызовов 2GIS Catalog API за счёт владельца или исчерпать квоту.
- **Сделать:**
  - Создать backend-прокси в `api-gateway` или отдельном сервисе:
    `GET /api/v1/geo/search?q=...` → gateway подставляет ключ из env и
    обращается к 2GIS. Фронтенд видит только `/api/v1/geo/search`.
  - Убрать ключ из `frontend/web-app/.env` / `vite.config.ts` — он не должен
    попадать в бандл.
  - Параллельно — отозвать текущий ключ в кабинете 2GIS и выпустить новый.
- **Приёмка:** `grep -r "3b3d04f2"` в сборке → ноль совпадений.

---

### N-C2 — Регистрация с существующим email создаёт дублирующий аккаунт (КРИТИЧЕСКАЯ)

- **Файл:** `services/identity-service/.../service/AuthService.java` (`register()`)
- **Проблема:** `POST /api/v1/auth/register` с уже зарегистрированным email
  (`buyer001@demo.topdim.uz`) и другим телефоном вернул **HTTP 201** и новый
  валидный JWT с отдельным `sub` (id аккаунта). Два аккаунта с одним email.
  Нарушает уникальность и позволяет обходить блокировки.
- **Сделать:**
  - Убедиться, что в `AuthService.register()` стоит проверка на уникальность
    email **до** сохранения (`userRepository.existsByEmail(email)`).
  - Вернуть `409 Conflict` с нейтральным сообщением (без указания «email занят»
    — чтобы не сломать M5-фикс).
  - Добавить `UNIQUE` constraint на колонку `email` в схеме БД как резервную
    защиту.
- **Тесты:** повторная регистрация с тем же email → 409; с тем же телефоном → 409.

---

### N-H1 — `window.onerror` раскрывает стектрейс через `alert()` (HIGH)

- **Файл:** `frontend/web-app/index.html` (inline `<script>` в `<body>`)
- **Проблема:**
  ```javascript
  window.onerror = function(msg, url, line, col, error) {
    alert("Error: " + msg + "\nLine: " + line + "\n" + (error ? error.stack : ""));
  };
  ```
  Любая JS-ошибка показывает браузерный диалог с полным стектрейсом: раскрывает
  внутренние пути файлов и структуру кода. `alert()` блокирует все браузерные
  события — атакующий может заморозить вкладку у пользователя.
- **Сделать:** удалить весь блок. Для prod — подключить Sentry/аналог через
  env-переменную; для dev — оставить `console.error`.
- **Приёмка:** при JS-ошибке в prod — нет alert-диалога.

---

### N-H2 — Регистрация без верификации email и телефона (HIGH)

- **Файл:** `services/identity-service/.../service/AuthService.java`
- **Проблема:** Аккаунт активируется немедленно, поля `emailVerified` и
  `phoneVerified` в JWT-payload равны `false`. Позволяет массово создавать
  фиктивные аккаунты с произвольными email/телефонами без владения ими.
- **Сделать:**
  - После регистрации отправлять OTP на телефон (SMS) или ссылку на email.
  - До верификации — аккаунт в статусе `PENDING`: может только верифицироваться,
    не может покупать/создавать заказы.
  - Добавить TTL на `PENDING`-аккаунты (например, 24 ч) и их автоудаление.
- **Приёмка:** без верификации `/api/v1/orders` возвращает 403 для нового аккаунта.

---

### N-M1 — Полное отсутствие security-заголовков на фронтенде (MEDIUM)

- **Файл:** `infrastructure/nginx/sizbiz.uz.conf` (или аналог в traefik)
- **Проблема:** `sizbiz.uz` не отдаёт ни одного заголовка безопасности:

  | Заголовок | Риск при отсутствии |
  |---|---|
  | `X-Frame-Options: DENY` | Clickjacking |
  | `Content-Security-Policy` | XSS, data injection |
  | `Strict-Transport-Security` | Downgrade to HTTP |
  | `X-Content-Type-Options: nosniff` | MIME sniffing |
  | `Referrer-Policy` | Утечка URL в referer |
  | `Permissions-Policy` | Доступ к камере/геолокации |

- **Сделать:** добавить в nginx-конфиг фронтенда:
  ```nginx
  add_header X-Frame-Options "DENY" always;
  add_header X-Content-Type-Options "nosniff" always;
  add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
  add_header Referrer-Policy "strict-origin-when-cross-origin" always;
  add_header Permissions-Policy "camera=(), microphone=(), geolocation=(self)" always;
  add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; connect-src 'self' https://api.sizbiz.uz; frame-ancestors 'none';" always;
  ```
- **Приёмка:** `curl -I https://sizbiz.uz/uz/` — все 6 заголовков присутствуют.

---

### N-M2 — CSP на `api.sizbiz.uz` в режиме `report-only` (MEDIUM)

- **Файл:** `infrastructure/api-gateway/...` (Spring Security config или nginx)
- **Проблема:** Заголовок `Content-Security-Policy-Report-Only` не блокирует
  нарушения политики, только логирует. Реальной защиты нет.
- **Сделать:** переключить с `Content-Security-Policy-Report-Only` на
  `Content-Security-Policy`. Для API-сервера достаточно строгой политики:
  `default-src 'none'; frame-ancestors 'none';`
- **Приёмка:** заголовок `Content-Security-Policy` (без `-Report-Only`) присутствует.

---

### N-M3 — Rate-limit на `/auth/login` допускает bypass (MEDIUM)

- **Связано с:** H3 (PR #47) — XFF-bypass закрыт, но проблема другая.
- **Проблема:** В ходе теста 10 параллельных запросов на `/auth/login`:
  попытки 1-2 → 401, попытки 3-5 → 429, попытка 6 → **401 (пропущена!)**, 7-10 → 429.
  Лимитер пропускает часть запросов при параллельной нагрузке (race condition
  в скользящем окне или неатомарный счётчик).
  Нет заголовка `Retry-After` в ответах 429.
- **Сделать:**
  - Использовать атомарный счётчик в Redis (INCR + EXPIRE) вместо sliding-window
    без блокировки.
  - Добавить `Retry-After` заголовок в 429-ответы.
  - Рассмотреть вторичный лимит по `email` в теле запроса (IP-лимит обходится
    при распределённой атаке).
- **Приёмка:** 10 параллельных запросов — не более 2 проходят до 429; все 429
  содержат `Retry-After`.

---

### N-L1 — HTTP 500 на `/api/v1/complaints` и `/api/v1/refunds` для роли USER (LOW)

- **Файлы:** `services/complaint-service` и `services/payment-service` (или
  единый сервис) — контроллеры `ComplaintController`, `RefundController`.
- **Проблема:** Авторизованный USER получает 500 вместо 403 при обращении к
  admin-only эндпоинтам. Это утечка информации (подтверждает существование
  эндпоинта и ошибку в бизнес-логике).
- **Сделать:** добавить проверку роли (`@PreAuthorize("hasRole('ADMIN')")`) на
  уровне контроллера; при отсутствии прав — 403, не 500.
- **Приёмка:** USER получает 403 (не 500) на этих путях.

---

### N-L2 — Версия nginx раскрыта в заголовке `Server` (LOW)

- **Файл:** `infrastructure/nginx/*.conf`
- **Проблема:** `Server: nginx/1.27.5` — точная версия помогает атакующему
  искать CVE под конкретный релиз.
- **Сделать:** в `nginx.conf` добавить `server_tokens off;`
- **Приёмка:** `curl -I https://sizbiz.uz/` — `Server: nginx` без версии или
  заголовок отсутствует.

---

### N-L3 — Demo-complete эндпоинт существует в продакшн-коде (LOW)

- **Эндпоинт:** `POST /api/v1/payments/order/{id}/demo-complete`
- **Файл:** `services/payment-service/.../controller/PaymentController.java`
- **Проблема:** Тестовый эндпоинт для симуляции завершения оплаты присутствует
  в проде. Требует аутентификации (401 без токена), но само существование
  создаёт риск — при ошибке в authz он может быть вызван произвольным
  аутентифицированным пользователем.
- **Сделать:** обернуть контроллер/метод в `@Profile("!prod")` или удалить
  из кода — использовать только в тестовом окружении.
- **Приёмка:** `POST /api/v1/payments/order/1/demo-complete` в prod → 404.
