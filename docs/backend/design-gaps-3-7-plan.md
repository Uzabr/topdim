# План реализации бэкенда: недостающие фичи нового дизайна web-app

> Назначение: пошаговый план для ИИ-агента, который берёт пункты **по одному**.
> Каждый раздел самодостаточен: цель, затронутые сервисы, модель данных + миграция,
> контракт API, логика, тесты, зависимости и открытые продуктовые решения.
>
> Контекст: аудит от 2026-07-19 (см. `docs/frontend/web-app.md` «Зависит от бэкенда»).
>
> **В объёме (решено 2026-07-19):** Q&A к купону и Telegram-привязка профиля.
> **Вне объёма (решено не делать сейчас):** фото к отзыву (достаточно самого отзыва),
> бонусный баланс и бонус за отзыв (система лояльности пока не нужна).
> Пункты 1–2 (`OrderResponse.title`, `PurchasedCoupon.pricePaid`) сделаны отдельно в order-service.

## Общие конвенции (соблюдать во всех пунктах)

- **Формат ответа:** обёртка `uz.topdim.common.dto.ApiResponse<T>` (`success/message/data/timestamp`),
  фабрики `ApiResponse.success(data)`, `success(message, data)`, `error(message)`.
- **Текущий пользователь:** заголовок `@RequestHeader("X-User-Id") Long userId` (Gateway ставит после JWT).
  Имя — опционально `X-User-Name`. SecurityContext для userId в контроллерах не используется.
- **Роли:** `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")` на admin, `hasAnyRole('MODERATOR',...)` на mod,
  `hasAnyRole('PARTNER',...)` на partner. Публичные контроллеры — без аннотации, но путь должен быть
  в gateway `OPEN_ENDPOINTS` (см. ниже).
- **Ошибки:** кидать `ResourceNotFoundException`→404, `IllegalStateException`→409, `IllegalArgumentException`→400;
  их подхватит `@RestControllerAdvice GlobalExceptionHandler` каждого сервиса.
- **Миграции Flyway** (`resources/db/migration`, формат `V{n}__{snake}.sql`): на момент плана
  **coupon-service = следующий V24**, **identity-service = следующий V12**.
  ⚠️ Если несколько пунктов реализуются параллельно — согласовать номера, чтобы не было коллизий.
- **Маппинг entity→DTO:** MapStruct в coupon-service (`@Mapper(componentModel="spring")`),
  ручные мапперы в identity-service.
- **⚠️ Gateway (обязательно для любого нового публичного пути):** новый путь надо добавить в
  1) `infrastructure/api-gateway/.../application.yml` — `Path=` предикат соответствующего сервиса, и
  2) `JwtAuthenticationFilter.OPEN_ENDPOINTS` — только если чтение публичное без токена.
  (Именно отсутствие обоих было багом `/api/v1/situations` 404 → PR #62.) Admin/partner/mod пути в
  `OPEN_ENDPOINTS` НЕ добавлять — они должны требовать JWT.
- **Тесты:** копировать стиль situations (`SituationRepositoryTest`/`ServiceTest`/`ControllerTest`) и
  reviews. Обязательны негативные кейсы (нет сущности, дубликат, нет прав).

## Рекомендуемый порядок

1. **Пункт 3 — Q&A** (coupon-service, самодостаточно, шаблон = reviews) ✅ готов к реализации
2. **Пункт 6 — Telegram-привязка** (identity-service + бот) 🟡 нужно решение по флоу привязки

---

## Пункт 3 — Q&A на странице купона ✅

**Цель:** пользователи задают вопросы к офферу, продавец/модератор отвечает; на странице купона
показывается список опубликованных пар «вопрос-ответ».

**Сервис:** coupon-service. **Шаблон для копирования:** модуль `reviews` (entity+repo+service+controller+DTO+тесты).

### Модель данных — миграция `V24__create_coupon_questions.sql`
Таблица `coupon_questions`:
- `id BIGSERIAL PK`
- `coupon_offer_id BIGINT NOT NULL REFERENCES coupon_offers(id) ON DELETE CASCADE`
- `user_id BIGINT NOT NULL`
- `user_name VARCHAR(...)`
- `question TEXT NOT NULL`
- `answer TEXT` (nullable — пока нет ответа)
- `answered_by_user_id BIGINT` (nullable)
- `answered_at TIMESTAMP` (nullable)
- `status VARCHAR(...) NOT NULL` — enum `PENDING / PUBLISHED / REJECTED` (модерация вопросов)
- `created_at TIMESTAMP NOT NULL DEFAULT now()`
- Индекс `idx_coupon_questions_offer_status (coupon_offer_id, status)`.

Сущности: `entity/CouponQuestion.java`, `entity/QuestionStatus.java` (enum). По образцу `Review`/`ReviewStatus`.

### ✅ Продуктовые решения (зафиксированы 2026-07-19)
- **Кто отвечает:** ТОЛЬКО админ/модератор (НЕ партнёр). → owner-check не нужен, ответ идёт через `/api/v1/mod/**`.
- **Премодерация вопросов:** ДА. Новый вопрос = `PENDING`; становится виден только после того, как
  модератор ответил (→ `PUBLISHED`) или отклонил (→ `REJECTED`).

### API
Q&A оформляется как **отдельный путь `/api/v1/questions`** (по образцу `/api/v1/reviews`), НЕ вкладывается в
`/api/v1/coupons` — чтобы POST оставался аутентифицированным (иначе open-endpoint `/api/v1/coupons` пустит
аноним без `X-User-Id`).
- **POST `/api/v1/questions`** — пользователь задаёт вопрос. `X-User-Id`, `X-User-Name`,
  body `CreateQuestionRequest{ @NotNull Long couponOfferId; @Size(min=5,max=1000) String question }`.
  Проверка: оффер существует. Статус нового вопроса = `PENDING`. Возврат `ApiResponse<Long>`.
- **GET `/api/v1/questions/coupon/{offerId}?page&size`** — публичный список: статус `PUBLISHED` и непустой
  `answer`. `ApiResponse<Page<QuestionResponse>>`.
- **GET `/api/v1/questions/my?page&size`** — свои вопросы (`X-User-Id`), любой статус (видеть, ответили ли).
- **Модерация (admin/mod):** под `/api/v1/mod/questions` (route `/api/v1/mod/**` уже есть в gateway),
  `@PreAuthorize hasAnyRole('MODERATOR','ADMIN','SUPER_ADMIN')`:
  - **GET `/api/v1/mod/questions?status=PENDING&page&size`** — очередь на модерацию.
  - **PATCH `/api/v1/mod/questions/{id}/answer`** — `X-User-Id` (модератор), body `AnswerQuestionRequest{ @Size(min=1,max=2000) String answer }`
    → проставляет `answer`, `answered_by_user_id`, `answered_at`, статус → `PUBLISHED`.
  - **PATCH `/api/v1/mod/questions/{id}/reject`** — отклонить спам (статус → `REJECTED`, опц. reason).

⚠️ **Gateway:** добавить `Path=/api/v1/questions/**` в предикат coupon-service (`application.yml`).
Публичный GET `/api/v1/questions/coupon/**` без токена — сделать ТОЧНО как у reviews (свериться с
`JwtAuthenticationFilter`: как reviews отдаёт публичный GET, но требует JWT на POST; повторить ту же схему,
как это делалось для PR #62). `/api/v1/questions` целиком в `OPEN_ENDPOINTS` НЕ класть — POST должен требовать JWT.

DTO: `CreateQuestionRequest`, `AnswerQuestionRequest`, `QuestionResponse{ id, userName, question, answer, answeredAt, status, createdAt }`.

### Тесты
Repo (сохранение, выборка PUBLISHED по офферу), Service (создание→PENDING, оффер не найден→404, answer→PUBLISHED,
reject→REJECTED), Controller (POST 200, публичный GET отдаёт только PUBLISHED с ответом, mod-эндпоинты без прав→403).

### Фронт
Проверить, есть ли на странице купона UI Q&A; если фронт уже готов — сверить контракт полей.
Отметить закрытие пункта в `docs/frontend/web-app.md`.

---

## Пункт 6 — Telegram-привязка к профилю 🟡

**Цель:** в профиле — привязка Telegram + статус «подключён» (и, вероятно, для будущих уведомлений/бота).

**Сервис:** identity-service (хранение привязки) + бот (coupon-service держит bot-webhook).

### Модель данных (identity-service) — миграция `V12__user_telegram_link.sql`
`ALTER TABLE users ADD COLUMN`:
- `telegram_chat_id BIGINT` (nullable, UNIQUE) — id чата/пользователя в Telegram
- `telegram_username VARCHAR(100)` (nullable)
- `telegram_linked_at TIMESTAMP` (nullable)

В `entity/User.java` — соответствующие поля (по стилю существующих).

### ⛔ Продуктовое РЕШЕНИЕ по флоу привязки (блокер)
Настоящая привязка требует подтверждения через бота, иначе любой введёт чужой @username.
Предлагаемый безопасный флоу (переиспользует существующий механизм одноразовых токенов
`AuthActionToken`/`AuthActionType` в identity):
1. **POST `/api/v1/users/me/telegram/link-code`** (`X-User-Id`) → identity генерирует одноразовый код
   (новый `AuthActionType.TELEGRAM_LINK`, TTL ~10 мин) → возвращает код/deeplink `https://t.me/<bot>?start=<code>`.
2. Пользователь жмёт deeplink → шлёт боту `/start <code>`.
3. Бот (coupon-service bot-webhook, путь `/api/v1/bot/**`, защищён `X-Bot-Api-Key`) получает `chat_id`+`username`
   и вызывает identity: **POST `/api/v1/internal/telegram/confirm`** (внутренний, только из сети/по секрету)
   с `{ code, chatId, username }` → identity валидирует код, проставляет поля, статус «подключён».
4. **DELETE `/api/v1/users/me/telegram`** — отвязка (обнуляет поля).

Открытые вопросы: имя бота/токен; как coupon-service зовёт identity (Feign-клиент, как
`OrderReviewEligibilityClient`?); защита внутреннего эндпоинта (сеть/секрет, не через gateway).
Альтернатива (проще, менее строго): пользователь вводит @username без подтверждения — НЕ рекомендуется.

### API профиля
`UserProfileResponse` (GET `/api/v1/users/me`) добавить: `boolean telegramConnected`, `String telegramUsername`.
Маппинг — в `UserService.mapToProfile` (ручной).

### Тесты
Service (генерация кода, confirm с валидным/просроченным кодом, отвязка), профиль отдаёт статус.
Безопасность: confirm-эндпоинт недоступен снаружи через gateway.

---

## Сводная таблица готовности

| # | Фича | Сервис(ы) | Готовность | Блокер |
|---|------|-----------|-----------|--------|
| 3 | Q&A к купону | coupon | ✅ ready to code | нет — решения зафиксированы (admin/mod отвечают, премодерация) |
| 6 | Telegram-привязка | identity + бот | 🟡 | флоу привязки (токен + бот + внутренний вызов) |

**Вне объёма (пока не делаем):** фото к отзыву; бонусный баланс; бонус за отзыв.

**Вывод для агента:** **пункт 3 (Q&A) — можно кодить прямо сейчас**, все решения приняты, шаблон = reviews.
Пункт **6 (Telegram)** — после решения по флоу привязки (deeplink + бот + внутренний confirm).
