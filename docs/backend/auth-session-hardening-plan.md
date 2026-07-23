# План: session-hardening авторизации (identity-service)

> Для ИИ-агента. Реализовать в этом воркри (ветка `feat/auth-session-hardening`).
> Из аудита авторизации 2026-07-23. **Только identity-service.**
>
> ⛔ НЕ ТРОГАТЬ (эти файлы ведёт параллельная работа — будут конфликты):
> - `infrastructure/api-gateway/**` (любой файл)
> - `services/*/security/RoleHeaderAuthenticationFilter.java`
> - `services/identity-service/.../security/JwtService.java`
> - `services/identity-service/.../config/SecurityConfig.java`
> - `services/*/application.yml`
> Работай только в `AuthService`, `RefreshToken` entity/repository, новой миграции, тестах.

## Конвенции
- Формат ответа `uz.topdim.common.dto.ApiResponse`. Ошибки — `AuthException` (→401), их ловит `GlobalExceptionHandler`.
- Есть готовый хелпер sha256 в `PasswordResetService` (токены сброса уже хэшируются) — тот же приём.
- Следующая Flyway-миграция identity: **V13** (`resources/db/migration/V13__...sql`).
- После изменений: `./gradlew :services:identity-service:test` зелёный. Копировать стиль `AuthServiceTest`.

## Задачи

### 1. Хэшировать refresh-токен в БД (#12)
Сейчас `RefreshToken.token` хранит **plaintext UUID** (`entity/RefreshToken.java`) — утечка БД = кража сессий.
- Хранить **SHA-256(token)** вместо plaintext. Пользователю (в cookie) по-прежнему уходит plain-token.
- `AuthService.createRefreshToken`: сгенерировать `plainToken = UUID`, сохранить `sha256(plainToken)` в entity, вернуть `plainToken`.
- `AuthService.refreshToken(...)`: искать по `sha256(входящий токен)`. Обновить `RefreshTokenRepository` (напр. `findByTokenHash`) — переименовать поле/колонку `token` → `token_hash` (миграция V13: `ALTER TABLE refresh_tokens RENAME COLUMN token TO token_hash;`).
- ⚠️ Существующие refresh-токены станут невалидны после деплоя (лежат plaintext, а искать будем по hash) → активные сессии разлогинятся один раз. Это ок, отметь в PR. (Опц. в миграции: `UPDATE refresh_tokens SET revoked=true;` чтобы явно закрыть старые.)
- Сохранить ротацию/revoked-логику как есть.

### 2. Проверять `deleted` при логине (#15)
`AuthService.login` (~стр. 95) проверяет только `isEnabled()`, но не `isDeleted()`. Soft-deleted юзер с `enabled=true` всё ещё входит.
- Добавить проверку: если `user.isDeleted()` → та же обезличенная ошибка, что и при неверных кредах (анти-enumeration, не «аккаунт удалён»).
- То же в ветках существующего юзера `guestAuth` и `telegramAuth` (если находит deleted-юзера — не пускать).

### 3. Единая инвалидация старых сессий (#6)
`login/change-password/password-reset` вызывают `revokeAllByUser`, а `telegramAuth`/`guestAuth` для существующего юзера — нет (параллельные сессии живут). Для консистентности с `login`:
- В ветках существующего пользователя `telegramAuth` и `guestAuth` вызвать `revokeAllByUser(user)` перед выдачей нового refresh (как это делает `login`).
- `register` — новый юзер, revoke не нужен (нет старых токенов).

## Тесты (обязательно)
- refresh: валидный (по хэшу) проходит; предъявление plain-строки, не совпадающей с хэшем, → ошибка.
- login: `deleted=true` юзер → отказ (обезличенный).
- telegram/guest существующего юзера → старые refresh помечены revoked.

## Сдача
`./gradlew :services:identity-service:test` зелёный → коммит → `git push -u origin feat/auth-session-hardening` → PR в main.
