# TopDim — Оставшиеся задачи

> По результатам архитектурного аудита (72% ready). Всего ~6 спринтов.

---

## Sprint 1 · Backend: Архитектурные исправления (2-3 дня)

- [x] **1.1 Разделить shared БД**
  - [x] Создать `topdim_user` БД, перенести user-service
  - [x] Создать `topdim_payment` БД, перенести payment-service
  - [x] Обновить `docker-compose.yml` (init скрипты для новых БД)
  - [x] Убрать Flyway workarounds (baseline-on-migrate)

- [x] **1.2 OpenFeign клиенты** (межсервисные вызовы)
  - [x] `CouponClient` в order-service → getCouponOption(id)
  - [x] `UserClient` в order-service → getUserById(id)
  - [x] `CouponClient` в bazaar-service → getCouponOfferById(id)

- [x] **1.3 Redis кэширование**
  - [x] RedisConfig в coupon-service
  - [x] `@Cacheable` на getCatalog, getCategories, getTopSelling
  - [x] `@CacheEvict` при создании/обновлении купона
  - [x] RedisConfig в auth-service (token blacklist)

- [ ] **1.4 Config Server подключение**
  - [ ] Общий `application.yml` в config-server Git repo
  - [ ] `spring.config.import` в каждом сервисе
  - [ ] Вынести DB credentials, JWT secret, RabbitMQ config

---

## Sprint 2 · Backend: Недостающие сущности и логика (2-3 дня)

- [x] **2.1 MapStruct маpперы**
  - [x] CouponMapper, MerchantMapper, CategoryMapper (coupon-service)
  - [x] OrderMapper (order-service)
  - [x] BazaarMapper, ShopMapper (bazaar-service)
  - [x] UserMapper (user-service)
  - [x] PaymentMapper (payment-service)

- [x] **2.2 Redemption (погашение купона)**
  - [x] Entity: `Redemption` (order-service)
  - [x] Redemption создаётся при redeemCoupon
  - [x] Endpoint: `POST /api/v1/orders/redeem`
  - [x] Endpoint: `GET /api/v1/orders/{id}/coupons`

- [x] **2.3 RefundRequest**
  - [x] Entity + Repository (order-service)
  - [x] Endpoints: create, list, approve/reject (admin)

- [x] **2.4 GlobalExceptionHandler**
  - [x] Добавить в payment-service, notification-service
  - [x] Единый формат ошибок через `ApiResponse`

- [x] **2.5 Media Service — MinIO интеграция** (уже было реализовано)
  - [x] MinIO client config
  - [x] Upload endpoint (multipart)
  - [x] Download / serve endpoint
  - [x] Delete endpoint

- [x] **2.6 Notification Service — реальная отправка**
  - [x] Email через SMTP (stub mode по умолчанию)
  - [x] SMS через Eskiz.uz API (stub mode по умолчанию)

---

## Sprint 3 · Frontend: Полировка (2-3 дня)

- [ ] **3.1 UI компоненты** (`components/ui/`)
  - [ ] Button, Card, Modal, Input, Spinner, Badge, Toast

- [ ] **3.2 Bazaar компоненты** (`components/bazaar/`)
  - [ ] ShopCard, BazaarMarker, InteriorMap (SVG)

- [ ] **3.3 i18n (ru / uz)**
  - [ ] Настроить react-i18next
  - [ ] `i18n/ru.json`, `i18n/uz.json`
  - [ ] Переключатель языка в Header

- [ ] **3.4 React Hook Form + Zod**
  - [ ] LoginPage, CheckoutPage, ProfilePage

- [ ] **3.5 Внутренняя карта базара** (SVG/Canvas)

- [ ] **3.6 LoginPage** — register tab, OAuth, forgot password

---

## Sprint 4 · Admin Panel (3-5 дней)

- [ ] **4.1 Инициализация** `frontend/admin-app/` (Vite + React)
- [ ] **4.2 CRUD** — купоны, партнёры, категории, базары, магазины, заказы
- [ ] **4.3 Dashboard** — аналитика + графики (Recharts)

---

## Sprint 5 · DevOps (2-3 дня)

- [ ] **5.1 Dockerfiles** (multi-stage для Java, nginx для frontend)
- [ ] **5.2 docker-compose.prod.yml** + nginx + SSL + healthchecks
- [ ] **5.3 CI/CD** — GitHub Actions (build → test → deploy)
- [ ] **5.4 Monitoring** — Prometheus + Grafana + Actuator
- [ ] **5.5 Logging** — Logback JSON + ELK/Loki

---

## Sprint 6 · Тестирование (3-5 дней)

- [ ] **6.1 Unit** — JUnit 5 + Mockito (все сервисы)
- [ ] **6.2 Integration** — Testcontainers + PostgreSQL
- [ ] **6.3 E2E** — Playwright (основные flows)

---

## Оценка

| Sprint | Оценка | Приоритет |
|---|---|---|
| 1 Backend фиксы | 2-3 дня | 🔴 Критический |
| 2 Сущности + логика | 2-3 дня | 🔴 Критический |
| 3 Frontend полировка | 2-3 дня | 🟡 Высокий |
| 4 Admin Panel | 3-5 дней | 🟡 Высокий |
| 5 DevOps | 2-3 дня | 🟢 Средний |
| 6 Тесты | 3-5 дней | 🟢 Средний |
| **Итого** | **~15-22 дня** | |
