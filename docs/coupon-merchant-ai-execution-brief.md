# AI Execution Brief: refactor coupon + merchant data model

## 1. Назначение документа

Этот документ предназначен для ИИ-исполнителя, который будет делать рефакторинг схемы данных, backend API и frontend под новую модель `coupon + merchant`.

Цель документа:

- не дать исполнителю пропустить скрытые зависимости;
- зафиксировать уже принятые решения;
- разбить работу на обязательные шаги;
- зафиксировать порядок выполнения;
- зафиксировать критерии готовности;
- зафиксировать, что нельзя делать “по-своему”.

Этот документ нужно использовать вместе с:

- [coupon-merchant-db-refactor-plan.md](/Users/abror/Projects/topdim/docs/research/coupon-merchant-db-refactor-plan.md)

Если есть конфликт между этим brief и устаревшими markdown-документами в репозитории, источником истины считать этот brief и актуальный refactor plan.

---

## 2. Frozen Decisions

### Подтверждено решением команды

Ниже решения уже приняты. ИИ не должен заново их обсуждать и не должен предлагать альтернативы без отдельного запроса:

1. `bazaar` и `coupon` — это разные продуктовые контуры.
2. `bazaar` не должен определять структуру `coupon` и `merchant`.
3. `coupon-service` в целевой модели не должен хранить bazaar/domain сущности.
4. `Shop` в `coupon-service` является legacy-остатком и подлежит удалению во втором релизе.
5. У мерчанта может быть несколько адресов и точек контакта.
6. Для этого вводится таблица `merchant_locations`.
7. На coupon detail в первом релизе показывается только один `primary` адрес мерчанта.
8. У купона остается одно большое текстовое поле `offer_description`.
9. Поля `short_description`, `terms`, `usage_rules`, `how_to_use` становятся legacy и должны быть свернуты в `offer_description`.
10. Контакты заведения не должны храниться в `coupon_offers`.
11. Блок “О заведении” должен строиться из `merchant`.
12. Bazaar/directory cleanup нужно делать вторым релизом, не в первом.

---

## 3. What Must Be True After Release 1

После Release 1 система должна удовлетворять всем правилам ниже:

1. `coupon_offers` больше не является source of truth для:
   - адреса;
   - телефона;
   - часов работы;
   - описания заведения.
2. Source of truth для данных о заведении:
   - `merchant`;
   - `merchant_locations`.
3. Source of truth для текста акции:
   - `coupon_offers.offer_description`.
4. `coupon detail` строится из двух разных блоков:
   - merchant block;
   - coupon offer block.
5. Старые колонки еще могут физически существовать в БД во время переходного релиза, но они не должны быть каноническим источником данных.
6. Bot lead flow не должен сломаться.
7. Admin coupon flow не должен сломаться.
8. Public catalog и coupon detail не должны сломаться.
9. Ни один существующий активный купон не должен потерять merchant linkage.

---

## 4. Explicit Non-Goals

ИИ не должен включать в первый релиз:

- удаление всего bazaar-кода из `coupon-service`;
- глубокий редизайн UI;
- изменение coupon lifecycle;
- изменение ролей и permission-модели;
- изменение checkout/order flow;
- изменение review domain beyond adaptation to new coupon response shape.

---

## 5. Hidden Dependencies That Must Not Be Missed

Это обязательный список скрытых зависимостей. Если исполнитель не проверил каждую из них, задача считается выполненной не полностью.

## 5.1. Telegram lead flow

Сейчас bot lead flow создает купон и ищет мерчанта по:

- `telegramChatId`
- `phone`

Задействованные файлы:

- [BotWebhookController.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/controller/BotWebhookController.java)
- [BotLeadRequest.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/dto/BotLeadRequest.java)
- [MerchantRepository.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantRepository.java)
- [CouponOfferService.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java)

### Обязательное правило

При переносе `phone` из merchant-level публичной модели нельзя сломать поиск существующего мерчанта по номеру телефона.

### Разрешенные варианты

1. Сохранить отдельный merchant-level operational contact phone.
2. Перевести lookup на `merchant_locations.phone`.

### Запрещено

- удалять phone-based lookup без замены;
- оставлять bot lead flow в нерабочем состоянии;
- silently менять поведение без тестов.

## 5.2. Admin coupon form

Сейчас admin coupon form:

- запрашивает список мерчантов;
- умеет быстро создать мерчанта;
- отправляет coupon DTO со старыми полями контактов.

Задействованный файл:

- [CouponFormPage.tsx](/Users/abror/Projects/topdim/frontend/admin-app/src/features/coupons/CouponFormPage.tsx)

### Обязательное правило

После refactor admin coupon form не должна отправлять:

- `address`
- `contactPhone`
- `workingHours`
- `shortDescription`
- `terms`
- `usageRules`
- `howToUse`

Если quick-create merchant flow остается, он должен быть адаптирован к новой merchant-модели.

## 5.3. Storefront coupon types

Текущая фронтовая типизация жестко ожидает legacy-поля.

Задействованный файл:

- [coupons.ts](/Users/abror/Projects/topdim/frontend/web-app/src/api/coupons.ts)

### Обязательное правило

После рефакторинга типы и компоненты должны работать на новой структуре ответа, а не на legacy shape.

## 5.4. Catalog card depends on address

Сейчас каталог использует coupon-level address.

Задействованные файлы:

- [CouponCatalogPage.tsx](/Users/abror/Projects/topdim/frontend/web-app/src/pages/CouponCatalogPage.tsx)
- [coupons.ts](/Users/abror/Projects/topdim/frontend/web-app/src/api/coupons.ts)

### Обязательное правило

Каталог должен начать использовать:

- `merchantPrimaryAddress` в read-model/API;
или
- `merchant.primaryLocation.address`

Но не `coupon.address`.

## 5.5. Gateway and route contracts

Directory cleanup во втором релизе потребует изменения gateway routes и docs.

Задействованный файл:

- [application.yml](/Users/abror/Projects/topdim/infrastructure/api-gateway/src/main/resources/application.yml)

### Обязательное правило

Во втором релизе нельзя удалить `DirectoryController`, не обновив gateway и документацию.

---

## 6. Target Data Model

## 6.1. Merchant

`merchant` хранит:

- `id`
- `name`
- `description`
- `logo_url`
- `cover_url`
- `email`
- `website`
- `contact_person`
- `active`
- `user_id`
- `telegram_chat_id`
- `created_at`
- `updated_at`

Дополнительно допускается отдельное техническое поле для operational contact, если это потребуется для bot lead flow.

## 6.2. MerchantLocation

Новая таблица `merchant_locations` хранит:

- `id`
- `merchant_id`
- `title`
- `address`
- `phone`
- `working_hours`
- `latitude`
- `longitude`
- `is_primary`
- `active`
- `created_at`
- `updated_at`

## 6.3. CouponOffer

`coupon_offers` хранит:

- `id`
- `merchant_id`
- `category_id`
- `title`
- `offer_description`
- `old_price`
- `from_price`
- `discount_percent`
- `cover_image_url`
- `buy_until`
- `use_until`
- `gift_available`
- `status`
- `assigned_moderator_id`
- `assigned_moderator_name`
- `revision_comment`
- `total_sold`
- `redeemed_count`
- `view_count`
- `total_turnover`
- `created_at`
- `updated_at`

## 6.4. CouponOption

Оставляется без концептуального изменения:

- `id`
- `coupon_offer_id`
- `title`
- `regular_price`
- `coupon_price`
- `quantity_limit`
- `quantity_sold`
- `status`

---

## 7. Legacy Fields That Must Be Removed From Canonical Model

## 7.1. Coupon legacy fields

Следующие поля должны быть выведены из canonical coupon model:

- `short_description`
- `full_description`
- `terms`
- `usage_rules`
- `how_to_use`
- `address`
- `contact_phone`
- `working_hours`

## 7.2. Merchant legacy fields

Если `merchant_locations` введена как canonical model для публичных контактов, из merchant canonical model должны быть выведены:

- `address`
- `phone`
- `working_hours`

---

## 8. Mandatory Migration Rules

Это один из самых важных разделов. ИИ не должен “просто добавить таблицу”. Он обязан сделать детерминированную миграцию данных.

## 8.1. Release 1 migration sequence

Порядок обязателен:

1. Добавить новые структуры.
2. Выполнить backfill.
3. Перевести backend на чтение новых структур.
4. Перевести frontend на чтение новых структур.
5. Только потом переводить новые записи на canonical write-path.
6. Только после стабилизации готовить cleanup.

## 8.2. Suggested Flyway split

Рекомендуемая разбивка migration-файлов:

1. `V13__create_merchant_locations_and_offer_description.sql`
2. `V14__backfill_merchant_locations_and_offer_description.sql`
3. `V15__cleanup_coupon_legacy_fields.sql` — только после стабилизации Release 1
4. `V16__remove_directory_tables_from_coupon_service.sql` — Release 2

Точные номера можно скорректировать, если в ветке появились новые migration files, но разделение по смыслу должно остаться.

## 8.3. Backfill rules for offer_description

`offer_description` не заполняется случайным конкатом.

Нужен детерминированный алгоритм:

1. Взять `short_description`, если оно есть.
2. Взять `full_description`, если оно есть.
3. Взять `terms`, если есть.
4. Взять `usage_rules`, если есть.
5. Взять `how_to_use`, если есть.
6. Собрать в один markdown-like текст в фиксированном порядке.

Рекомендуемый порядок блоков:

1. лид/короткое описание;
2. основное описание;
3. условия;
4. правила использования;
5. как использовать.

### Обязательное правило

Если какие-то части пустые, они пропускаются.

### Запрещено

- склеивать текст без разделителей;
- терять порядок смысловых блоков;
- silently отбрасывать non-null поля.

## 8.4. Backfill rules for merchant_locations

Нужно создать normalized location set по каждому мерчанту.

Алгоритм:

1. Взять merchant-level tuple:
   - `merchant.address`
   - `merchant.phone`
   - `merchant.working_hours`
2. Если tuple не пустой, создать первую location candidate.
3. Затем пройти по всем купонам этого мерчанта и собрать уникальные tuple:
   - `coupon.address`
   - `coupon.contact_phone`
   - `coupon.working_hours`
4. Нормализовать значения:
   - trim;
   - empty string -> null;
   - collapse repeated spaces;
   - телефон сравнивать в нормализованном виде, если в проекте уже есть helper; если нет, хотя бы trim и exact string.
5. Создать по одному `merchant_location` на каждый уникальный non-empty tuple.

### Правило выбора primary location

Использовать такой порядок:

1. Если merchant-level tuple существует, именно он становится `is_primary = true`.
2. Если merchant-level tuple пустой, primary становится earliest created tuple, найденный по купонам этого merchant.
3. У merchant должен остаться максимум один `primary` location.

### Обязательное правило

Нельзя терять coupon-level address tuples, если они содержат реальные данные и отличаются от merchant-level tuple.

### Запрещено

- создавать только один location и игнорировать остальные coupon-level адреса;
- создавать несколько `primary` locations у одного merchant;
- терять купонные адреса при backfill.

## 8.5. Publication safety rule

Поскольку coupon detail в первом релизе показывает один primary address, исполнитель обязан ввести validation rule:

- купон не должен становиться публично пригодным для показа на storefront без доступного `merchant.primaryLocation`.

Минимум нужно проверить это для перехода в customer-facing состояние.

Если lifecycle rules архитектурно мешают поставить hard-block именно на `ACTIVE`, нужно хотя бы:

- добавить явную backend validation;
или
- добавить админское предупреждение и regression-тест;
или
- документировать временное исключение.

Но пропускать этот вопрос нельзя.

---

## 9. API Contract That Must Exist After Release 1

Ниже не обязательно буквальный окончательный JSON, но смысловая структура должна быть именно такой.

## 9.1. Coupon detail response

`GET /api/v1/coupons/{id}` должен давать модель, в которой:

- offer data и merchant data разделены;
- merchant block достаточен для “О заведении” и “Контакты”;
- coupon block не содержит legacy contact fields.

Рекомендуемая форма:

```json
{
  "id": 123,
  "title": "Скидка 50% на SPA",
  "offerDescription": "Большой текст акции...",
  "oldPrice": 300000,
  "fromPrice": 150000,
  "discountPercent": 50,
  "coverImageUrl": "/media/...",
  "buyUntil": "2026-05-31T23:59:59",
  "useUntil": "2026-06-30T23:59:59",
  "giftAvailable": false,
  "status": "ACTIVE",
  "totalSold": 45,
  "redeemedCount": 10,
  "viewCount": 100,
  "averageRating": 4.7,
  "reviewCount": 18,
  "merchant": {
    "id": 7,
    "name": "SPA Oasis",
    "logoUrl": "/logo.jpg",
    "description": "Описание заведения",
    "primaryLocation": {
      "id": 55,
      "address": "Ташкент, ...",
      "phone": "+998...",
      "workingHours": "09:00-21:00"
    }
  },
  "options": [],
  "images": [],
  "createdAt": "2026-04-01T10:00:00"
}
```

## 9.2. Merchant admin response

`MerchantResponse` должен содержать locations, иначе admin UI не сможет управлять новой моделью.

Минимум нужно поддержать:

- `primaryLocation`
- `locations[]`

## 9.3. Merchant create/update request

`CreateMerchantRequest` больше не должен ограничиваться только merchant base fields.

Он должен поддержать location payload.

Минимально рекомендуемая структура:

```json
{
  "name": "SPA Oasis",
  "description": "Описание заведения",
  "logoUrl": "/logo.jpg",
  "coverUrl": "/cover.jpg",
  "email": "spa@test.com",
  "website": "https://spa.com",
  "contactPerson": "Алишер",
  "locations": [
    {
      "title": "Основной филиал",
      "address": "Ташкент, ...",
      "phone": "+998...",
      "workingHours": "09:00-21:00",
      "latitude": 41.31,
      "longitude": 69.27,
      "isPrimary": true
    }
  ]
}
```

---

## 10. Required Backend File Review List

ИИ-исполнитель обязан проверить и при необходимости обновить как минимум эти файлы.

## 10.1. Entities / DTOs / repositories

- [Merchant.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/entity/Merchant.java)
- [CouponOffer.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponOffer.java)
- [CreateMerchantRequest.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateMerchantRequest.java)
- [MerchantResponse.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantResponse.java)
- [CreateCouponOfferRequest.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java)
- [CouponOfferResponse.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponOfferResponse.java)
- [MerchantRepository.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantRepository.java)
- новый `MerchantLocation.java`
- новый `MerchantLocationRepository.java`

## 10.2. Services

- [MerchantService.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java)
- [CouponOfferService.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java)

## 10.3. Controllers

- [AdminCouponController.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java)
- [CouponController.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/controller/CouponController.java)
- [BotWebhookController.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/controller/BotWebhookController.java)

## 10.4. Legacy cleanup targets for Release 2

- [DirectoryController.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/controller/DirectoryController.java)
- [AdminDirectoryController.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminDirectoryController.java)
- [DirectoryService.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/service/DirectoryService.java)
- [Shop.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/entity/Shop.java)
- bazaar/shop DTOs and repositories in `coupon-service`
- [application.yml](/Users/abror/Projects/topdim/infrastructure/api-gateway/src/main/resources/application.yml)

---

## 11. Required Frontend File Review List

## 11.1. web-app

- [coupons.ts](/Users/abror/Projects/topdim/frontend/web-app/src/api/coupons.ts)
- [CouponDetailPage.tsx](/Users/abror/Projects/topdim/frontend/web-app/src/pages/CouponDetailPage.tsx)
- [CouponCatalogPage.tsx](/Users/abror/Projects/topdim/frontend/web-app/src/pages/CouponCatalogPage.tsx)

## 11.2. admin-app

- [CouponFormPage.tsx](/Users/abror/Projects/topdim/frontend/admin-app/src/features/coupons/CouponFormPage.tsx)
- [App.tsx](/Users/abror/Projects/topdim/frontend/admin-app/src/App.tsx) — если потребуется новый merchant management route

### Обязательное правило

Если отдельной merchant management формы сейчас нет, ИИ должен явно решить один из двух вариантов:

1. расширить существующий merchant create/edit API и UI;
2. создать отдельный merchant form flow.

Но нельзя оставить новую `merchant_locations` модель без UI-способа редактирования.

---

## 12. Release 1 Required Work Breakdown

Это обязательный порядок. Не перескакивать.

## 12.1. Step 1 — schema preparation

Сделать:

1. migration для `merchant_locations`;
2. migration для `offer_description`;
3. entity changes без cleanup legacy columns.

## 12.2. Step 2 — data backfill

Сделать:

1. заполнить `offer_description`;
2. создать `merchant_locations`;
3. проставить одному location `is_primary=true` на merchant.

## 12.3. Step 3 — backend dual-read

Сделать:

1. Coupon API читает уже новую модель;
2. Merchant API отдает locations;
3. fallback на legacy columns возможен только как временный read-compatibility слой;
4. новые записи должны уже писаться в новую модель.

## 12.4. Step 4 — admin UI adaptation

Сделать:

1. удалить legacy coupon contact fields из формы купона;
2. заменить раздельные текстовые поля купона на `offerDescription`;
3. обеспечить редактирование merchant locations.

## 12.5. Step 5 — storefront adaptation

Сделать:

1. coupon detail читает merchant block отдельно;
2. contacts tab использует `merchant.primaryLocation`;
3. coupon description block использует `offerDescription`;
4. catalog card использует merchant primary address, а не coupon address.

## 12.6. Step 6 — regression and tests

Сделать:

1. обновить unit tests;
2. добавить новые tests для migration-sensitive behavior;
3. вручную проверить coupon create/edit/detail/catalog.

---

## 13. Release 2 Required Work Breakdown

Release 2 нельзя начинать, пока Release 1 не стабилен.

## 13.1. Cleanup old coupon fields

Удалить:

- `short_description`
- `full_description`
- `terms`
- `usage_rules`
- `how_to_use`
- `address`
- `contact_phone`
- `working_hours`

## 13.2. Cleanup old merchant contact fields

Удалить merchant-level публичные contact columns, если новая модель `merchant_locations` уже используется везде.

## 13.3. Remove directory from coupon-service

Удалить:

- `DirectoryController`
- `AdminDirectoryController`
- `DirectoryService`
- bazaar/shop entities in `coupon-service`
- bazaar/shop migrations from active model
- gateway references
- docs references

### Важно

Удаление directory-кода допускается только если публичный bazaar flow уже полностью обслуживается `bazaar-service`.

---

## 14. Test Matrix — Mandatory

Исполнитель обязан покрыть минимум эти сценарии.

## 14.1. Merchant service tests

Обновить:

- [MerchantServiceTest.java](/Users/abror/Projects/topdim/services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java)

Проверить:

1. создание merchant с locations;
2. update merchant с locations;
3. выбор `primaryLocation`;
4. response shape содержит `primaryLocation` и `locations`.

## 14.2. Coupon service tests

Обновить:

- [CouponOfferServiceTest.java](/Users/abror/Projects/topdim/services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java)
- [CouponOfferServiceBusinessLogicTest.java](/Users/abror/Projects/topdim/services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceBusinessLogicTest.java)

Проверить:

1. create coupon пишет `offerDescription`, а не legacy text fields;
2. response не содержит coupon-level contact source usage;
3. coupon detail собирается из merchant + primaryLocation;
4. bot lead flow не ломается;
5. merchant lookup по телефону или его replacement работает.

## 14.3. Frontend regression checklist

Ручная проверка обязательна:

1. Создать merchant.
2. Добавить ему минимум одну primary location.
3. Создать coupon на этого merchant.
4. Убедиться, что coupon form больше не содержит контакты заведения.
5. Открыть coupon detail.
6. Убедиться, что:
   - “О заведении” берется из merchant description;
   - контакты берутся из merchant primary location;
   - offer text берется из `offerDescription`.
7. Открыть catalog.
8. Убедиться, что карточка использует адрес мерчанта, а не legacy coupon address.

## 14.4. Data QA checklist

Проверить на реальных данных:

1. у всех merchants после backfill есть 0..N locations;
2. у merchant с данными адреса есть ровно один primary location;
3. все уникальные coupon address tuples не потеряны;
4. `offer_description` не пустой там, где раньше были заполнены legacy text fields;
5. активные coupons не потеряли `merchant_id`.

---

## 15. Required Documentation Updates

После рефакторинга исполнитель обязан обновить документацию, иначе задача считается незавершенной.

Минимальный список:

- [DATABASE.md](/Users/abror/Projects/topdim/docs/DATABASE.md)
- [API_CONTRACT.md](/Users/abror/Projects/topdim/docs/API_CONTRACT.md)
- [BACKEND.md](/Users/abror/Projects/topdim/docs/BACKEND.md)
- [COUPON_CREATION_FLOW.md](/Users/abror/Projects/topdim/docs/COUPON_CREATION_FLOW.md)

Во втором релизе дополнительно:

- docs, где `coupon-service` до сих пор описывается как owner directory API;
- docs, где остались legacy coupon text/contact fields.

---

## 16. Banned Shortcuts

Исполнителю запрещено:

1. Удалять legacy columns в том же шаге, где только добавляется новая модель.
2. Переносить данные без deterministic backfill rules.
3. Игнорировать bot lead flow.
4. Игнорировать catalog address dependency.
5. Оставлять frontend типы в legacy shape после смены backend contract.
6. Не обновлять тесты.
7. Не обновлять документацию.
8. Молча терять coupon-level address data.
9. Делать “temporary” решение, в котором coupon detail по-прежнему secretly зависит от `coupon.address`.
10. Удалять directory routes из gateway до переноса bazaar flow.

---

## 17. Done Criteria

Задача считается выполненной только если одновременно выполнены все условия:

1. Новая схема `merchant + merchant_locations + coupon.offer_description` внедрена.
2. Backend API отдает merchant block и primaryLocation.
3. Coupon detail больше не зависит от coupon-level contacts.
4. Admin coupon form больше не редактирует контакты заведения.
5. Merchant UI/API поддерживает locations.
6. Bot lead flow сохранен.
7. Catalog не использует coupon address как source of truth.
8. Tests updated and green.
9. Docs updated.
10. Cleanup второго релиза описан и отделен от первого релиза.

---

## 18. Execution Note

Если во время реализации исполнитель обнаружит дополнительные legacy-зависимости, он обязан:

1. зафиксировать их в рабочем отчете;
2. обновить этот brief или refactor plan;
3. не обходить проблему молча временным хаком.

Цель этой задачи — не просто “починить схему”, а перевести систему на новую каноническую модель без скрытых источников истины.
