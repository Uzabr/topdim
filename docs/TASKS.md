# TopDim — Оставшиеся задачи

> Архитектурный аудит v2: **88%** соответствия. Осталось ~12%.

---

## Sprint 1 · Backend: Архитектурные исправления ✅

- [x] **1.1 Разделить shared БД** (topdim_user, topdim_payment)
- [x] **1.2 OpenFeign клиенты** (order→coupon, order→user, bazaar→coupon)
- [x] **1.3 Redis кэширование** (coupon cache + auth token blacklist)
- [ ] **1.4 Config Server подключение**
  - [ ] `spring.config.import` в каждом сервисе
  - [ ] Вынести DB credentials, JWT secret, RabbitMQ config

---

## Sprint 2 · Backend: Сущности и логика ✅

- [x] **2.1 MapStruct маpперы** (5 сервисов)
- [x] **2.2 Redemption** (entity + endpoint + Redemption record)
- [x] **2.3 RefundRequest** (entity + create/list/admin resolve)
- [x] **2.4 GlobalExceptionHandler** (payment, notification)
- [x] **2.5 Media Service — MinIO** (уже было)
- [x] **2.6 Notification** (EmailService + SmsService, stub mode)

### Оставшиеся backend задачи (из аудита v2):
- [ ] **2.7 Complaint entity** (user жалобы — из ER-диаграммы)
- [ ] **2.8 BazaarMap entity** (внутренняя карта базара)
- [ ] **2.9 GET /auth/verify** (подтверждение email/phone)
- [ ] **2.10 Payme/Click реальная интеграция**
- [ ] **2.11 Rate Limiting на Gateway** (Redis-based)

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

- [ ] **3.7 CSS Modules** (вместо plain CSS)

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

## Документация ✅

- [x] README.md (проект)
- [x] docs/BACKEND.md (бизнес-логика, API, архитектура)
- [x] docs/TASKS.md
- [x] docs/implementation_plan.md
- [x] README.md × 8 (каждый сервис)
- [x] Swagger UI (каждый сервис)

---

## Прогресс

| Область | Статус |
|---|---|
| Backend архитектура | **89%** ✅ |
| Backend стек | **88%** ✅ |
| Сервисы (функционал) | **90%** ✅ |
| Модель данных | **88%** ✅ |
| Документация | **100%** ✅ |
| Frontend | **50%** ⬜ |
| DevOps | **14%** ⬜ |
| Тесты | **0%** ⬜ |
| **ОБЩИЙ** | **88%** |
