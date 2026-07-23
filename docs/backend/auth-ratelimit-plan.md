# План: rate-limit на auth-роуты (api-gateway)

> Для ИИ-агента. Реализовать в этом воркри (ветка `feat/auth-rate-limits`).
> Из аудита авторизации 2026-07-23. **Только `infrastructure/api-gateway/src/main/resources/application.yml`.**
>
> ⛔ НЕ ТРОГАТЬ Java-код (никакие фильтры/`JwtAuthenticationFilter`), только `application.yml` (секция `spring.cloud.gateway.routes`).

## Проблема (#7, #8)
Rate-limit сейчас есть только на `login` (2 r/s), `register` (1 r/s), `telegram` (2 r/s). Остальные auth-пути идут через catch-all route `identity-service-auth` **без лимита**: особенно уязвимы `guest` (спам-создание аккаунтов) и `password-reset/request` (спам писем/enumeration).

## Что сделать
Добавить отдельные route-блоки с `RequestRateLimiter` (по образцу существующих `auth-login`/`auth-register`) для:
- **`/api/v1/auth/guest`** → 1 r/s, burst 2 (как register — анти-спам аккаунтов).
- **`/api/v1/auth/password-reset/request`** → 1 r/s, burst 2 (анти-спам писем/enumeration).
- **`/api/v1/auth/password-reset/confirm`** → 2 r/s, burst 3 (перебор токена).
- **`/api/v1/auth/refresh`** → 3 r/s, burst 5 (легитимные частые refresh, но не даём флудить).

Каждый блок — по образцу `auth-login` (уже в файле): `id`, `uri: lb://identity-service`, `predicates: Path=<путь>`, `filters: RequestRateLimiter` с `redis-rate-limiter.*` + `key-resolver: "#{@ipKeyResolver}"` + `deny-empty-key: false`.

⚠️ Важно: эти конкретные route-блоки должны идти **ВЫШЕ** catch-all `identity-service-auth` в списке routes (Spring Cloud Gateway матчит по порядку — первый совпавший выигрывает), иначе catch-all перехватит раньше.

Примечание: в этом же файле недавно добавлен блок `auth-telegram` — не дублируй, просто добавь новые блоки рядом с `auth-login`/`auth-register`/`auth-telegram`.

## Проверка
`./gradlew :infrastructure:api-gateway:test` компилируется/зелёный (rate-limit юнит-тестами не покрыт — достаточно, что yml валиден и сборка проходит).

## Сдача
Коммит → `git push -u origin feat/auth-rate-limits` → PR в main.
