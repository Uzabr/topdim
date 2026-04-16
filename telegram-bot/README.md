# Coupon Concierge Telegram Bot

Telegram bot для сценария "консьерж-сервис" в Topdim.

На текущем backend-срезе от `2026-04-12` бот интегрирован с существующими сервисами так:

- onboarding партнера сохраняется через `user-service` как `partner application`;
- merchant approval/revision работает через готовые bot endpoints в `coupon-service`;
- preview карточки приходит во входящий webhook самого бота.

## Что умеет бот

### 1. Onboarding партнера

FSM-состояния:

- `WAIT_FOR_CONTACT`
- `WAIT_FOR_LINK`
- `WAIT_FOR_PROMO_DETAILS`
- `WAIT_FOR_REVISION`

Флоу:

1. `/start` показывает кнопку `📱 Поделиться контактом`
2. бот принимает `contact`
3. бот запрашивает ссылку на Instagram / Telegram / сайт
4. бот принимает описание акции текстом или voice
5. бот отправляет данные в `user-service` как публичную заявку партнера

Важно: в текущем backend нет отдельного endpoint-а вида `POST /api/v1/bot/merchants/auth` и нет отдельного `POST /api/v1/bot/leads`.

Поэтому бот адаптирован к существующему публичному endpoint:

- `POST /api/v1/partners/applications`

При этом:

- `companyName` выводится из ссылки партнера;
- ссылка, детали акции, `telegramChatId`, `telegramUserId`, `telegramUsername` и voice metadata уходят в поле `comment`.

### 2. Approval flow

Для согласования купона используется готовый backend endpoint в `coupon-service`:

- `POST /api/v1/bot/coupons/{id}/approve`
- `POST /api/v1/bot/coupons/{id}/reject`

Бот:

1. принимает входящий preview webhook;
2. отправляет `sendPhoto` с caption;
3. показывает inline-кнопки approve / reject;
4. по approve вызывает backend;
5. по reject переводит пользователя в FSM и отправляет комментарий на доработку.

## Архитектурная особенность вашего backend

Сейчас bot-интеграция должна ходить **напрямую в сервисы**, а не через API Gateway:

- `user-service` по умолчанию: `http://localhost:8082`
- `coupon-service` по умолчанию: `http://localhost:8083`

Причина:

- в `api-gateway` сейчас нет маршрута для `/api/v1/bot/**`
- в `api-gateway` сейчас нет маршрута для `/api/v1/partners/applications`

То есть `http://localhost:8080` для этих сценариев использовать нельзя, пока gateway не будет расширен.

## Структура проекта

```text
.
├── .env.example
├── package.json
├── README.md
└── src
    ├── bot
    │   ├── keyboards.js
    │   ├── states.js
    │   ├── text.js
    │   └── update-handler.js
    ├── clients
    │   ├── backend-client.js
    │   └── telegram-client.js
    ├── config.js
    ├── index.js
    ├── server.js
    ├── store
    │   └── session-store.js
    └── utils
        └── http.js
```

## Переменные окружения

| Переменная | Обязательна | Описание |
| --- | --- | --- |
| `PORT` | нет | HTTP-порт bot-service, по умолчанию `3000` |
| `NODE_ENV` | нет | Среда запуска |
| `TELEGRAM_BOT_TOKEN` | да | Токен Telegram-бота |
| `TELEGRAM_WEBHOOK_URL` | нет | Публичный URL для регистрации webhook в Telegram |
| `TELEGRAM_WEBHOOK_SECRET` | да | Ожидается в `X-Telegram-Bot-Api-Secret-Token` |
| `USER_SERVICE_BASE_URL` | да | Базовый URL `user-service`, обычно `http://localhost:8082` |
| `COUPON_SERVICE_BASE_URL` | да | Базовый URL `coupon-service`, обычно `http://localhost:8083` |
| `COUPON_SERVICE_BOT_API_KEY` | нет, но рекомендуется | Значение `BOT_API_KEY` для `coupon-service`, отправляется как `X-Bot-Api-Key` |
| `BACKEND_TIMEOUT_MS` | нет | Таймаут REST-вызовов к backend, по умолчанию `10000` |
| `PREVIEW_WEBHOOK_TOKEN` | да | Секрет для `POST /webhook/preview`, ожидается в `X-Webhook-Token` |
| `SESSION_TTL_MINUTES` | нет | TTL in-memory FSM-сессии, по умолчанию `60` |

## HTTP endpoints bot-service

### `GET /health`

Ответ:

```json
{
  "ok": true
}
```

### `POST /webhooks/telegram`

Входящий webhook Telegram.

Обязательный заголовок:

```text
X-Telegram-Bot-Api-Secret-Token: <TELEGRAM_WEBHOOK_SECRET>
```

### `POST /webhook/preview`

Входящий webhook от Java backend, когда карточка купона готова к согласованию.

Обязательный заголовок:

```text
X-Webhook-Token: <PREVIEW_WEBHOOK_TOKEN>
```

Поддерживаются два варианта payload.

#### Вариант A: custom preview payload

```json
{
  "chat_id": 123456789,
  "coupon_id": 991,
  "cover_image_url": "https://cdn.example.com/coupons/991-cover.jpg",
  "title": "Сет роллов -30%",
  "old_price": "120 000 сум",
  "new_price": "84 000 сум",
  "description": "Действует ежедневно с 12:00 до 22:00. Количество ограничено."
}
```

#### Вариант B: payload, близкий к `CouponOfferResponse`

```json
{
  "chatId": 123456789,
  "data": {
    "id": 991,
    "title": "Сет роллов -30%",
    "shortDescription": "Действует ежедневно с 12:00 до 22:00",
    "oldPrice": 120000,
    "fromPrice": 84000,
    "coverImageUrl": "https://cdn.example.com/coupons/991-cover.jpg"
  }
}
```

Успешный ответ:

```json
{
  "ok": true
}
```

## Реальные backend-контракты, которые использует бот

### 1. Onboarding → `user-service`

`POST /api/v1/partners/applications`

Request:

```json
{
  "firstName": "Abror",
  "lastName": "Karimov",
  "phone": "+998901234567",
  "companyName": "Instagram @topdim_sushi",
  "comment": "Источник: Telegram bot concierge\nСсылка партнера: https://instagram.com/topdim_sushi\nTelegram chat id: 123456789\nTelegram user id: 123456789\nTelegram username: abror_dev\n\nОписание акции:\nСкидка 20% на все роллы по будням после 18:00"
}
```

Response:

```json
{
  "success": true,
  "message": "Заявка успешно отправлена. Мы свяжемся с вами в ближайшее время.",
  "data": {
    "id": 555,
    "firstName": "Abror",
    "lastName": "Karimov",
    "phone": "+998901234567",
    "companyName": "Instagram @topdim_sushi",
    "comment": "..."
  }
}
```

Если партнер прислал voice, бот добавляет в `comment` его Telegram metadata:

```text
Голосовое сообщение:
fileId=...
fileUniqueId=...
durationSeconds=24
mimeType=audio/ogg
```

### 2. Approve → `coupon-service`

`POST /api/v1/bot/coupons/{couponId}/approve`

Header:

```text
X-Bot-Api-Key: <COUPON_SERVICE_BOT_API_KEY>
```

Response:

```json
{
  "success": true,
  "message": "Купон одобрен мерчантом",
  "data": {
    "id": 991,
    "status": "ACTIVE",
    "revisionComment": null
  }
}
```

### 3. Reject → `coupon-service`

`POST /api/v1/bot/coupons/{couponId}/reject`

Header:

```text
X-Bot-Api-Key: <COUPON_SERVICE_BOT_API_KEY>
```

Request:

```json
{
  "comment": "Поменяйте, пожалуйста, фото и укажите цену 79 000 сум."
}
```

Response:

```json
{
  "success": true,
  "message": "Купон возвращён на доработку",
  "data": {
    "id": 991,
    "status": "REVISION_REQUESTED",
    "revisionComment": "Поменяйте, пожалуйста, фото и укажите цену 79 000 сум."
  }
}
```

## Что важно знать backend-команде

Для полной автоматики в будущем желательно добавить отдельные bot endpoints:

- `POST /api/v1/bot/merchants/auth`
- `POST /api/v1/bot/leads`

И желательно хранить явно:

- `telegramChatId`
- `telegramUserId`
- `telegramUsername`
- `sourceLink`
- structured voice metadata

Сейчас это временно упаковывается в `partner_applications.comment`, потому что в текущей модели `PartnerApplication` нет отдельных полей для Telegram-интеграции.

## Запуск

1. Скопировать `.env.example` в `.env`
2. Заполнить переменные окружения
3. Запустить `npm start`
4. Настроить Telegram webhook на `POST /webhooks/telegram`

Если задан `TELEGRAM_WEBHOOK_URL`, сервис сам вызовет `setWebhook` при старте.

## Production notes

- Сейчас используется `InMemorySessionStore`; для нескольких инстансов лучше заменить на Redis.
- Approval flow уже адаптирован к реальному `coupon-service`.
- Onboarding сейчас адаптирован через `user-service` без изменений backend-кода.
- Если захотите, следующим шагом можно сделать полноценный backend bot module с dedicated DTO и webhook orchestration.
