# Dev Demo Seed — Руководство

## Что создаёт seed

| Сущность | Количество | База |
|---|---|---|
| Buyer users (роль USER) | 5 | identity |
| Owner users (роль PARTNER) | 50 | identity |
| Cashier users (роль PARTNER) | 100 | identity |
| Staff rows (cashier, с привязкой к филиалу) | 100 | identity |
| Merchants | 50 | coupon |
| Merchant locations (филиалы) | 80 | coupon |
| Coupon offers | 500 | coupon |
| — из них ACTIVE | 350 | coupon |
| — DRAFT | 50 | coupon |
| — PAUSED | 50 | coupon |
| — ARCHIVED | 50 | coupon |
| Coupon options | 1000 | coupon |
| Coupon images | 1250 | coupon |

## Маркер demo-данных

Все demo-данные маркируются email-доменом `@demo.topdim.uz`:
- Users: `owner001@demo.topdim.uz`, `cashier001a@demo.topdim.uz`, etc.
- Merchants: `merchant001@demo.topdim.uz`

Reset удаляет **только** данные с этим маркером.

## Предварительные требования

1. **Docker** — запущен PostgreSQL:
   ```bash
   docker compose up -d
   ```

2. **psql** — клиент PostgreSQL:
   ```bash
   brew install libpq
   export PATH="/usr/local/opt/libpq/bin:$PATH"
   ```
   Или использовать Docker-контейнер с postgres (скрипт определяет автоматически).

3. **Миграции** — должны быть применены. Запустите сервисы хотя бы 1 раз:
   ```bash
   ./gradlew :services:identity-service:bootRun   # запустить, дождаться старта, остановить
   ./gradlew :services:coupon-service:bootRun
   ```

## Запуск seed

```bash
# Seed (создание demo-данных)
./scripts/dev/seed-demo.sh

# Или явно
./scripts/dev/seed-demo.sh --seed
```

## Сброс demo-данных

```bash
./scripts/dev/seed-demo.sh --reset
```

Удаляет только данные с маркером `@demo.topdim.uz`.
Не трогает admin, production и ваши тестовые данные.

## Demo-логины

| Роль | Email | Пароль |
|---|---|---|
| Owner (партнёр) | `owner001@demo.topdim.uz` .. `owner050@demo.topdim.uz` | `Demo123!` |
| Cashier (кассир) | `cashier001a@demo.topdim.uz`, `cashier001b@demo.topdim.uz` .. `cashier050a/b` | `Demo123!` |
| Buyer (покупатель) | `buyer001@demo.topdim.uz` .. `buyer005@demo.topdim.uz` | `Demo123!` |

## QA-сценарии

### 1. Покупатель: просмотр каталога
1. Открыть `http://localhost:3000`
2. Войти как `buyer001@demo.topdim.uz` / `Demo123!`
3. Проверить: каталог показывает 350+ активных купонов
4. Карточки купонов с обложками, ценами, скидками

### 2. Покупатель: покупка
1. Выбрать купон → страница детали
2. Выбрать опцию → «В корзину» или «Купить сейчас»
3. Оформить заказ

### 3. Партнёр: дашборд
1. Открыть `http://localhost:3002`
2. Войти как `owner001@demo.topdim.uz` / `Demo123!`
3. Дашборд → KPI (купоны, продажи)
4. «Мои купоны» → 10 купонов мерчанта

### 4. Кассир: погашение
1. Открыть `http://localhost:3002`
2. Войти как `cashier001a@demo.topdim.uz` / `Demo123!`
3. «Погашение» → ввести PIN или отсканировать QR

### 5. Негативные проверки
- Кассир мерчанта 1 не может погасить купон мерчанта 2
- Owner видит только свои купоны
- Buyer не может войти в partner app

## Структура категорий (равномерное распределение)

| Slug | Мерчанты | Купонов | Активных |
|---|---|---|---|
| restaurants | 1-6 | 60 | 42 |
| coffee | 7-12 | 60 | 42 |
| beauty | 13-18 | 60 | 42 |
| spa | 19-23 | 50 | 35 |
| fitness | 24-29 | 60 | 42 |
| entertainment | 30-34 | 50 | 35 |
| education | 35-40 | 60 | 42 |
| services | 41-45 | 50 | 35 |
| shops | 46-50 | 50 | 35 |

## Технические детали

- **Подход:** SQL-скрипты под `scripts/dev/sql/`, оркестратор `scripts/dev/seed-demo.sh`
- **Идемпотентность:** `ON CONFLICT DO NOTHING` (users), `WHERE NOT EXISTS` (merchants, coupons, staff)
- **Cross-service linking:** оркестратор запрашивает user IDs из identity DB и merchant IDs из coupon DB, связывает через SQL UPDATE/INSERT
- **BCrypt hash:** генерирован через Spring `BCryptPasswordEncoder(10)`
- **Фото:** реальные фотографии через `picsum.photos` с детерминированными seed-значениями (каждый купон получает уникальное, но стабильное фото)
