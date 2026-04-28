# Полный план имплементации: окончательное разделение coupon / merchant / bazaar

## 1. Назначение документа

Этот документ предназначен для ИИ-исполнителя, который должен **довести текущее переходное состояние до полного разделения доменов**:

- `coupon` хранит только данные акции;
- `merchant` хранит профиль заведения;
- `merchant_locations` хранит адреса и контакты;
- `bazaar` и `shop` живут только в `bazaar-service`;
- `coupon-service` больше не содержит bazaar/directory домен;
- legacy-поля и переходные fallback-слои аккуратно удаляются без поломки текущего продукта.

Документ написан так, чтобы ИИ мог идти **строго по порядку**, с понятными stop-gates и критериями готовности на каждом этапе.

---

## 2. Источники истины

### Подтверждено кодом

Текущий `current state` нужно брать из:

- `services/coupon-service`
- `services/bazaar-service`
- `frontend/web-app`
- `frontend/admin-app`
- `infrastructure/api-gateway`

### Подтверждено документами

Для целевой модели и ранее зафиксированных решений нужно использовать:

- [coopon-merchant-db-refactor-plan.md](/Users/abror/Projects/copy-topdim-repo/topdim/docs/coopon-merchant-db-refactor-plan.md)
- [coupon-merchant-ai-execution-brief.md](/Users/abror/Projects/copy-topdim-repo/topdim/docs/coupon-merchant-ai-execution-brief.md)
- [coupon-merchant-phase1-completion-backlog.md](/Users/abror/Projects/copy-topdim-repo/topdim/docs/coupon-merchant-phase1-completion-backlog.md)

### Обязательное правило

Если есть конфликт между кодом и устаревшими общими markdown-документами, источником истины считать:

1. код;
2. этот implementation plan;
3. coupon-merchant refactor docs.

---

## 3. Что подтверждено кодом прямо сейчас

### 3.1. Что уже сделано

- `merchant_locations` уже существует и используется.
- `offer_description` уже существует и используется как canonical поле акции.
- storefront detail уже берет контакты из `merchant.primaryLocation`.
- bot lead flow уже переведен на:
  - `telegramChatId`
  - `merchant_locations.phone`
  - fallback по имени
- admin quick-create merchant уже умеет слать `locations[]`.
- live catalog search уже ищет по `offerDescription`.
- phone normalization уже вынесена в общий helper и применена в merchant flow.

### 3.2. Что еще остается переходным

- в `merchants` все еще лежат legacy поля:
  - `address`
  - `phone`
  - `working_hours`
- в `coupon_offers` все еще лежат legacy поля:
  - `short_description`
  - `full_description`
  - `terms`
  - `usage_rules`
  - `how_to_use`
  - `address`
  - `contact_phone`
  - `working_hours`
- backend DTO ответа и запроса все еще содержат backward-compatible legacy поля;
- сервисы купонов и мерчантов все еще поддерживают legacy fallback;
- `coupon-service` все еще содержит:
  - `Shop`
  - `Bazaar`
  - `DirectoryController`
  - `DirectoryService`
  - `BazaarRepository`
  - `ShopRepository`
- frontend bazaar/directory поток все еще идет через `/api/v1/directory/*`, хотя gateway и `bazaar-service` уже ориентированы на `/api/v1/bazaars/*` и `/api/v1/shops/*`.

### Вывод

Сейчас система находится в состоянии:

- **домен логически уже разделен частично**;
- **физическая БД и runtime contracts еще не очищены**;
- **bazaar-домен еще не выведен из coupon-service**.

---

## 4. Что считается “полностью реализовано”

Работа считается завершенной только когда одновременно выполняются все условия ниже.

### 4.1. Coupon domain

`coupon_offers` хранит только:

- идентичность оффера;
- связь с merchant и category;
- цены, скидку, сроки;
- статус и счетчики;
- `offer_description`.

В `coupon_offers` больше нет:

- `short_description`
- `full_description`
- `terms`
- `usage_rules`
- `how_to_use`
- `address`
- `contact_phone`
- `working_hours`

### 4.2. Merchant domain

`merchants` хранит только профиль заведения:

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

В `merchants` больше нет:

- `address`
- `phone`
- `working_hours`

### 4.3. Merchant contacts

Контакты и адреса хранятся только в `merchant_locations`.

### 4.4. API contract

Frontend больше не зависит от legacy coupon/merchant contact fields.

### 4.5. Bazaar isolation

`coupon-service` больше не содержит bazaar/directory сущности, контроллеры, сервисы и репозитории.

### 4.6. Frontend routing

Bazaar/storefront потоки идут в `bazaar-service`, а не в `coupon-service`.

---

## 5. Главные правила безопасности

ИИ обязан следовать этим правилам. Если хотя бы одно из них нарушено, реализация считается рискованной.

### 5.1. Не удалять schema раньше времени

Нельзя удалять legacy DB columns, пока:

- все write flows не пишут только canonical данные;
- все read flows не читают canonical данные;
- есть backfill и data audit;
- пройдены тесты и ручной smoke.

### 5.2. Не смешивать destructive cleanup и contract migration в одном шаге без gate

Удаление колонок и удаление API-полей должно происходить только после стабилизации потребителей.

### 5.3. Не удалять directory из coupon-service, пока bazaar-service не достиг функционального паритета

Если в `coupon-service` есть endpoint/flow, которого нет в `bazaar-service`, сначала нужно портировать его, а уже потом удалять.

### 5.4. Не ломать bot lead flow

`telegramChatId`, `phone` и `name` lookup должны оставаться рабочими на всех этапах.

### 5.5. Не делать silent behavior changes

Если поведение search, preview или phone matching меняется, оно должно быть:

- явно задокументировано;
- покрыто тестами;
- проверено вручную.

---

## 6. Рекомендуемая стратегия релизов

### Рекомендация

Не делать “большой взрыв” за один заход.

Безопасная последовательность:

1. `Release A` — закрыть все remaining read/write хвосты без удаления колонок.
2. `Release B` — перевести все runtime contracts на canonical-first режим.
3. `Release C` — физически очистить merchant/coupon legacy schema.
4. `Release D` — вывести bazaar/domain из `coupon-service` в `bazaar-service`.
5. `Release E` — финальная очистка контрактов, документации и техдолга.

---

## 7. Пошаговый implementation plan

Ниже шаги приведены в обязательном порядке.

---

## Step 0. Подготовка и safety net

### Цель

Подготовить базу для безопасного cleanup без скрытых регрессий.

### Что сделать

1. Собрать финальный список remaining legacy usage в коде.
2. Зафиксировать список таблиц и колонок, которые будут удаляться позже.
3. Подготовить regression matrix:
   - admin merchant create/edit
   - admin coupon create/edit
   - partner coupon create/edit
   - bot lead create
   - public catalog
   - coupon detail
   - coupon search
   - bazaar list/detail/search/map
4. Подготовить data audit queries:
   - merchants without primary location
   - active coupons without offer_description
   - active coupons still relying on legacy contact fields
   - counts of bazaar/shop entities in coupon-service vs bazaar-service
5. Подготовить rollback notes на каждый destructive этап.

### Задействованные файлы

- `docs/*`
- `services/coupon-service/src/main/resources/db/migration/*`
- `services/bazaar-service/src/main/resources/db/migration/*`, если миграции bazaar-service потребуются

### Done Criteria

- есть список всех remaining legacy точек;
- есть SQL-аудиты до удаления данных;
- есть ручной QA checklist;
- есть список тестов, которые нужно гонять после каждого релиза.

---

## Step 1. Завершить canonical read-model на всех оставшихся экранах и secondary flows

### Цель

Убедиться, что UI и secondary backend flows читают canonical модель, а не legacy поля.

### Подтверждено кодом

Остаточные legacy-read точки уже есть в:

- `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx`
- `services/coupon-service/src/main/java/uz/topdim/coupon/search/CouponSearchService.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/search/CouponSearchDocument.java`
- `frontend/web-app/src/api/coupons.ts`
- `frontend/web-app/src/components/coupon/CouponCard.tsx`
- `frontend/web-app/src/pages/HomePage.tsx`
- `frontend/web-app/src/pages/FavoritesPage.tsx`

### Что сделать

1. Перевести `MerchantReviewPage` на:
   - `offerDescription`
   - `merchant.primaryLocation`
   - merchant profile block
2. Перевести secondary search layer на `offerDescription`.
3. Привести search document и mappings к canonical полям.
4. Для карточек каталога и homepage определить единый preview strategy.

### Рекомендация по preview strategy

Не хранить `short_description` как source of truth в БД.

Безопасный вариант:

- хранить только `offer_description`;
- вычислять preview в backend read-model:
  - либо первая непустая строка;
  - либо ограниченный teaser extractor;
- временно можно оставлять `shortDescription` в response как derived field, если это нужно для текущего frontend.

### Done Criteria

- ни один экран не требует `coupon.address/contactPhone/workingHours`;
- moderation/review экран живет на canonical модели;
- secondary search индексирует `offerDescription`;
- storefront-card preview больше не требует physical `short_description` column как source of truth.

### Запрещено

- удалять legacy columns на этом шаге;
- оставлять review screen на старой модели;
- менять UI-контент silently без QA.

---

## Step 2. Перевести все write flows на canonical-only persistence

### Цель

Сделать так, чтобы runtime больше не записывал legacy merchant/coupon fields как рабочий источник истины.

### Подтверждено кодом

Сейчас transition-поведение еще видно в:

- `CouponOfferService.java`
- `PartnerCouponService.java`
- `MerchantService.java`
- `CreateCouponOfferRequest.java`
- `CreateMerchantRequest.java`

### Что сделать

1. В `CouponOfferService`:
   - продолжать принимать legacy input только как fallback на переходный период;
   - но записывать canonical смысл только в `offer_description`;
   - перестать записывать legacy coupon text fields как обязательную рабочую модель.
2. В `PartnerCouponService`:
   - сделать то же самое, что и в admin coupon flow;
   - убрать runtime-зависимость от legacy coupon text columns.
3. В `MerchantService`:
   - перестать считать merchant-level `address/phone/workingHours` каноническими;
   - принимать legacy input только для авто-сборки `primary location`;
   - все реальные контакты писать только в `merchant_locations`.
4. В bot lead flow:
   - сохранить lookup и lead creation;
   - не опираться на `merchant.phone` как каноническое поле.
5. Добавить/обновить тесты:
   - admin create/update coupon;
   - partner create/update coupon;
   - merchant create/update with locations;
   - bot lead create/reuse.

### Done Criteria

- новые купоны не зависят от legacy coupon columns;
- новые merchants не зависят от merchant-level contact columns;
- все create/update flows дают корректный canonical result;
- tests pass.

### Запрещено

- удалять request compatibility до завершения frontend migration;
- ломать partner flow;
- ломать bot flow.

---

## Step 3. Подготовить contract-cleanup без мгновенного breaking change

### Цель

Убрать риск поломки frontend и внутренних клиентов перед физическим DB cleanup.

### Что сделать

1. Перечислить все response/request DTO, где еще есть legacy поля:
   - `CouponOfferResponse`
   - `MerchantResponse`
   - `CreateCouponOfferRequest`
   - `CreateMerchantRequest`
2. Разделить поля на:
   - canonical;
   - derived temporary compatibility;
   - removable after frontend migration.
3. Если нужно сохранить совместимость на один релиз:
   - оставлять legacy response поля только как derived read-model;
   - не считать их source of truth.
4. Обновить frontend types и компоненты так, чтобы они опирались на canonical shape.
5. Убедиться, что admin app и web app больше не шлют legacy coupon data как основной input.

### Рекомендация

Самый безопасный вариант:

- на одном релизе перевести frontend на canonical поля;
- на следующем релизе удалить deprecated поля из API contract.

### Done Criteria

- frontend уже может работать без legacy response fields;
- request payloads canonical-first;
- compatibility слой локализован и понятен.

---

## Step 4. Физически очистить coupon / merchant schema

### Цель

Удалить legacy колонки из БД только после того, как продукт уже живет на canonical модели.

### Что удалить

#### Из `merchants`

- `address`
- `phone`
- `working_hours`

#### Из `coupon_offers`

- `short_description`
- `full_description`
- `terms`
- `usage_rules`
- `how_to_use`
- `address`
- `contact_phone`
- `working_hours`

### Что сделать перед удалением

1. Запустить data audit:
   - нет активных купонов без `offer_description`;
   - нет merchants, используемых купонами, без `primary location`;
   - нет write flows, которые все еще опираются на legacy columns.
2. Добавить pre-drop migration c проверками и, если нужно, дополнительным backfill.
3. Только потом добавить destructive migration на drop columns.

### Что сделать после удаления

1. Удалить поля из:
   - `Merchant.java`
   - `CouponOffer.java`
2. Удалить соответствующие legacy accessors и mapping code.
3. Удалить fallback logic, который читал legacy DB columns.
4. Обновить тесты.

### Done Criteria

- schema физически очищена;
- entities больше не содержат legacy fields;
- runtime не содержит fallback на удаленные колонки;
- все тесты проходят;
- ручной smoke пройден.

### Запрещено

- дропать колонки без предварительного audit;
- дропать колонки до удаления всех runtime зависимостей;
- смешивать этот шаг с bazaar extraction.

---

## Step 5. Довести bazaar-service до функционального паритета

### Цель

Подготовить реальный перенос directory/bazaar домена из `coupon-service` в уже существующий `bazaar-service`.

### Подтверждено кодом

Сейчас `bazaar-service` уже существует и обслуживает:

- `/api/v1/bazaars/**`
- `/api/v1/shops/**`
- `/api/v1/admin/bazaars/**`
- `/api/v1/admin/shops/**`
- `/api/v1/partner/shops/**`

Это подтверждается в:

- [application.yml](/Users/abror/Projects/copy-topdim-repo/topdim/infrastructure/api-gateway/src/main/resources/application.yml:83)
- `services/bazaar-service`

### Подтверждено кодом

При этом frontend и часть текущих flows все еще используют:

- `/api/v1/directory/*`

из `coupon-service`.

### Что нужно проверить на паритет

Сравнить `coupon-service DirectoryController/DirectoryService` и `bazaar-service BazaarController/ShopService`.

Обязательно проверить, есть ли в `bazaar-service` аналоги для:

- list bazaars;
- bazaar detail;
- bazaar shops;
- shop detail;
- shop search;
- admin create/update;
- partner shop flows;
- area search / bounding box search;
- standalone shops list, если frontend на это опирается.

### Что сделать

1. Составить матрицу parity:
   - endpoint в `coupon-service`
   - endpoint/flow в `bazaar-service`
   - статус: already covered / missing / incompatible
2. Перенести недостающие возможности в `bazaar-service`.
3. Переключить frontend `api/bazaars.ts` и связанные страницы на canonical bazaar-service contract.
4. При необходимости добавить temporary compatibility proxy или alias-route, но только как переходный этап.

### Done Criteria

- весь bazaar frontend использует bazaar-service;
- в bazaar-service есть функциональный паритет;
- ручной smoke по bazaar list/detail/search/shop/map проходит.

### Запрещено

- удалять directory из coupon-service до достижения паритета;
- переключать frontend раньше, чем bazaar-service закроет все используемые сценарии.

---

## Step 6. Удалить bazaar/domain из coupon-service

### Цель

Физически завершить доменное разделение сервисов.

### Что удалить из coupon-service

- `DirectoryController`
- `AdminDirectoryController`
- `DirectoryService`
- `Bazaar`
- `Shop`
- `BazaarRepository`
- `ShopRepository`
- DTO и response models, которые нужны только directory-domain
- миграции и schema artifacts, если они принадлежат coupon-service domain model

### Что проверить перед удалением

1. Frontend уже переключен на bazaar-service.
2. Gateway routes для bazaar уже актуальны.
3. Никакой код в coupon-service не импортирует directory сущности.
4. Нет integration tests, завязанных на старые directory endpoints.

### Что сделать после удаления

1. Почистить docs.
2. Почистить references в admin menu, frontend api clients и tests.
3. Убедиться, что `coupon-service` больше не содержит bazaar/domain imports.

### Done Criteria

- в `coupon-service` нет bazaar/domain кода;
- все bazaar routes обслуживаются из `bazaar-service`;
- сборка проходит;
- smoke tests проходят.

---

## Step 7. Финальный contract cleanup

### Цель

После DB cleanup и bazaar extraction закрыть оставшиеся temporary compatibility слои.

### Что сделать

1. Удалить deprecated legacy поля из:
   - `CouponOfferResponse`
   - `MerchantResponse`
   - `CreateCouponOfferRequest`
   - `CreateMerchantRequest`
2. Удалить derived compatibility mapping, если он больше не нужен.
3. Удалить устаревшие комментарии и fallback helpers.
4. Обновить документацию:
   - `DATABASE.md`
   - `API_CONTRACT.md`
   - `BACKEND.md`
   - `COUPON_CREATION_FLOW.md`

### Done Criteria

- контракт чистый и соответствует domain model;
- docs совпадают с кодом;
- в response/request DTO нет legacy полей, если они больше не нужны продукту.

---

## 8. Обязательный список файлов по релизам

Ниже минимальный ориентир для ИИ, какие зоны кода проверять на каждом этапе.

### Release A-B

- `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/search/CouponSearchService.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/search/CouponSearchDocument.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponOfferResponse.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateMerchantRequest.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantResponse.java`
- `frontend/admin-app/src/features/coupons/CouponFormPage.tsx`
- `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx`
- `frontend/web-app/src/api/coupons.ts`
- `frontend/web-app/src/components/coupon/CouponCard.tsx`
- `frontend/web-app/src/pages/CouponCatalogPage.tsx`
- `frontend/web-app/src/pages/CouponDetailPage.tsx`
- `frontend/web-app/src/pages/HomePage.tsx`
- `frontend/web-app/src/pages/FavoritesPage.tsx`

### Release C

- `services/coupon-service/src/main/java/uz/topdim/coupon/entity/Merchant.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponOffer.java`
- `services/coupon-service/src/main/resources/db/migration/*`
- все tests, покрывающие create/update/read flows

### Release D

- `services/coupon-service/src/main/java/uz/topdim/coupon/controller/DirectoryController.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminDirectoryController.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/service/DirectoryService.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/entity/Bazaar.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/entity/Shop.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/repository/BazaarRepository.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/repository/ShopRepository.java`
- `services/bazaar-service/src/main/java/**`
- `frontend/web-app/src/api/bazaars.ts`
- `frontend/web-app/src/pages/BazaarMapPage.tsx`
- `frontend/web-app/src/pages/BazaarDetailPage.tsx`
- `frontend/web-app/src/pages/ShopDetailPage.tsx`
- `frontend/web-app/src/pages/SearchPage.tsx`
- `infrastructure/api-gateway/src/main/resources/application.yml`   

---

## 9. Тесты и проверки после каждого релиза

### Автотесты

После каждого релиза ИИ обязан запускать релевантные тесты минимум по:

- `./gradlew :services:coupon-service:test`
- `./gradlew :services:bazaar-service:test`

Если менялся frontend, ИИ обязан запускать хотя бы сборку или релевантные frontend checks проекта.

### Ручной smoke

После каждого релиза нужно вручную проверить:

1. admin create merchant
2. admin create coupon
3. admin edit coupon
4. partner create/edit coupon
5. bot lead creation
6. catalog list
7. catalog search
8. coupon detail
9. bazaar list
10. bazaar detail
11. shop detail
12. bazaar search/map

---

## 10. Стоп-условия

ИИ должен остановиться и не продолжать destructive этап, если выполняется хотя бы одно условие:

- найден remaining frontend consumer, который зависит от legacy column как source of truth;
- `bazaar-service` не покрывает используемый directory endpoint;
- data audit показывает active rows без canonical data;
- tests не проходят;
- неясно, где живет реальный production contract для конкретного endpoint.

---

## 11. Рекомендуемый порядок выполнения для ИИ

Строго выполнять так:

1. `Step 0`
2. `Step 1`
3. `Step 2`
4. `Step 3`
5. `Step 4`
6. `Step 5`
7. `Step 6`
8. `Step 7`

Нельзя перепрыгивать сразу к `drop columns` или к `delete directory`.

---

## 12. Формат отчета после каждого релиза

ИИ должен завершать каждый релиз отчетом в формате:

### Что сделано

- какие backend файлы изменены;
- какие frontend файлы изменены;
- какие migrations добавлены;
- какие docs обновлены.

### Что проверено

- какие тесты запущены;
- какие manual smoke сценарии пройдены.

### Что осталось

- какие шаги следующего релиза еще не сделаны;
- какие risks остаются;
- какие compatibility слои пока еще сохранены.

---

## 13. Краткий итог

Чтобы “полностью разделить БД и домены” без поломки проекта, ИИ должен идти не через один большой cleanup, а через пять безопасных волн:

1. закрыть все remaining canonical read-flow хвосты;
2. остановить legacy writes;
3. стабилизировать contract migration;
4. удалить merchant/coupon legacy schema;
5. перенести directory domain полностью в `bazaar-service` и удалить его из `coupon-service`.

Только после этого модель можно считать действительно полностью разделенной.
