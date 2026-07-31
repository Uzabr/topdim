# Эпик: Аккаунт и коммуникации (аватар, SMS, верификация, Google, уведомления)

> Поэтапный план для распределения между агентами. **Бэкенд (`services/*`, `infrastructure/*`, `shared/*`) ведёт Claude; фронт (`frontend/*`) — Codex** (может запускать свои под-агенты). Каждая задача = отдельный focused PR. Правила — ниже.

## Регламент (обязательно для всех агентов)
1. **Границы:** Codex → только `frontend/*`; Claude → `services/*`/`infrastructure/*`/`shared/*`. Шов контрактов `frontend/web-app/src/api/*.ts` ↔ бэк-DTO меняет **одна сторона по согласованию**.
2. **1 PR = 1 фича.** Без кросс-стека (фронт+бэк+миграция в одном PR — запрещено). Без новых зависимостей без согласования.
3. **Миграции:** занять номер в реестре ниже + сверить `origin/main` ПЕРЕД созданием. Следующие свободные: **identity V14, coupon V25, order V14, payment V5, bazaar V6, notification V2**.
4. **Ревью-гейт:** каждый PR ревьюит Claude перед мержем (полный список файлов + `grep db/migration/`, контракты, безопасность, границы).
5. **Не мержить 2 PR подряд** (деплой-шторм); проверять реальный сервис после деплоя.
6. ⚠️ **Фронт-коллизии:** НЕ запускать параллельно два агента, которые оба правят `api/auth.ts` / `store/authStore.ts` / `components/auth/LoginCard.tsx` — сериализовать их. Аватар/профиль-настройки (`components/profile/*`, `api/media.ts`) можно параллелить с одной auth-задачей.

## Граф зависимостей (что от чего)
```
Каналы:   B1 SMS(Eskiz)          B2 Email(SMTP)
              │                      │
Верифи:   C2 phone-OTP          C1 email-verify
Уведомл:  E2 SMS-notify         E1 email-notify
Незав.:   A1 avatar (fix)   |   D Google OAuth   |   F дизайн-модель   |   G trust→JWT/перки
```

---

## Фаза 0 — параллельно, стартуем сразу

### F1 · Дизайн-модель «аккаунт + коммуникации» · [Claude + владелец] · design
Короткий док: как объединяются входы (email/Telegram/Google) в один аккаунт; флоу верификации (email/phone) → L0→L1; **матрица «событие → канал»** (Telegram привязан > SMS если phone verified > email). ⛔ Нужны решения владельца (см. «Открытые решения»). Блокирует детали C/D/E.

### A1 · Починка загрузки аватара · [Codex/front] · `frontend/web-app` (+ проверить media)
Симптом: фото не загружается. Разобрать: вызывается ли `POST /api/v1/media/upload` (multipart), возвращается ли `url`, идёт ли `PUT /me {avatarUrl}`, рендерится ли `avatarUrl`. Вероятно регрессия после #73 или CORS/размер. Файлы: `components/profile/*`, `api/media.ts`, `components/ui/UserAvatar.*`. **НЕ трогать** `api/auth.ts`/`authStore`. Бэкенд (media upload) готов — если окажется, что дело в media/gateway, эскалировать Claude.

### B2 · Проверка Email/SMTP · [Claude + владелец] · config
Gmail SMTP, env `MAIL_USERNAME`/`MAIL_PASSWORD` на **identity + notification** (EasyPanel). Проверить, что `MAIL_PASSWORD` = Gmail **App Password**. Тест: `POST /auth/password-reset/request` → инбокс + лог identity. Разблокирует C1, E1.

---

## Фаза 1 — каналы + независимый Google (параллельно)

### B1 · SMS-клиент Eskiz (доработка) · [Claude/back] · `services/notification-service` + `services/identity-service`
Eskiz на стороне провайдера готов. Сейчас `SmsService` — скелет (нет Bearer, нет refresh, JSON вместо form-data). Сделать: `login (email+pass) → токен → Bearer`, авто-refresh на 401, form-data + `from`. Вынести переиспользуемый SMS-клиент, чтобы identity мог слать OTP (identity без rabbit → свой клиент). Env: Eskiz creds в EasyPanel. Разблокирует C2, E2.

### D · Google OAuth · разбить на 2 PR:
- **D1 · backend** · [Claude/back] · `services/identity-service` — верификация Google ID-token (audience/issuer/exp), find-or-create по `google_sub` (+ email от Google → без placeholder), выдача JWT (L0). Миграция **identity V14** (`google_sub` UNIQUE + поля). Публичный `POST /api/v1/auth/google` + gateway OPEN_ENDPOINTS + route (по образцу telegram #67).
- **D2 · frontend** · [Codex/front] · кнопка Google Sign-In в `LoginCard` + `authStore.googleLogin` + `api/auth.ts`. ⚠️ трогает auth.ts/authStore/LoginCard — **сериализовать с другими auth-front задачами**. Стартовать после согласования формы payload с D1.

---

## Фаза 2 — верификация (после каналов)

### C1 · Email: СМЕНА + подтверждение · depends B2
Сейчас в профиле только подтверждение, **смены email нет**. Для Telegram-юзеров с placeholder `tg_*@topdim.uz` смена = задать реальную почту (правильный фикс placeholder-проблемы).
- backend [Claude] · `services/identity-service`: разрешить смену email в профиле — новый эндпоинт (напр. `POST /auth/email/change-request`) ИЛИ расширить `UpdateProfileRequest`: валидация формата + **уникальность** + при смене `email_verified=false` и отправка письма подтверждения на НОВЫЙ адрес; подтверждать через `/auth/confirm/email` → `email_verified=true` (+ вклад в L1). Логин по старому email — до подтверждения решить (см. решение №5).
- frontend [Codex]: в настройках профиля поле «изменить email» + статус (подтверждён/нет) + кнопка «подтвердить»; для placeholder-почты показывать «email не задан → добавить». (Экран подтверждения частично из #66.)

### C2 · Верификация телефона (OTP) · depends B1
- backend [Claude] · `services/identity-service`: `OtpService` (Redis: код+TTL+rate-limit), `POST /auth/phone/otp/request` + `/confirm` → `phone_verified=true` (+ L1). SMS через B1-клиент. Миграция при необходимости (**identity V15**).
- frontend [Codex]: ввод телефона + экран кода в профиле.

---

## Фаза 3 — уведомления + trust/перки (после каналов/верификации)

### E1 · Email-уведомления · [Claude/back] · `services/notification-service` · depends B2
События (`OrderCreated`/`PaymentCompleted`/`CouponPurchased`/…) → EmailService по матрице каналов (F1).

### E2 · SMS-уведомления · [Claude/back] · depends B1
То же через SMS; приоритет канала по F1 (Telegram > SMS > email).

### G · trust→JWT + гейтинг перков · [Claude/back]
`trustLevel` в JWT + проброс `X-User-Trust` (gateway); coupon/order проверяют L1 на перках. Завершает связку «верификация → ценность».

---

## Открытые решения владельца (для F1, разблокируют C/D/E)
1. **Верификация обязательна или опциональна?** Что именно открывает L1 (промокоды / скидка первого заказа / бесплатные купоны)?
2. **Матрица уведомлений:** какие события шлём и в какой канал; приоритет (Telegram > SMS > email?).
3. **Объединение аккаунтов:** Google/Telegram/email — один аккаунт по верифицированному email? Что если email от Google совпал с существующим?
4. **Email/SMTP** реально работает? (B2 проверит.)
5. **Смена email:** можно ли логиниться по старому email до подтверждения нового (или email становится «в ожидании» до клика по ссылке)? Как поступать с placeholder `tg_*@topdim.uz` — считать «email не задан» в UI, разрешать задать реальный?

## Порядок запуска (рекомендация)
- **Сейчас параллельно:** A1 (Codex, аватар) · B2 (проверка SMTP) · F1 (дизайн-модель + решения выше).
- **Дальше:** B1 (SMS-код) ‖ D1+D2 (Google).
- **Потом:** C1/C2 (верификация) → E1/E2 + G (уведомления/перки).
