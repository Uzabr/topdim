# Backlog закрытия Phase 1: coupon + merchant refactor

## 1. Назначение

Этот документ фиксирует, что после ветки `backend/refactoring-db` уже сделано, а что еще **обязательно** нужно добить, чтобы Release 1 считался завершенным.

Документ предназначен для ИИ-исполнителя.

Его нужно использовать вместе с:

- [coupon-merchant-db-refactor-plan.md](/Users/abror/Projects/topdim/docs/coupon-merchant-db-refactor-plan.md)
- [coupon-merchant-ai-execution-brief.md](/Users/abror/Projects/topdim/docs/coupon-merchant-ai-execution-brief.md)

Если есть конфликт:

- `current state` брать из кода;
- `target state` брать из refactor plan и execution brief;
- этот backlog использовать как список **незакрытых хвостов Release 1**.

---

## 2. Что уже сделано в Phase 1

### Подтверждено кодом

В ветке `backend/refactoring-db` уже реализованы:

- миграции:
  - `V13__create_merchant_locations_and_offer_description.sql`
  - `V14__backfill_merchant_locations_and_offer_description.sql`
- новая сущность `MerchantLocation`;
- новый `MerchantLocationRepository`;
- canonical поле `coupon_offers.offer_description`;
- `CouponDetailPage` переведен на `offerDescription + merchant.primaryLocation`;
- каталог купонов перестал брать адрес из `coupon.address`;
- DTO ответа купона и мерчанта уже умеют `primaryLocation` и `locations`;
- backend и frontend уже частично поддерживают новую модель;
- часть тестов обновлена.

### Вывод

База и основной read-model уже сдвинуты в правильную сторону.

Но Release 1 еще нельзя считать полностью завершенным, потому что часть write-flow и часть secondary read-flow все еще завязаны на legacy-модель.

---

## 3. Что обязательно нужно сделать сейчас

Ниже перечислены задачи, которые нужно закрыть **сейчас**, до перехода к Release 2.

---

## Task 1. Довести до конца bot lead flow под merchant_locations

### Приоритет

`P1`

### Почему это важно

Сейчас bot lead flow все еще использует legacy merchant-level phone lookup и частично пишет контакты в legacy-поля.

После введения `merchant_locations` это создает расхождение между:

- canonical contact model;
- operational flow создания лидов из Telegram.

### Зафиксированное продуктовое правило

Для поиска существующего мерчанта в bot lead flow нужно поддержать поиск:

1. по `telegramChatId`, если он есть;
2. по номеру телефона;
3. по имени мерчанта.

### Обязательное уточнение для реализации

Имя нельзя использовать для безусловного auto-merge без проверки уникальности.

Безопасное правило для Release 1:

1. сначала искать мерчанта по `telegramChatId`;
2. потом искать по нормализованному `phone` в `merchant_locations`;
3. потом искать по точному нормализованному имени;
4. если name-match не уникален, не делать auto-link молча:
   - либо создать нового merchant lead;
   - либо оставить след для ручной проверки;
   - но не привязывать к произвольному существующему мерчанту.

### Что сейчас неправильно

- lookup по телефону идет через `merchant.phone`, а не через `merchant_locations.phone`;
- lead flow все еще частично опирается на legacy merchant contact model;
- это противоречит новой canonical схеме.

### Задействованные файлы

- [MerchantRepository.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantRepository.java)
- [MerchantLocationRepository.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantLocationRepository.java)
- [CouponOfferService.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java)
- [Merchant.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/entity/Merchant.java)
- [MerchantLocation.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/entity/MerchantLocation.java)
- [CouponOfferServiceTest.java](/Users/abror/Projects/topdim/services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java)
- [CouponOfferServiceBusinessLogicTest.java](/Users/abror/Projects/topdim/services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceBusinessLogicTest.java)

### Что нужно сделать

1. Перевести phone lookup на `merchant_locations`.
2. Добавить name lookup по зафиксированному правилу.
3. Нормализовать номер телефона и имя перед поиском.
4. Проверить, где создается новый merchant в lead flow, и гарантировать, что у нового мерчанта создается `primary location`, если пришли телефон/адрес/часы работы.
5. Убедиться, что `telegramChatId` flow не сломан.
6. Не сломать создание `LEAD` купона.

### Acceptance Criteria

- существующий merchant находится по `telegramChatId`;
- если `telegramChatId` нет, merchant ищется по телефону в `merchant_locations`;
- если phone не дал матч, выполняется поиск по имени;
- при нескольких совпадениях по имени auto-link не происходит молча;
- новый lead не теряет номер телефона;
- canonical location data сохраняется в `merchant_locations`;
- тесты покрывают:
  - match by telegram chat id;
  - match by phone;
  - unique match by name;
  - ambiguous match by name;
  - create new merchant lead.

### Запрещено

- оставлять phone lookup только в `merchant.phone`;
- silently merge по неуникальному имени;
- ломать bot webhook API;
- менять lifecycle купона.

---

## Task 2. Довести admin write-flow до merchant_locations

### Приоритет

`P1`

### Почему это важно

Сейчас backend уже умеет `locations[]`, но admin quick-create merchant flow все еще пишет только legacy `address/phone/workingHours`.

Это означает:

- новая data model уже есть;
- UI записи данных еще живет по старой схеме.

### Что сейчас неправильно

В админке быстрый create merchant по-прежнему отправляет плоский payload вместо normalized location model.

### Задействованные файлы

- [CouponFormPage.tsx](/Users/abror/Projects/topdim/frontend/admin-app/src/features/coupons/CouponFormPage.tsx)
- [CreateMerchantRequest.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateMerchantRequest.java)
- [MerchantService.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java)
- [MerchantResponse.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantResponse.java)
- [MerchantServiceTest.java](/Users/abror/Projects/topdim/services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java)

### Что нужно сделать

1. Адаптировать quick-create merchant modal под новую модель.
2. Отправлять `locations[]` с одним `primary` location.
3. Сохранить совместимость с текущим UX:
   - админ быстро создает мерчанта;
   - новый merchant сразу выбирается в coupon form.
4. Проверить edit/create сценарии, если coupon form использует response shape мерчанта.
5. Убедиться, что после создания мерчанта UI получает `primaryLocation`.

### Acceptance Criteria

- quick-create merchant создает merchant c `locations[0].isPrimary = true`;
- адрес, телефон и часы работы попадают в `merchant_locations`, а не только в legacy merchant fields;
- после создания merchant сразу доступен для выбора в coupon form;
- UI не ломается, если у merchant есть `primaryLocation`;
- тесты и/или ручная проверка подтверждают, что merchant создается корректно.

### Запрещено

- оставлять admin quick-create только на legacy payload;
- ломать создание купона после создания нового merchant;
- удалять legacy поддержку из backend в этом таске, если она еще нужна для переходного релиза.

---

## Task 3. Добавить offerDescription в реальный поиск каталога

### Приоритет

`P1`

### Почему это важно

После Phase 1 главным текстовым полем купона становится `offerDescription`.

Но живой поиск каталога сейчас ищет только по:

- `title`
- `shortDescription`

Это означает, что пользователь не найдет купон по основному описанию акции, даже если данные уже корректно сохранены.

### Важное уточнение

Этот таск относится к **реальному catalog search**, а не к будущему Elasticsearch cleanup.

На текущий момент live search идет через `CouponOfferRepository.searchByTitleOrDescription(...)`.

### Задействованные файлы

- [CouponOfferRepository.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferRepository.java)
- [CouponOfferService.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java)
- [frontend/web-app/src/api/coupons.ts](/Users/abror/Projects/topdim/frontend/web-app/src/api/coupons.ts)
- [CouponCatalogPage.tsx](/Users/abror/Projects/topdim/frontend/web-app/src/pages/CouponCatalogPage.tsx)
- релевантные backend tests для catalog search, если они есть

### Что нужно сделать

1. Обновить репозиторный поиск так, чтобы он искал по:
   - `title`
   - `shortDescription` как legacy fallback
   - `offerDescription` как canonical field
2. Убедиться, что поиск не ломает текущую пагинацию и сортировку.
3. Проверить, что пустой search path работает как раньше.
4. При необходимости обновить тесты на поиск.

### Acceptance Criteria

- пользователь может найти купон по словам, содержащимся только в `offerDescription`;
- старый поиск по `title` не ломается;
- поиск по legacy `shortDescription` не ломается;
- каталог продолжает работать с пагинацией и сортировкой;
- нет регрессии в `/api/v1/coupons`.

### Запрещено

- ограничиться только правкой `CouponSearchService`, если live catalog search все еще идет через JPA query;
- ломать response shape каталога;
- убирать `shortDescription` из backward compatibility в этом таске.

---

## 4. Что выносится в отдельную задачу

Ниже перечислены задачи, которые **не обязательно** делать прямо сейчас в рамках добивки Release 1, но они уже зафиксированы как следующие долги.

---

## Task 4. Перевести merchant review / moderation screen на canonical model

### Приоритет

`P2`

### Почему это отдельная задача

Это важный UI read-model cleanup, но он не блокирует:

- миграции;
- bot lead flow;
- admin write flow;
- public coupon catalog.

### Что сейчас неправильно

Экран review/moderation все еще показывает:

- `fullDescription`
- `terms`
- `usageRules`
- `howToUse`
- `address`
- `contactPhone`
- `workingHours`

вместо:

- `offerDescription`
- `merchant.primaryLocation`
- merchant profile block

### Задействованные файлы

- [MerchantReviewPage.tsx](/Users/abror/Projects/topdim/frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx)
- [CouponOfferResponse.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponOfferResponse.java)
- [MerchantResponse.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantResponse.java)

### Что должно быть результатом

- review page показывает canonical offer block;
- review page показывает merchant block отдельно;
- coupon-level contacts не используются как source of truth.

---

## Task 5. Выровнять secondary search layer под offerDescription

### Приоритет

`P2`

### Почему это отдельная задача

`CouponSearchService` сейчас не управляет основным catalog search flow.

Но если Elasticsearch/search-index будет использоваться активнее дальше, его тоже нужно привести к новой canonical модели.

### Что сейчас неправильно

Secondary search layer все еще ориентирован на `fullDescription`.

### Задействованные файлы

- [CouponSearchService.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/search/CouponSearchService.java)
- [CouponSearchDocument.java](/Users/abror/Projects/topdim/services/coupon-service/src/main/java/uz/topdim/coupon/search/CouponSearchDocument.java)

### Что должно быть результатом

- search index и mappings знают про `offerDescription`;
- legacy `fullDescription` либо остается как fallback на переходный период, либо документированно убирается.

---

## 5. Что нельзя трогать в этой добивке

Это все остается вне scope текущего completion backlog:

- удаление `Shop` из `coupon-service`;
- удаление `DirectoryController` и `DirectoryService`;
- cleanup bazaar routes;
- gateway cleanup;
- полный отказ от legacy merchant fields в БД;
- полный отказ от legacy coupon fields в БД;
- изменение coupon lifecycle;
- редизайн storefront или admin UI.

Это относится к Release 2 или отдельным задачам.

---

## 6. Рекомендуемый порядок выполнения

Ниже порядок, в котором ИИ-исполнителю лучше закрывать хвосты:

1. `Task 1` — bot lead flow
2. `Task 2` — admin merchant write-flow
3. `Task 3` — live catalog search
4. отдельной задачей `Task 4`
5. отдельной задачей `Task 5`

### Почему именно так

- сначала нужно выровнять критичный write-flow;
- потом закрыть админскую запись данных;
- потом довести публичный поиск;
- только после этого браться за secondary UI/read-model cleanup.

---

## 7. Формат отчета для другого ИИ

После выполнения задач ИИ должен отчитаться в формате:

### Сделано

- какие файлы backend изменены;
- какие файлы frontend изменены;
- какие тесты добавлены или обновлены.

### Проверено

- какие сценарии были проверены вручную;
- какие автоматические тесты были запущены.

### Осталось

- какие пункты backlog сознательно не тронуты;
- какие задачи остаются на отдельный `P2` тикет;
- какие legacy зависимости остаются до Release 2.

---

## 8. Краткий итог

Чтобы Release 1 действительно считался закрытым, нужно добить три вещи:

1. bot lead flow на canonical merchant location model;
2. admin quick-create merchant на `locations[]`;
3. live catalog search по `offerDescription`.

Все остальное уже либо сделано, либо является отдельным следующим тикетом.
