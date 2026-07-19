# План реализации: Q&A на странице купона (coupon-service)

> Назначение: самодостаточный план для ИИ-агента. **Готов к реализации — все решения приняты.**
> Контекст: недостающая фича нового дизайна web-app (аудит 2026-07-19).
> Telegram-привязка вынесена в отдельный документ (`telegram-profile-link-plan.md`) — она ещё в
> проработке продуктовой модели. Фото к отзыву / бонусы — вне объёма (решено не делать).

## Общие конвенции coupon-service (соблюдать)

- **Формат ответа:** обёртка `uz.topdim.common.dto.ApiResponse<T>` (`success/message/data/timestamp`),
  фабрики `ApiResponse.success(data)`, `success(message, data)`, `error(message)`.
- **Текущий пользователь:** заголовок `@RequestHeader("X-User-Id") Long userId` (Gateway ставит после JWT).
  Имя — опционально `@RequestHeader("X-User-Name")`. SecurityContext для userId в контроллерах не используется.
- **Роли:** `@PreAuthorize("hasAnyRole('MODERATOR','ADMIN','SUPER_ADMIN')")` на mod-контроллерах.
- **Ошибки:** кидать `ResourceNotFoundException`→404, `IllegalStateException`→409, `IllegalArgumentException`→400;
  их подхватит `@RestControllerAdvice GlobalExceptionHandler`.
- **Миграции Flyway** (`services/coupon-service/src/main/resources/db/migration`, `V{n}__{snake}.sql`):
  на момент плана последняя = V23, **новую создавать с V24** (согласовать номер, если параллельно ещё что-то).
- **Маппинг entity→DTO:** MapStruct (`@Mapper(componentModel="spring")`).
- **Шаблон для копирования:** модуль `reviews` (entity+repo+service+controller+DTO+тесты) — Q&A той же формы.
- **⚠️ Gateway:** новый путь добавить в `infrastructure/api-gateway/.../application.yml` (`Path=` предикат
  coupon-service). Публичный GET без токена — сделать ТОЧНО как reviews (свериться с `JwtAuthenticationFilter`,
  повторить схему из PR #62). Путь Q&A целиком в `OPEN_ENDPOINTS` НЕ класть — POST должен требовать JWT.
- **Тесты:** копировать стиль `SituationRepositoryTest/ServiceTest/ControllerTest` и reviews. Обязательны
  негативные кейсы (нет оффера, нет прав).

## ✅ Продуктовые решения (зафиксированы 2026-07-19)

- **Кто отвечает:** ТОЛЬКО админ/модератор (НЕ партнёр). → owner-check не нужен, ответ через `/api/v1/mod/**`.
- **Премодерация вопросов:** ДА. Новый вопрос = `PENDING`; виден публично только после ответа модератора
  (→ `PUBLISHED`) либо отклонён (→ `REJECTED`).

## Модель данных — миграция `V24__create_coupon_questions.sql`

Таблица `coupon_questions`:
- `id BIGSERIAL PK`
- `coupon_offer_id BIGINT NOT NULL REFERENCES coupon_offers(id) ON DELETE CASCADE`
- `user_id BIGINT NOT NULL`
- `user_name VARCHAR(...)`
- `question TEXT NOT NULL`
- `answer TEXT` (nullable — пока нет ответа)
- `answered_by_user_id BIGINT` (nullable)
- `answered_at TIMESTAMP` (nullable)
- `status VARCHAR(...) NOT NULL` — enum `PENDING / PUBLISHED / REJECTED`
- `reject_reason VARCHAR(...)` (nullable, для REJECTED — по образцу `Review.rejectReason`)
- `created_at TIMESTAMP NOT NULL DEFAULT now()`
- Индекс `idx_coupon_questions_offer_status (coupon_offer_id, status)`.

Сущности: `entity/CouponQuestion.java`, `entity/QuestionStatus.java` (enum). По образцу `Review`/`ReviewStatus`.

## API

Q&A — **отдельный путь `/api/v1/questions`** (по образцу `/api/v1/reviews`), НЕ вкладывать в `/api/v1/coupons`
(иначе open-endpoint `/api/v1/coupons` пустит аноним без `X-User-Id`).

- **POST `/api/v1/questions`** — пользователь задаёт вопрос. `X-User-Id`, `X-User-Name`,
  body `CreateQuestionRequest{ @NotNull Long couponOfferId; @Size(min=5,max=1000) String question }`.
  Проверка: оффер существует (иначе 404). Статус нового = `PENDING`. Возврат `ApiResponse<Long>` (id).
- **GET `/api/v1/questions/coupon/{offerId}?page&size`** — публичный список: статус `PUBLISHED` с непустым
  `answer`. `ApiResponse<Page<QuestionResponse>>`.
- **GET `/api/v1/questions/my?page&size`** — свои вопросы (`X-User-Id`), любой статус (видеть, ответили ли).
- **Модерация (`/api/v1/mod/questions`, route `/api/v1/mod/**` уже есть в gateway),
  `@PreAuthorize hasAnyRole('MODERATOR','ADMIN','SUPER_ADMIN')`:**
  - **GET `/api/v1/mod/questions?status=PENDING&page&size`** — очередь на модерацию.
  - **PATCH `/api/v1/mod/questions/{id}/answer`** — `X-User-Id` (модератор),
    body `AnswerQuestionRequest{ @Size(min=1,max=2000) String answer }` → проставляет `answer`,
    `answered_by_user_id`, `answered_at`, статус → `PUBLISHED`.
  - **PATCH `/api/v1/mod/questions/{id}/reject`** — отклонить (статус → `REJECTED`, опц. `rejectReason`).

DTO: `CreateQuestionRequest`, `AnswerQuestionRequest`,
`QuestionResponse{ id, userName, question, answer, answeredAt, status, createdAt }`.

## Тесты

- **Repo:** сохранение; выборка `PUBLISHED` по офферу; выборка PENDING (очередь).
- **Service:** создание → PENDING; оффер не найден → 404; `answer` → PUBLISHED + проставлены поля;
  `reject` → REJECTED.
- **Controller:** POST 200; публичный GET отдаёт только PUBLISHED с непустым ответом; mod-эндпоинты без
  прав → 403; валидация (короткий вопрос) → 400.

## Фронт

Проверить, есть ли на странице купона UI Q&A; если фронт готов — сверить контракт полей `QuestionResponse`.
Отметить закрытие пункта в `docs/frontend/web-app.md`.
