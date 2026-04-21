# План рефакторинга БД: coupon + merchant без bazaar-связности

## 1. Цель документа

Документ фиксирует целевую структуру данных и пошаговый план имплементации для сценария, в котором:

- bazaar является отдельным продуктовым контуром;
- bazaar не является частью coupon/merchant домена;
- информация о заведении хранится у `merchant`;
- информация об акции хранится у `coupon`;
- контакты и адреса не дублируются в `coupon`;
- `Shop` в `coupon-service` больше не используется как bazaar-сущность.

---

## 2. Зафиксированные решения

### Подтверждено решением команды

- `bazaar` и `coupon` — разные доменные направления.
- Купоны не должны зависеть от bazaar-модели.
- Мерчант и купон должны жить независимо от bazaar.
- Блок “О заведении” должен наполняться из сущности `merchant`.
- У мерчанта может быть несколько адресов и точек контакта.
- На детальной странице купона в первом релизе показывается один `primary` адрес мерчанта.
- Bazaar/shop-контур удаляется из `coupon-service` вторым релизом, не в первой волне изменений.
- В `coupon` должна оставаться только информация об акции:
  - title;
  - единый длинный текст оффера;
  - скидка;
  - цены;
  - даты покупки и использования;
  - правила и условия;
  - рейтинг/отзывы/счетчики;
  - сертификаты (`coupon_options`).

### Подтверждено решением команды

Структура текстового описания купона упрощается:

- отдельные поля `short_description`, `terms`, `usage_rules`, `how_to_use` считаются legacy-моделью;
- целевая модель использует одно поле `offer_description`;
- внутри `offer_description` хранится весь текст акции и условий в одной большой структуре.

### Подтверждено кодом

Сейчас в `coupon-service` есть смешение зон ответственности:

- `merchant` хранит `description`, `address`, `phone`, `workingHours`;
- `coupon_offers` тоже хранит `fullDescription`, `address`, `contactPhone`, `workingHours`;
- в `coupon-service` существует отдельный bazaar/directory-контур (`bazaars`, `shops`, `DirectoryService`).

### Риск

- Если удалить поля из `coupon` без подготовительной миграции API и UI, детальная страница купона и админская форма сломаются.
- Если оставить `shops` в `coupon-service`, команда будет продолжать смешивать coupon/merchant и bazaar/directory домены.

---

## 3. Текущее состояние

### Подтверждено кодом

#### 3.1. Merchant

Текущая сущность `merchant` хранит:

- `name`
- `description`
- `logoUrl`
- `coverUrl`
- `address`
- `phone`
- `email`
- `website`
- `workingHours`
- `contactPerson`
- `active`
- `userId`
- `telegramChatId`

#### 3.2. CouponOffer

Текущая сущность `coupon_offers` хранит:

- `title`
- `shortDescription`
- `fullDescription`
- `merchant_id`
- `category_id`
- `oldPrice`
- `fromPrice`
- `discountPercent`
- `coverImageUrl`
- `buyUntil`
- `useUntil`
- `terms`
- `usageRules`
- `howToUse`
- `address`
- `contactPhone`
- `workingHours`
- `giftAvailable`
- `status`
- счетчики и служебные поля

#### 3.3. Shop / Bazaar в coupon-service

Внутри `coupon-service` есть:

- таблица `bazaars`;
- таблица `shops`;
- `Shop` с полями `bazaar_id`, `location_type`, `merchant_id`;
- публичный `DirectoryController` и `DirectoryService`.

### Вывод

Сейчас один и тот же продуктовый смысл разложен сразу в трех местах:

- merchant profile;
- coupon detail;
- directory shop/bazaar.

Для новой целевой модели это нужно развести.

---

## 4. Целевая доменная модель

## 4.1. Merchant — “кто продает”

### Рекомендация

`merchant` должен отвечать только за профиль заведения/бренда.

Базовые поля:

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

### Рекомендация

Если у мерчанта возможны несколько адресов/телефонов/точек, их нужно выносить в отдельную таблицу `merchant_locations`, а не хранить в `coupon`.

Предлагаемые поля `merchant_locations`:

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

## 4.2. CouponOffer — “что продается”

### Рекомендация

`coupon_offers` должен хранить только информацию об акции.

Целевые поля:

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

### Рекомендация

Из `coupon_offers` должны быть удалены:

- `short_description`
- `address`
- `contact_phone`
- `working_hours`
- `terms`
- `usage_rules`
- `how_to_use`

### Рекомендация

Поле `full_description` лучше переименовать в `offer_description`, чтобы больше не было путаницы между:

- описанием заведения;
- описанием акции.

### Подтверждено решением команды

В новой модели:

- `offer_description` становится единственным большим текстовым полем купона;
- отдельные смысловые блоки правил не хранятся в отдельных колонках;
- если UI нужно визуально делить текст на секции, это делается уровнем контента/разметки, а не отдельными DB-полями.

### Риск

- После объединения текстовых полей карточке каталога может не хватать короткого teaser-текста.
- Для каталога нужно будет выбрать один из двух вариантов:
  - вычислять preview автоматически из `offer_description`;
  - хранить preview только в read-model/API-агрегации, но не как отдельный source-of-truth столбец.

## 4.3. CouponOption — “какой сертификат покупают”

### Подтверждено кодом

Существующая модель `coupon_options` уже близка к целевой и должна быть сохранена:

- `coupon_offer_id`
- `title`
- `regular_price`
- `coupon_price`
- `quantity_limit`
- `quantity_sold`
- `status`

## 4.4. Bazaar / Shop

### Рекомендация

Для coupon/merchant домена `Shop` в `coupon-service` должен быть удален из целевой модели.

Корректное разделение:

- `bazaar-service` продолжает жить как отдельный продуктовый модуль;
- `coupon-service` больше не содержит `bazaars`, `shops`, `DirectoryService`, `DirectoryController`;
- если coupons в будущем должны работать с несколькими адресами мерчанта, используется `merchant_locations`, а не `shops`.

---

## 5. Что нужно изменить в БД

## 5.1. Основные изменения схемы

### Phase A: подготовка

1. Создать таблицу `merchant_locations`.
2. Добавить в `coupon_offers` новое поле `offer_description`.
3. Временно оставить старые поля:
   - `full_description`
   - `short_description`
   - `address`
   - `contact_phone`
   - `working_hours`
   - `terms`
   - `usage_rules`
   - `how_to_use`

### Phase B: перенос данных

1. Сформировать `coupon_offers.offer_description` из legacy-полей:
   - `full_description`
   - `terms`
   - `usage_rules`
   - `how_to_use`
   - при необходимости `short_description` как первая строка или лид-абзац.
2. Для каждого `merchant` создать primary location в `merchant_locations` из:
   - `merchant.address`
   - `merchant.phone`
   - `merchant.working_hours`
3. Если в каких-то купонах заполнены `coupon.address/contactPhone/workingHours`, а у мерчанта этих данных нет:
   - либо создать merchant location из этих данных;
   - либо вынести такие записи в отдельный migration report для ручной нормализации.

### Phase C: очистка

1. Удалить из `coupon_offers`:
   - `full_description`
   - `short_description`
   - `address`
   - `contact_phone`
   - `working_hours`
   - `terms`
   - `usage_rules`
   - `how_to_use`
2. Удалить из `merchants`:
   - `address`
   - `phone`
   - `working_hours`
   если эти данные полностью переезжают в `merchant_locations`.
3. Удалить из `coupon-service` таблицы:
   - `shops`
   - `bazaars`
   после полного отключения directory API из этого сервиса.

### Подтверждено решением команды

- `merchant_locations` вводится обязательно.
- Мерчант может иметь несколько адресов и телефонов.
- Для первого релиза storefront использует один `primary` адрес.

---

## 6. Изменения в backend

## 6.1. Слой entity / migration

### Изменить

- `Merchant`
- `CouponOffer`
- Flyway migrations в `coupon-service`

### Добавить

- `MerchantLocation`
- `MerchantLocationRepository`
- DTO для чтения и записи локаций мерчанта

## 6.2. Слой service

### Coupon service

Нужно:

- перестать читать контакты из `CouponOffer`;
- отдавать в coupon detail данные мерчанта и/или его primary location;
- использовать `offer_description` вместо набора `full_description/terms/usageRules/howToUse`.

### Merchant service

Нужно:

- научить CRUD мерчанта работать с location-данными;
- поддерживать список locations;
- уметь отдавать `primaryLocation` как отдельное поле для storefront coupon detail.

## 6.3. Слой API / DTO

### Coupon API

`CouponOfferResponse` должен поменяться:

- убрать:
  - `address`
  - `contactPhone`
  - `workingHours`
- добавить:
  - `offerDescription`
  - `merchant.description`
  - `merchant.primaryLocation`
  - при необходимости `merchant.locations` для backoffice или будущего storefront

### Подтверждено решением команды

Отдельные coupon-level поля:

- `shortDescription`
- `terms`
- `usageRules`
- `howToUse`

в новой API-модели не нужны и должны быть выведены из публичного контракта после переходного периода.

### Merchant API

`MerchantResponse` должен расшириться так, чтобы storefront мог строить блок “О заведении” без вытягивания этих данных из купона.

## 6.4. Удаление directory-контурa из coupon-service

Нужно:

- удалить `DirectoryController`;
- удалить `DirectoryService`;
- удалить `Bazaar`, `Shop`, `LocationType`, `BazaarResponse`, `ShopResponse`, `CreateBazaarRequest`, `CreateShopRequest`;
- удалить `BazaarRepository`, `ShopRepository`;
- удалить связанные migrations после переноса/архивации данных;
- убрать маршруты `/api/v1/directory/**` из gateway-маршрутизации для `coupon-service`.

### Важно

Это нужно делать отдельной фазой, не в том же коммите, где идет базовый рефакторинг `merchant` и `coupon`.

---

## 7. Изменения во frontend

## 7.1. Admin coupon form

Сейчас форма купона содержит:

- `fullDescription`;
- `address`;
- `contactPhone`;
- `workingHours`.

Нужно:

- удалить из формы купона поля контактов заведения;
- заменить набор полей `shortDescription/fullDescription/terms/usageRules/howToUse` на одно поле “Описание оффера”;
- блок “Контакты заведения” перенести в форму мерчанта.

## 7.2. Coupon detail page

Сейчас coupon detail использует coupon-level поля для:

- блока “О заведении”;
- вкладки контактов.

Нужно:

- блок “О заведении” строить из `merchant.description`;
- вкладку контактов строить из `merchant.primaryLocation`;
- блок оффера строить из `offerDescription`.

## 7.3. Catalog card

В карточке купона location должен браться не из `coupon.address`, а из primary location мерчанта или из отдельного normalized поля, если команда захочет оставить denormalized read-model для каталога.

### Рекомендация

Для каталога допустимо сделать read-side denormalization:

- в API каталога отдавать `merchantPrimaryAddress`;
- но не хранить его как source of truth внутри `coupon_offers`.

---

## 8. Предлагаемый порядок имплементации

## Этап 1. Зафиксировать target model

### Подтверждено решением команды

На старте зафиксированы такие правила:

1. У мерчанта поддерживается несколько locations.
2. `offer_description` является каноническим текстовым полем купона.
3. В детальной странице купона в первом релизе показывается один `primary` адрес.
4. Directory/bazaar-контур удаляется из `coupon-service` вторым релизом.

## Этап 2. Подготовительные миграции БД

1. Добавить `offer_description`.
2. Добавить `merchant_locations`.
3. Написать backfill SQL / Java migration script.

## Этап 3. Dual-read / dual-write

На переходный период:

- backend пишет новые данные в новую модель;
- API умеет читать новые поля, а при отсутствии — fallback на старые;
- admin UI постепенно переводится на новые DTO.

Это снижает риск “big bang migration”.

## Этап 4. Перевод frontend

1. Admin merchant form становится источником данных о заведении.
2. Admin coupon form оставляет только поля акции.
3. Storefront coupon detail начинает читать merchant-блок отдельно от coupon-блока.
4. Storefront coupon detail показывает `merchant.primaryLocation`, а не список всех точек.

## Этап 5. Cleanup

1. Удалить старые coupon-level contact fields.
2. Удалить старые merchant-level contact fields, если они вынесены в `merchant_locations`.
3. Удалить bazaar/shop-directory код из `coupon-service`.

## Этап 6. Regression + data QA

Проверить:

- ни один активный купон не потерял merchant linkage;
- у всех published merchants есть хотя бы один валидный contact/location source;
- детальная страница не показывает пустой merchant block;
- поиск и каталог не зависят от удаленных coupon contact fields;
- админка не отправляет старые поля после очистки DTO.

---

## 9. План работ по задачам

## 9.1. Database

1. Создать migration `merchant_locations`.
2. Создать migration `offer_description`.
3. Создать data backfill migration.
4. Создать cleanup migration для удаления legacy columns.

## 9.2. Backend coupon-service

1. Обновить `CouponOffer` entity.
2. Обновить `Merchant` entity.
3. Добавить `MerchantLocation` entity и repository.
4. Обновить `CreateCouponOfferRequest` / `CouponOfferResponse`.
5. Обновить `CreateMerchantRequest` / `MerchantResponse`.
6. Изменить `CouponOfferService`.
7. Изменить `MerchantService`.

## 9.3. Frontend admin-app

1. Обновить форму купона.
2. Обновить форму мерчанта.
3. Проверить редактирование существующих купонов и мерчантов.

## 9.4. Frontend web-app

1. Обновить типы coupon API.
2. Обновить `CouponDetailPage`.
3. Обновить `CouponCatalogPage`.
4. Обновить fallback/mock данные, чтобы они соответствовали новой структуре.

## 9.5. Cleanup bazaar separation

1. Удалить directory API из `coupon-service`.
2. Оставить bazaar-модель только в `bazaar-service`.
3. Обновить gateway/docs/tests.

---

## 10. Минимальный безопасный вариант релиза

### Рекомендация

Если команда хочет минимизировать риск, делать не одним большим рефакторингом, а двумя релизами:

### Release 1

- добавить `offer_description`;
- добавить `merchant_locations`;
- перевести coupon-текст на одну колонку `offer_description`;
- перевести API и frontend на чтение новых полей;
- оставить legacy fields в БД.

### Release 2

- удалить legacy fields из `coupon_offers`;
- удалить legacy fields из `merchants`, если принято решение о `merchant_locations`;
- удалить bazaar/directory-контур из `coupon-service`.

---

## 11. Главные архитектурные правила после рефакторинга

### Рекомендация

После завершения работ должны действовать такие правила:

1. `merchant` отвечает на вопрос: “что это за заведение?”
2. `merchant_location` отвечает на вопрос: “где оно находится и как связаться?”
3. `coupon` отвечает на вопрос: “что за акция и на каких условиях?”
4. `coupon_option` отвечает на вопрос: “какой сертификат покупает пользователь?”
5. `bazaar` не участвует в coupon/merchant data model.
6. `coupon-service` не хранит bazaar-domain сущности.
7. Детальная страница купона показывает один `primary` адрес мерчанта в первом релизе.
8. Полный текст акции хранится в одном поле `offer_description`.

---

## 12. Открытые вопросы перед стартом

### Открытый вопрос

1. Нужно ли сохранять `giftAvailable` в coupon scope?
2. Нужен ли storefront-каталогу отдельный короткий preview-текст, или он всегда вычисляется из `offer_description`?
3. Нужен ли список всех `merchant_locations` на отдельной merchant page в будущем, даже если coupon detail в первом релизе показывает только `primary` адрес?

---

## 13. Рекомендуемый стартовый scope

### Рекомендация

Начать с самого практичного объема:

1. Зафиксировать target schema.
2. Добавить `offer_description`.
3. Добавить `merchant_locations`.
4. Перенести contact/location данные из coupon в merchant layer.
5. Свернуть coupon text fields в одну колонку `offer_description`.
6. Перевести админку и storefront.
7. Только после этого вычищать `shops`/`bazaars` из `coupon-service`.

Такой порядок даст контролируемый рефакторинг без поломки текущего coupon flow.
