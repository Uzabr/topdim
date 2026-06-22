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
