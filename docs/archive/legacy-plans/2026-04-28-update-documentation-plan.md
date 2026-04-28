# Обновление документации проекта и подготовка плана для QA

**Цель:** Актуализировать текущую документацию (`PRD.md`, `BACKEND.md`, `FRONTEND.md`) с учетом последних реализованных MVP фичей (онбординг партнеров, создание и апрув купонов, редизайн страницы купона, гостевой доступ). А также подготовить специализированный документ `QA_TEST_PLAN.md` для тестировщика.

**Текущее состояние продукта (что изменилось с последней редакции док):**
1. Реализован `partner-onboarding-mvp` (PartnerApplication, админское ревью, создание мерчанта и стаффа).
2. Реализован `partner-dashboard-lite-mvp` (простой дашборд для мерчанта в `admin-app` для просмотра статистики и купонов).
3. Реализован флоу работы с купонами: `partner-coupon-request-mvp` (мерчант создает заявку) и `partner-coupon-approval-mvp` (админ берет в работу, отправляет на согласование, мерчант апрувит или реджектит через Telegram Bot).
4. Реструктурирована страница `CouponDetailPage` на клиенте (убраны мок-данные, реализован новый layout).
5. Починен гостевой доступ (на уровне API Gateway и клиентских axios-интерцепторов).
6. Настроены порты и энвайронменты на production сервере (`minio`, `cors`).

## User Review Required

> [!IMPORTANT]
> Пожалуйста, проверьте этот план. Нужно ли добавить в документацию для тестировщика какие-то специфичные сценарии, о которых я мог не упомянуть (например, интеграция с внешними платежками, если она уже активна в демо-режиме)?

## Proposed Changes

### Документация продукта (PRD & Архитектура)

#### [MODIFY] [PRODUCT_REQUIREMENTS_DOCUMENT.md](file:///Users/abror/Projects/copy-topdim-repo/topdim/docs/PRODUCT_REQUIREMENTS_DOCUMENT.md)
- Обновить "Статус документа" на текущую дату и актуальную версию (v2).
- В разделе "Merchant contour" (6.3, 7.3, 8.3, 11) убрать метки "Риск" и "Предположение", так как теперь реализован `admin-app` дашборд для мерчанта и Telegram Bot для апрувов. Указать, что MVP кабинета партнера работает через портал администратора с ролью `PARTNER`.
- В разделе "Купонный контур" (10.10) актуализировать статусы: `LEAD → DRAFT → WAITING_FOR_MERCHANT → ACTIVE / REVISION_REQUESTED`. Описать, что мерчант подает заявку (LEAD), модератор правит (DRAFT), мерчант одобряет (Telegram Bot).
- В разделе "Guest mode" (9.4) закрепить, что гость может просматривать отзывы (исправлено в API Gateway) и не перенаправляется на `/login` агрессивно.

#### [MODIFY] [BACKEND.md](file:///Users/abror/Projects/copy-topdim-repo/topdim/docs/BACKEND.md)
- В `coupon-service` обновить раздел "Бизнес-логика" и "API Endpoints". Добавить/уточнить пути для `/api/v1/partner/coupons/requests` (подача заявки партнером).
- Уточнить роли (добавились `PARTNER`, `PARTNER_CASHIER` и их права в `identity-service`).

#### [MODIFY] [FRONTEND.md](file:///Users/abror/Projects/copy-topdim-repo/topdim/docs/FRONTEND.md)
- Добавить секцию про `admin-app` (админка), работающую на порту 3001, через которую заходят роли `ADMIN`, `MODERATOR` и `PARTNER`.
- Упомянуть про `CouponDetailPage` layout (Hero section, Info section, Variants).
- Уточнить работу Axios Interceptor (гостевой 401 не редиректит на логин, если токена не было).

---

### Документация для QA (Тестировщика)

#### [NEW] [QA_TEST_PLAN.md](file:///Users/abror/Projects/copy-topdim-repo/topdim/docs/QA_TEST_PLAN.md)
- Создать с нуля детальный тест-план для ручного QA-инженера.
- **Раздел 1: Подготовка окружения** (как залогиниться под разными ролями, где взять тестовые доступы).
- **Раздел 2: Основные E2E сценарии (Happy Paths)**:
  - Регистрация и гостевой просмотр купона.
  - Покупка купона пользователем (в демо-режиме платежей) и просмотр в профиле.
  - Погашение купона кассиром партнера.
- **Раздел 3: Partner Onboarding & Coupon Approval Flow**:
  - Партнер подает заявку на лендинге.
  - Админ одобряет заявку, создается аккаунт мерчанта.
  - Партнер логинится в `admin-app` и создает черновик купона (LEAD).
  - Модератор берет LEAD в работу, заполняет детали и переводит в `WAITING_FOR_MERCHANT`.
  - Партнер получает уведомление в Telegram Bot и нажимает "Одобрить" (`ACTIVE`).
- **Раздел 4: Negative & Edge Cases**:
  - Попытка покупки sold_out купона.
  - Доступ гостя к защищенным роутам профиля.
  - Попытка мерчанта отредактировать купон в статусе `ACTIVE`.

#### [MODIFY] [TESTING.md](file:///Users/abror/Projects/copy-topdim-repo/topdim/docs/TESTING.md)
- Добавить в конец ссылку на новый `QA_TEST_PLAN.md`, так как `TESTING.md` больше ориентирован на backend-разработчиков (Unit/Integration тесты).

## Verification Plan

### Manual Verification
- Показать пользователю `QA_TEST_PLAN.md`.
- Убедиться, что сборка MkDocs (если используется) или просто Markdown Preview не ломается.
