# Product Requirements Document — TopDim

# Оглавление
  - [Статус документа](#статус-документа)
  - [Как читать документ](#как-читать-документ)
  - [1. Product Overview](#1-product-overview)
  - [2. Problem Statement](#2-problem-statement)
  - [2.1. Пользовательская проблема](#21-пользовательская-проблема)
  - [2.2. Проблема мерчанта](#22-проблема-мерчанта)
  - [2.3. Бизнес-проблема платформы](#23-бизнес-проблема-платформы)
  - [2.4. Почему текущие альтернативы недостаточны](#24-почему-текущие-альтернативы-недостаточны)
  - [3. Product Goals](#3-product-goals)
  - [3.1. Business goals](#31-business-goals)
  - [3.2. User goals](#32-user-goals)
  - [3.3. Merchant goals](#33-merchant-goals)
  - [3.4. Platform goals](#34-platform-goals)
  - [3.5. Operational goals](#35-operational-goals)
  - [4. Non-Goals](#4-non-goals)
  - [5. Target Audience](#5-target-audience)
  - [5.1. Конечные пользователи](#51-конечные-пользователи)
  - [5.2. Мерчанты / партнеры](#52-мерчанты--партнеры)
  - [5.3. Модераторы / администраторы](#53-модераторы--администраторы)
  - [5.4. Внутренние операторы / support](#54-внутренние-операторы--support)
  - [6. User Roles and Permissions](#6-user-roles-and-permissions)
  - [6.1. Guest](#61-guest)
  - [6.2. Registered User](#62-registered-user)
  - [6.3. Merchant / Partner](#63-merchant--partner)
  - [6.4. Moderator](#64-moderator)
  - [6.5. Admin](#65-admin)
  - [6.6. Super Admin](#66-super-admin)
  - [6.7. Support/Operator](#67-supportoperator)
  - [7. Product Structure / Information Architecture](#7-product-structure--information-architecture)
  - [7.1. Публичный контур](#71-публичный-контур)
  - [7.2. Внутренний контур](#72-внутренний-контур)
  - [7.3. Merchant contour](#73-merchant-contour)
  - [8. Detailed Functional Scope](#8-detailed-functional-scope)
  - [8.1. Пользовательский контур](#81-пользовательский-контур)
  - [8.2. Купонный контур](#82-купонный-контур)
  - [8.3. Merchant contour](#83-merchant-contour)
  - [8.4. Bazaar / directory contour](#84-bazaar--directory-contour)
  - [8.5. Admin / moderation contour](#85-admin--moderation-contour)
  - [9. Detailed User Functionality](#9-detailed-user-functionality)
  - [9.1. Регистрация](#91-регистрация)
  - [9.2. Вход](#92-вход)
  - [9.3. Выход](#93-выход)
  - [9.4. Guest mode](#94-guest-mode)
  - [9.5. Профиль и учетные данные](#95-профиль-и-учетные-данные)
  - [9.6. Город / location behavior](#96-город--location-behavior)
  - [9.7. Избранное](#97-избранное)
  - [9.8. Просмотр каталога купонов](#98-просмотр-каталога-купонов)
  - [9.9. Детальная страница купона](#99-детальная-страница-купона)
  - [9.10. Покупка / checkout](#910-покупка--checkout)
  - [9.11. Purchased coupons](#911-purchased-coupons)
  - [9.12. Bazaar browsing](#912-bazaar-browsing)
  - [9.13. Отчет о неверной информации](#913-отчет-о-неверной-информации)
  - [9.14. Уведомления](#914-уведомления)
  - [10. Detailed Coupon Functionality](#10-detailed-coupon-functionality)
  - [10.1. Определение сущности](#101-определение-сущности)
  - [10.2. Типы купонов](#102-типы-купонов)
  - [10.3. Карточка купона в списке](#103-карточка-купона-в-списке)
  - [10.4. Детальная страница купона](#104-детальная-страница-купона)
  - [10.5. Варианты покупки](#105-варианты-покупки)
  - [10.6. Сроки](#106-сроки)
  - [10.7. CTA и действия](#107-cta-и-действия)
  - [10.8. Visibility logic](#108-visibility-logic)
  - [10.9. Sorting and filtering](#109-sorting-and-filtering)
  - [10.10. Coupon lifecycle](#1010-coupon-lifecycle)
  - [10.11. Зависимости](#1011-зависимости)
  - [11. Detailed Merchant Functionality](#11-detailed-merchant-functionality)
  - [11.1. Merchant onboarding](#111-merchant-onboarding)
  - [11.2. Merchant account creation](#112-merchant-account-creation)
  - [11.3. Merchant profile](#113-merchant-profile)
  - [11.4. Merchant dashboard](#114-merchant-dashboard)
  - [11.5. Coupon management from merchant side](#115-coupon-management-from-merchant-side)
  - [11.6. Merchant shops](#116-merchant-shops)
  - [12. Detailed Bazaar / Marketplace Functionality](#12-detailed-bazaar--marketplace-functionality)
  - [12.1. Что такое bazaar в системе](#121-что-такое-bazaar-в-системе)
  - [12.2. Bazaar listing](#122-bazaar-listing)
  - [12.3. Bazaar detail](#123-bazaar-detail)
  - [12.4. Shops inside bazaar](#124-shops-inside-bazaar)
  - [12.5. Bazaar and coupons relation](#125-bazaar-and-coupons-relation)
  - [12.6. Bazaar without linked merchant](#126-bazaar-without-linked-merchant)
  - [12.7. Search in bazaar context](#127-search-in-bazaar-context)
  - [13. Detailed Map Functionality](#13-detailed-map-functionality)
  - [13.1. Назначение карты](#131-назначение-карты)
  - [13.2. Entry points](#132-entry-points)
  - [13.3. Markers](#133-markers)
  - [13.4. Default behavior](#134-default-behavior)
  - [13.5. Area selection](#135-area-selection)
  - [13.6. External objects](#136-external-objects)
  - [13.7. Empty / hidden / inactive states](#137-empty--hidden--inactive-states)
  - [14. Detailed Search and Filter Functionality](#14-detailed-search-and-filter-functionality)
  - [14.1. Global search](#141-global-search)
  - [14.2. Coupon search](#142-coupon-search)
  - [14.3. Merchant / shop search](#143-merchant--shop-search)
  - [14.4. Filters](#144-filters)
  - [14.5. No results](#145-no-results)
  - [14.6. Filter persistence](#146-filter-persistence)
  - [15. Detailed Admin / Moderation Functionality](#15-detailed-admin--moderation-functionality)
  - [15.1. Admin panel structure](#151-admin-panel-structure)
  - [15.2. Moderation queues](#152-moderation-queues)
  - [15.3. Merchant approval flow](#153-merchant-approval-flow)
  - [15.4. Coupon approval flow](#154-coupon-approval-flow)
  - [15.5. Content editing rights](#155-content-editing-rights)
  - [15.6. Rejection reasons](#156-rejection-reasons)
  - [15.7. Visibility override / manual hide-show](#157-visibility-override--manual-hide-show)
  - [15.8. Audit / safety](#158-audit--safety)
  - [16. Detailed Page-by-Page Description](#16-detailed-page-by-page-description)
  - [16.1. Home](#161-home)
  - [16.2. Coupon Catalog](#162-coupon-catalog)
  - [16.3. Coupon Detail](#163-coupon-detail)
  - [16.4. Cart](#164-cart)
  - [16.5. Checkout](#165-checkout)
  - [16.6. Profile](#166-profile)
  - [16.7. Favorites](#167-favorites)
  - [16.8. Search](#168-search)
  - [16.9. Bazaar Directory](#169-bazaar-directory)
  - [16.10. Bazaar Detail](#1610-bazaar-detail)
  - [16.11. Shop Detail](#1611-shop-detail)
  - [16.12. Admin Coupon Kanban](#1612-admin-coupon-kanban)
  - [16.13. Admin Coupon Form](#1613-admin-coupon-form)
  - [16.14. Partner Applications](#1614-partner-applications)
  - [17. Business Rules](#17-business-rules)
  - [18. Data / Entity Definitions](#18-data--entity-definitions)
  - [19. User Flows](#19-user-flows)
  - [19.1. Guest discovers coupon](#191-guest-discovers-coupon)
  - [19.2. User saves coupon](#192-user-saves-coupon)
  - [19.3. User uses coupon](#193-user-uses-coupon)
  - [19.4. User browses bazaar](#194-user-browses-bazaar)
  - [19.5. User finds merchant on map](#195-user-finds-merchant-on-map)
  - [19.6. User reports incorrect info](#196-user-reports-incorrect-info)
  - [20. Merchant Flows](#20-merchant-flows)
  - [20.1. Merchant registration / lead](#201-merchant-registration--lead)
  - [20.2. Merchant profile completion](#202-merchant-profile-completion)
  - [20.3. Coupon submission](#203-coupon-submission)
  - [20.4. Revision handling](#204-revision-handling)
  - [20.5. Stats follow-up](#205-stats-follow-up)
  - [21. Admin Flows](#21-admin-flows)
  - [21.1. Review merchant application](#211-review-merchant-application)
  - [21.2. Review and build coupon](#212-review-and-build-coupon)
  - [21.3. Manage taxonomy](#213-manage-taxonomy)
  - [21.4. Manage staff](#214-manage-staff)
  - [22. States and Transitions](#22-states-and-transitions)
  - [22.1. Merchant / partner application](#221-merchant--partner-application)
  - [22.2. Coupon offer](#222-coupon-offer)
  - [22.3. Review](#223-review)
  - [22.4. Purchased coupon](#224-purchased-coupon)
  - [22.5. Order](#225-order)
  - [23. Edge Cases](#23-edge-cases)
  - [24. Error States](#24-error-states)
  - [25. UX Requirements](#25-ux-requirements)
  - [26. Content Requirements](#26-content-requirements)
  - [27. Non-Functional Requirements](#27-non-functional-requirements)
  - [28. Metrics / KPIs](#28-metrics--kpis)
  - [29. MVP Scope](#29-mvp-scope)
  - [30. Post-MVP Scope](#30-post-mvp-scope)
  - [31. Risks](#31-risks)
  - [32. Open Questions](#32-open-questions)
  - [33. Glossary](#33-glossary)
  - [Заключение](#заключение)


## Статус документа

- Версия: `v1` на основе анализа репозитория от `2026-04-18`
- Язык: русский
- Основание: кодовая база, внутренние документы, reference-анализ, gap-анализ

## Как читать документ

В документе используются обязательные маркеры:

- **Подтверждено кодом** — зафиксировано в текущей реализации.
- **Подтверждено референсом** — подтверждено reference-product/materials.
- **Предположение** — вероятная, но не полностью подтвержденная трактовка.
- **Риск** — зона продуктовой, UX или архитектурной неопределенности.
- **Открытый вопрос** — вопрос, требующий явного решения.
- **Рекомендация** — предлагаемый продуктовый вариант для MVP или следующего этапа.

---

## 1. Product Overview

### Подтверждено кодом

- Название продукта: `TopDim`
- Тип продукта: гибридная платформа локальных скидок, купонов, directory базаров/магазинов и внутренней staff-модерации.
- Текущие продуктовые поверхности:
  - публичный web storefront;
  - admin-приложение;
  - Telegram-бот для merchant-согласования;
  - микросервисный backend.

### Подтверждено референсом

- Продуктовый паттерн близок к consumer marketplace локальных выгодных предложений с сильной карточкой оффера, условиями, географией, merchant context и post-purchase usage flow.

### Предположение

- Рыночный контекст: Узбекистан, локальные офлайн/гибридные предложения, где пользователь хочет быстро находить выгодные офферы и понятные места их использования, а партнеру нужен канал привлечения аудитории.

### Рекомендация

- Формулировать TopDim как:
  - “локальную платформу выгодных предложений и удобного поиска торговых точек, где пользователь находит купоны и места рядом, а партнер получает управляемый канал публикации акций”.

### Почему продукт существует

### Предположение

- Пользовательский рынок локальных скидок фрагментирован: предложения разбросаны по Instagram, Telegram, офлайн-точкам и не имеют единых правил доверия, срока действия, условий использования и простого redemption flow.

### Предположение

- Бизнесу нужен не только трафик, но и операционный контур, где акцию можно собрать, согласовать, запустить, измерить и остановить без хаоса в коммуникации.

---

## 2. Problem Statement

## 2.1. Пользовательская проблема

### Предположение

- Пользователю сложно:
  - быстро находить релевантные локальные скидки;
  - понимать реальные условия использования;
  - доверять качеству предложения;
  - видеть, где именно находится партнер;
  - хранить купленные сертификаты и использовать их без потери контекста.

## 2.2. Проблема мерчанта

### Подтверждено кодом

- Текущий backend уже предполагает управляемый staff-mediated flow, а не хаотичное самостоятельное размещение.

### Предположение

- Мерчанту нужна:
  - понятная подача заявки;
  - помощь в упаковке акции;
  - согласование текста/цен/условий;
  - прозрачный запуск;
  - последующая статистика и обратная связь.

## 2.3. Бизнес-проблема платформы

### Предположение

- Платформе нужен единый продуктовый контур, где:
  - витрина для пользователя конвертирует трафик;
  - staff управляет качеством контента;
  - мерчант проходит понятный onboarding;
  - продажи и использование купонов измеряются;
  - directory/map усиливают discovery и доверие.

## 2.4. Почему текущие альтернативы недостаточны

### Подтверждено референсом

- Reference-подход показывает, что рынок выигрывает не просто от публикации скидок, а от полного цикла:
  - карточка;
  - условия;
  - адрес;
  - карта;
  - покупка;
  - сертификат;
  - использование.

### Предположение

- Каналы вроде Instagram/Telegram-ленты не дают достаточно структурированного пользовательского опыта и прозрачных правил использования.

---

## 3. Product Goals

## 3.1. Business goals

### Рекомендация

- Сформировать управляемую платформу локальных офферов.
- Увеличить количество опубликованных и реально используемых акций.
- Упростить merchant acquisition без потери контроля качества.
- Получить измеримые воронки: просмотр → сохранение → покупка → использование.

## 3.2. User goals

### Рекомендация

- Быстро находить выгодные локальные предложения.
- Понимать условия без скрытых допущений.
- Доверять карточке и merchant context.
- Удобно хранить и использовать купленные купоны.

## 3.3. Merchant goals

### Рекомендация

- Быстро подать заявку и запустить оффер.
- Понимать статус публикации.
- Согласовывать карточку без сложного кабинета на MVP.
- Видеть базовую статистику по результатам акции.

## 3.4. Platform goals

### Рекомендация

- Иметь единый жизненный цикл сущностей.
- Иметь ясные роли и права.
- Исключить противоречия между storefront, админкой, bot-flow и backend rules.

## 3.5. Operational goals

### Рекомендация

- Снизить ручную неоднозначность у staff.
- Сократить количество скрытых решений “на словах”.
- Сделать статусы, visibility и edge cases тестируемыми.

---

## 4. Non-Goals

### Рекомендация

Для MVP не считать обязательными:

- полноценный self-service merchant cabinet уровня зрелого SaaS;
- универсальный e-commerce маркетплейс товаров с логистикой;
- глубокую CRM для мерчантов;
- сложную social/community-платформу с UGC beyond reviews;
- полноценный multi-city enterprise rollout без закрепленных правил city visibility;
- полную замену внешних картографических систем собственным GIS-решением.

### Риск

- В коде уже заложены элементы будущих модулей. Их наличие в репозитории не означает, что они входят в MVP.

---

## 5. Target Audience

## 5.1. Конечные пользователи

### Рекомендация

Кто это:

- жители крупных городов Узбекистана;
- люди, ищущие скидки на еду, развлечения, beauty, спорт, услуги;
- пользователи, которым важны цена, близость точки и понятность условий.

Потребности:

- выгодное предложение;
- доверие к офферу;
- понятные сроки и правила;
- удобная карта/адрес;
- сохранение и использование купона.

Мотивации:

- экономия;
- discovery новых мест;
- “покупка эмоций выгодно”.

Pain points:

- неясные условия;
- истекшие или недоступные офферы;
- отсутствие понятного redemption flow;
- невозможность быстро понять, где находится точка.

## 5.2. Мерчанты / партнеры

### Рекомендация

Кто это:

- офлайн-точки, сети, отдельные магазины, заведения, услуги;
- потенциально merchants внутри базаров и standalone merchants.

Потребности:

- заявить о себе;
- получить трафик;
- безопасно согласовать акцию;
- контролировать публикацию и базовые результаты.

Pain points:

- непонятный onboarding;
- отсутствие простого управляющего канала;
- сложность правильно упаковать условия оффера.

## 5.3. Модераторы / администраторы

### Подтверждено кодом

Роли staff уже существуют и управляют:

- купонами;
- партнерскими заявками;
- категориями;
- staff accounts;
- audit.

### Рекомендация

Их потребности:

- ясные очереди работы;
- понятные статусы;
- минимум ручной интерпретации;
- контроль качества публикации;
- аудит действий.

## 5.4. Внутренние операторы / support

### Предположение

- В будущем могут понадобиться отдельные операторы для:
  - жалоб;
  - пользовательских вопросов;
  - отзывов;
  - возвратов.

### Риск

- В текущем коде support-раздел как самостоятельный рабочий контур не завершен.

---

## 6. User Roles and Permissions

## 6.1. Guest

### Подтверждено кодом

Может:

- просматривать главную, каталог, bazaar/directory, магазины, legal pages;
- открывать детальную страницу купона;
- добавлять купоны в избранное;
- складывать товары в локальную корзину;
- проходить guest checkout;
- выбирать город в UI.

Не может:

- видеть вкладку контактов купона;
- видеть профиль с купленными купонами;
- пользоваться protected user data.

Ограничения:

- избранное до 10;
- часть действий сохраняется только локально.

## 6.2. Registered User

### Подтверждено кодом

Может:

- все, что может guest;
- авторизоваться и обновлять профиль;
- синхронизировать избранное с backend;
- просматривать вкладку контактов на карточке купона;
- видеть купленные купоны;
- инициировать refund request;
- создавать отзывы через backend API.

Не может:

- модерировать купоны;
- управлять staff или категориями;
- выполнять merchant redemption.

## 6.3. Merchant / Partner

### Подтверждено кодом

Сейчас подтверждены только следующие каналы возможностей:

- approve/revision купона через Telegram;
- получение списка своих купонов через bot API;
- получение статистики;
- управление своими магазинами через `bazaar-service` partner API.

### Риск

- Полноценный web-кабинет мерчанта на текущем срезе не реализован как цельный продукт.

## 6.4. Moderator

### Подтверждено кодом

Может:

- входить в admin-app;
- просматривать список купонов и kanban;
- брать лид в работу;
- редактировать купоны в допустимых статусах;
- отправлять купоны на согласование;
- участвовать в review flow;
- видеть dashboard.

### Подтверждено кодом

Ограничение:

- `MODERATOR` не должен редактировать чужой закрепленный купон.

### Риск

- Часть admin-роутов в приложении защищена шире, чем предполагает меню, поэтому текущая route-level authorization и intended permission model расходятся.

## 6.5. Admin

### Подтверждено кодом

Может:

- все действия модератора;
- управлять категориями;
- управлять партнерскими заявками;
- создавать мерчантов;
- просматривать и обрабатывать заказы/возвраты на backend уровне;
- удалять/обновлять часть объектов шире модератора.

## 6.6. Super Admin

### Подтверждено кодом

Может:

- все действия admin;
- создавать staff;
- менять роли;
- блокировать пользователей/staff;
- просматривать audit logs.

## 6.7. Support/Operator

### Предположение

- Как отдельная роль в коде не реализован.
- Если понадобится, следует описывать либо как subset admin/modeator, либо как отдельный пост-MVP role type.

---

## 7. Product Structure / Information Architecture

## 7.1. Публичный контур

### Подтверждено кодом

Основная структура:

- Главная
- Каталог купонов
- Деталь купона
- Избранное
- Корзина
- Checkout
- Профиль
- Поиск
- Bazaar / directory
- Bazaar detail
- Shop detail
- Legal / FAQ / Partners

### Рекомендация

Главные entry points для пользователя должны быть всего два:

1. “Скидки / Купоны”
2. “Базары и магазины / Карта”

Остальные разделы — вторичные и contextual.

## 7.2. Внутренний контур

### Подтверждено кодом

Admin IA:

- Dashboard
- Контент / Купоны
- Категории
- Партнерские заявки
- Система / Staff / Audit

### Рекомендация

Support, orders, reviews, complaints, bazaars, shops следует считать отдельными модулями roadmap, пока они не закреплены как MVP-ready.

## 7.3. Merchant contour

### Подтверждено кодом

На текущем срезе merchant-contour распределен между:

- Telegram bot;
- отдельными bot endpoints;
- частично `bazaar-service` partner-shops API.

### Рекомендация

Для MVP считать merchant contour канальным, а не полноценным web-кабинетом:

- вход через Telegram или partner application;
- согласование через Telegram;
- ограниченные shop management endpoints как internal/future foundation.

---

## 8. Detailed Functional Scope

## 8.1. Пользовательский контур

### Подтверждено кодом

- browsing каталога;
- карточка купона;
- favorites;
- local cart;
- checkout;
- purchased coupons в профиле;
- bazaar/directory browsing;
- карта;
- поиск;
- legal/faq pages.

## 8.2. Купонный контур

### Подтверждено кодом

- coupon lifecycle;
- purchase options;
- prices and discount;
- media;
- rating/reviews domain;
- stats;
- sold-out logic;
- redemption via purchased coupon.

## 8.3. Merchant contour

### Подтверждено кодом

- partner applications;
- merchant creation by admin;
- bot lead intake;
- merchant approval/revision flow;
- merchant stats via bot.

## 8.4. Bazaar / directory contour

### Подтверждено кодом

- bazaar listing;
- bazaar detail;
- shop detail;
- map browsing;
- area selection;
- external object injection via 2GIS.

## 8.5. Admin / moderation contour

### Подтверждено кодом

- coupon kanban;
- coupon form;
- review queue;
- categories;
- partner applications;
- staff management;
- audit logs.

---

## 9. Detailed User Functionality

## 9.1. Регистрация

### Подтверждено кодом

Точка входа:

- страница `/login` в режиме `Регистрация`.

Поля:

- `firstName` — обязательное;
- `email` — обязательное;
- `phone` — опциональное;
- `password` — обязательное.

Валидации UI:

- `firstName` минимум 2 символа;
- email валиден;
- телефон при вводе должен соответствовать формату `+998XXXXXXXXX`;
- пароль минимум 8 символов.

Валидации backend:

- строгая password-policy на стороне identity-service.

Успех:

- пользователь получает токены и user profile.

Ошибка:

- показывается server message или generic error.

### Риск

- UI не отражает всю силу backend password policy.

## 9.2. Вход

### Подтверждено кодом

Поля:

- email;
- password.

Успех:

- токены сохраняются в localStorage;
- пользователь уходит на главную.

Неуспех:

- показывается server error.

## 9.3. Выход

### Подтверждено кодом

- logout вызывает backend best-effort;
- локальное auth state очищается;
- пользователь теряет доступ к protected действиям.

## 9.4. Guest mode

### Подтверждено кодом

Гость может:

- browse каталог;
- сохранять favorites локально;
- собирать local cart;
- пройти guest-auth внутри checkout.

### Рекомендация

- В PRD закрепить guest как официальную MVP-функцию, иначе команда будет трактовать guest checkout как случайную техническую времянку.

## 9.5. Профиль и учетные данные

### Подтверждено кодом

Backend поддерживает:

- `GET /api/v1/users/me`
- `PUT /api/v1/users/me`

Обновляемые поля по коду:

- `firstName`
- `lastName`
- `phone`
- `avatarUrl`

### Подтверждено кодом

Storefront пока не реализует полноценный UI-редактор профиля. Профиль сфокусирован на купленных купонах и статистике.

### Рекомендация

- В MVP profile edit можно оставить минимальным, но в PRD нужно явно закрепить, на какой странице он живет и какие поля доступны.

## 9.6. Город / location behavior

### Подтверждено кодом

- В приложении есть city store с preset cities;
- default city — Ташкент;
- есть попытка определить город по IP через `ipapi`.

### Риск

- Выбранный город пока не закреплен как hard business rule выдачи.

### Рекомендация

- Для MVP задать:
  - city как фильтр каталога и directory;
  - fallback на Ташкент;
  - ручное переключение города;
  - явный empty state для города без результатов.

## 9.7. Избранное

### Подтверждено кодом

Точка входа:

- сердце на карточке;
- страница `/favorites`.

Поведение:

- toggle add/remove;
- guest limit 10;
- auth limit 50;
- sync after login.

Состояния:

- пустое;
- наполненное;
- limit reached.

## 9.8. Просмотр каталога купонов

### Подтверждено кодом

Точка входа:

- главная;
- `/coupons`.

Элементы:

- hero;
- поиск;
- сортировка;
- фильтр категорий;
- переключение вида;
- список карточек;
- пагинация;
- empty state;
- loading skeletons.

## 9.9. Детальная страница купона

### Подтверждено кодом

Пользователь видит:

- галерею;
- merchant badge;
- title;
- short description;
- favorite/share actions;
- social proof block;
- табы;
- условия;
- how-to-use;
- описание;
- варианты покупки;
- related offers.

CTA:

- добавить в корзину;
- купить сейчас.

### Подтверждено кодом

Контакты доступны только авторизованному пользователю.

### Рекомендация

- В MVP detail page должна быть единственной точкой снятия сомнений; в ней нельзя оставлять серые зоны типа “пользователь не понимает, можно ли использовать оффер сегодня и где именно”.

## 9.10. Покупка / checkout

### Подтверждено кодом

Checkout содержит:

- контактный шаг для guest;
- выбор способа оплаты;
- sidebar заказа;
- CTA на оплату.

### Риск

- Реальный server-side checkout flow не согласован с local storefront cart.

### Рекомендация

- Для MVP явно выбрать одну модель:
  - или web cart синхронизируется с backend cart;
  - или direct buy создает order line items без серверной корзины.

## 9.11. Purchased coupons

### Подтверждено кодом

В профиле пользователь может:

- видеть активные купоны;
- видеть использованные;
- видеть истекшие.

Поля отображения:

- название;
- опция;
- PIN/код;
- статус;
- срок действия.

### Рекомендация

- В MVP purchased coupon card должна еще явно объяснять:
  - где использовать;
  - что показать;
  - можно ли вернуть;
  - что делать, если партнер не принимает купон.

## 9.12. Bazaar browsing

### Подтверждено кодом

Пользователь может:

- просматривать список базаров;
- фильтровать по типу;
- искать;
- смотреть карточку базара;
- смотреть карточку магазина;
- выделять область на карте.

## 9.13. Отчет о неверной информации

### Подтверждено кодом

- Отдельный пользовательский flow не найден.

### Рекомендация

- Для MVP нужен простой report flow минимум для купона и магазина:
  - “Сообщить о проблеме”;
  - категория проблемы;
  - optional comment.

## 9.14. Уведомления

### Подтверждено кодом

- Backend support есть.

### Подтверждено кодом

- Явного notification center в storefront не найдено.

### Рекомендация

- Для MVP достаточно доставлять критические post-purchase сообщения по email/SMS и отражать их в профиле или будущем notification center.

---

## 10. Detailed Coupon Functionality

## 10.1. Определение сущности

### Подтверждено кодом

Купон — это коммерческий оффер, связанный с мерчантом, категорией, ценами, условиями, сроками, медиа и набором purchase options.

## 10.2. Типы купонов

### Подтверждено кодом

- Явной отдельной enum-типизации coupon types в storefront не найдено.
- На практике купон может отличаться:
  - по содержимому;
  - по gift-availability;
  - по наличию нескольких options;
  - по количественным лимитам.

### Рекомендация

- Для MVP достаточно делить купоны на:
  - single-option;
  - multi-option;
  - gift-enabled;
  - limited quantity.

## 10.3. Карточка купона в списке

### Подтверждено кодом

Карточка включает:

- cover image;
- title;
- short description;
- merchant;
- category;
- старая цена;
- новая цена;
- discount badge;
- total sold;
- rating/review count;
- адрес/локацию;
- hot/gift indicators;
- favorite action.

### Рекомендация

- Обязательные поля карточки для публикации:
  - `coverImageUrl`
  - `title`
  - `merchant`
  - `fromPrice`
  - `discountPercent` или вычисляемая экономия
  - хотя бы один location/context элемент

## 10.4. Детальная страница купона

### Подтверждено кодом

Содержит:

- media slider;
- merchant badge;
- title/subtitle;
- favorite/share;
- stats;
- tabbed content;
- purchase options;
- related offers.

### Подтверждено референсом

- Это соответствует reference-паттерну “detail page как конверсионный и trust-heavy экран”.

## 10.5. Варианты покупки

### Подтверждено кодом

У каждого купона может быть несколько options:

- `title`
- `regularPrice`
- `couponPrice`
- `quantityLimit`
- `quantitySold`
- `status`

### Подтверждено кодом

- При отсутствии options UI строит fallback-вариант.

### Рекомендация

- Для публикации купона в production-mode лучше требовать хотя бы одну явную option вместо UI fallback.

## 10.6. Сроки

### Подтверждено кодом

- `buyUntil` — крайний срок покупки;
- `useUntil` — крайний срок использования.

### Рекомендация

- На storefront оба срока должны быть визуально различимы и объяснены.

## 10.7. CTA и действия

### Подтверждено кодом

Основные действия:

- добавить в избранное;
- поделиться;
- добавить в корзину;
- купить сейчас.

### Рекомендация

- Для `SOLD_OUT` и недоступных состояний нужно отдельное CTA-поведение:
  - disabled button;
  - waitlist/postpone;
  - alternative offers.

## 10.8. Visibility logic

### Подтверждено кодом

- Публичный каталог = только `ACTIVE`.

### Рекомендация

- Direct detail page для non-public статусов должна показывать controlled unavailable state, а не реальную карточку.

## 10.9. Sorting and filtering

### Подтверждено кодом

Поддерживаются:

- popular;
- new;
- price asc/desc;
- discount;
- category filter;
- search.

### Рекомендация

- Для MVP этого достаточно, но в PRD нужно закрепить единый search/filter contract для:
  - home;
  - catalog;
  - global search.

## 10.10. Coupon lifecycle

### Подтверждено кодом

Рабочий lifecycle описан в разделе статусов ниже и должен быть сохранен как canonical operational model на MVP.

## 10.11. Зависимости

### Подтверждено кодом

Купон зависит от:

- мерчанта;
- категории;
- статуса;
- дат;
- purchase options;
- продаж/лимитов.

### Открытый вопрос

- Должен ли купон зависеть от города, bazaar context и geo-данных как жесткое правило видимости?

---

## 11. Detailed Merchant Functionality

## 11.1. Merchant onboarding

### Подтверждено кодом

Сейчас существуют два рабочих входа:

- partner application;
- Telegram bot lead.

### Рекомендация

- Для MVP закрепить основной сценарий:
  - первичный onboarding через Telegram для оперативности;
  - partner application как альтернативный лид-канал;
  - staff normalizes merchant entity.

## 11.2. Merchant account creation

### Подтверждено кодом

- Admin может создать мерчанта из admin form быстро.
- Quick-create в coupon form отправляет контакты через `locations[]` и создает `primaryLocation`.
- Bot lead flow может создать неактивного мерчанта автоматически.

### Открытый вопрос

- В какой момент создается полноценный partner user account с ролью `PARTNER` и какими credential flows он пользуется?

## 11.3. Merchant profile

### Подтверждено кодом

- Полноценного публичного merchant page на storefront не найдено.

### Рекомендация

- В MVP merchant public page желательно ввести хотя бы в минимальном виде:
  - бренд;
  - описание;
  - контакты;
  - адреса;
  - список купонов / магазинов.

## 11.4. Merchant dashboard

### Подтверждено кодом

- Полноценного web-dashboard не найдено.
- Частично роль dashboard выполняет Telegram bot:
  - list my coupons;
  - coupon stats;
  - approve/revision.

### Рекомендация

- Для MVP можно оставить bot-first merchant operations, если это будет явно закреплено в продукте.

## 11.5. Coupon management from merchant side

### Подтверждено кодом

- Мерчант не создает полноценно купон сам в web UI.
- Он участвует в согласовании и правках через bot.

### Рекомендация

- В PRD закрепить, что merchant в MVP:
  - подтверждает;
  - возвращает на доработку;
  - смотрит stats;
  - не обязан самостоятельно собирать весь оффер в сложной форме.

## 11.6. Merchant shops

### Подтверждено кодом

- В `bazaar-service` партнер может редактировать ограниченный набор полей своих shop entities.

### Открытый вопрос

- Входит ли это в MVP пользовательского продукта или остается внутренней/будущей функцией?

---

## 12. Detailed Bazaar / Marketplace Functionality

## 12.1. Что такое bazaar в системе

### Подтверждено кодом

Bazaar — это крупный location container с адресом, типом, координатами, описанием, медиа и списком магазинов.

Типы, встречающиеся в коде:

- `BAZAAR`
- `MARKET`
- `SHOPPING_CENTER`
- `TRADE_COMPLEX`

## 12.2. Bazaar listing

### Подтверждено кодом

Список базаров показывает:

- тип;
- число магазинов;
- название;
- optional `nameUz`;
- описание;
- адрес;
- часы работы.

## 12.3. Bazaar detail

### Подтверждено кодом

Страница базара содержит:

- hero image / placeholder;
- type badge;
- title / subtitle;
- description;
- мета-блок контактов;
- карту;
- поиск магазинов внутри базара;
- grid магазинов.

## 12.4. Shops inside bazaar

### Подтверждено кодом

Карточка магазина внутри bazaar context показывает:

- category;
- название;
- краткий ассортимент;
- bazaar placement;
- переход в shop detail.

## 12.5. Bazaar and coupons relation

### Подтверждено кодом

- Прямая жесткая модель “купон принадлежит bazaar” в storefront не закреплена явно.
- Магазины в `bazaar-service` могут иметь `hasCoupon` и `linkedCouponOfferId`.

### Открытый вопрос

- Что является canonical связью:
  - купон → merchant → shop → bazaar;
  - купон → bazaar напрямую;
  - только merchant/location relation?

## 12.6. Bazaar without linked merchant

### Рекомендация

- Если merchant не связан с bazaar, его магазин должен отображаться как standalone object и не входить в bazaar-specific filters.

## 12.7. Search in bazaar context

### Подтверждено кодом

- Внутри страницы базара есть client-side поиск по магазинам.

### Рекомендация

- Для MVP достаточно поиска по:
  - названию магазина;
  - категории;
  - goods description.

---

## 13. Detailed Map Functionality

## 13.1. Назначение карты

### Подтверждено кодом

- Карта используется как browsing-инструмент для базаров и для area-based discovery.

### Подтверждено референсом

- География является важным trust и conversion фактором.

## 13.2. Entry points

### Подтверждено кодом

- `/bazaar`
- coupon contacts tab
- bazaar detail
- standalone shop detail

## 13.3. Markers

### Подтверждено кодом

- На directory map отображаются bazaar markers.
- На detail pages используется `staticMarker`.
- Label базара показывается над marker.

### Подтверждено кодом

- Кластеризация не найдена.

## 13.4. Default behavior

### Подтверждено кодом

- default center: `[69.2797, 41.3111]`
- default zoom: `12`
- static detail marker zoom: `15`

## 13.5. Area selection

### Подтверждено кодом

- Пользователь кликами строит полигон.
- Double-click завершает выбор.
- Система берет bounding box и ищет объекты в этой области.

### Рекомендация

- В PRD считать текущую реализацию “area selection by polygon → search by bounding box” MVP-достаточной.

## 13.6. External objects

### Подтверждено кодом

- После area select фронтенд подмешивает объекты из 2GIS и отмечает их как `isExternal`.

### Рекомендация

- В UX внешние объекты должны быть явно помечены и не смешиваться без различия с внутренними.

## 13.7. Empty / hidden / inactive states

### Подтверждено кодом

- Для пустой области есть empty states в results panel.
- Inactive internal objects не должны приходить из directory API.

### Открытый вопрос

- Как отображать ситуации, когда на карте есть только external objects и нет внутренних?

---

## 14. Detailed Search and Filter Functionality

## 14.1. Global search

### Подтверждено кодом

- В явном production-ready виде не реализован единый global search across all entity types.

### Рекомендация

- Зафиксировать две модели:
  - catalog search — для купонов;
  - directory search — для базаров/магазинов;
  - отдельная unified search page как post-MVP либо в ограниченном MVP-виде.

## 14.2. Coupon search

### Подтверждено кодом

- В каталоге купонов используется backend search через `/api/v1/coupons`.
- В отдельной search page купонный поиск пока mock-based.

## 14.3. Merchant / shop search

### Подтверждено кодом

- Поиск магазинов реализован на dedicated search page и в directory API.

## 14.4. Filters

### Подтверждено кодом

Для купонов:

- category;
- sort;
- search.

Для bazaar/directory:

- type filter;
- search;
- selected area.

### Рекомендация

- Для MVP стоит добавить явный location/city filter в продуктовую модель, даже если UI сделан постепенно.

## 14.5. No results

### Подтверждено кодом

- В catalog, search, bazaar results есть отдельные empty states.

## 14.6. Filter persistence

### Подтверждено кодом

- Явной глубокой persistence модели фильтров кроме store/local state не найдено.

### Рекомендация

- Сохранять минимум:
  - выбранный город;
  - last used catalog filters в URL или local state.

---

## 15. Detailed Admin / Moderation Functionality

## 15.1. Admin panel structure

### Подтверждено кодом

Панель состоит из:

- dashboard;
- coupon management;
- categories;
- partner applications;
- system/staff/audit.

## 15.2. Moderation queues

### Подтверждено кодом

Очереди купонов выражены через kanban-колонки и список.

## 15.3. Merchant approval flow

### Подтверждено кодом

- Перевод в `WAITING_FOR_MERCHANT`;
- отправка preview;
- фиксация approve/revision.

### Уточнение product rule

- Merchant approval завершает только этап согласования.
- Само опубликование допустимо только если мерчант уже publication-ready.
- Publication-ready для текущего MVP означает:
  - мерчант существует;
  - у него есть active `primaryLocation`;
  - в `primaryLocation` заполнен адрес.

## 15.4. Coupon approval flow

### Подтверждено кодом

- Финальное customer-facing опубликование наступает после merchant approve, когда купон становится `ACTIVE`.

### Уточнение product rule

- Публикация не должна auto-fill merchant data.
- Если publication-ready merchant отсутствует, approve-path должен завершаться бизнес-ошибкой, а не silent publish.

## 15.5. Content editing rights

### Подтверждено кодом

- Строго ограничены статусами и ownership checks.

## 15.6. Rejection reasons

### Подтверждено кодом

- Merchant revision сохраняет комментарий в `revisionComment`.

## 15.7. Visibility override / manual hide-show

### Подтверждено кодом

- Явной отдельной ручной hide/unhide модели сверх статусов не найдено.

### Открытый вопрос

- Нужен ли admin override visibility вне основного lifecycle?

## 15.8. Audit / safety

### Подтверждено кодом

- Audit logs существуют как super-admin модуль.

---

## 16. Detailed Page-by-Page Description

## 16.1. Home

### Подтверждено кодом

Назначение:

- discovery-витрина.

Аудитория:

- guest и registered user.

Основные блоки сверху вниз:

1. hero + search;
2. категории;
3. топ-акции;
4. новые;
5. основная лента;
6. bazaar CTA.

Главные CTA:

- “Смотреть предложения”
- переходы в карточку купона
- переход в bazaar.

SEO:

- страница должна индексироваться как основная посадочная витрина.

## 16.2. Coupon Catalog

### Подтверждено кодом

Назначение:

- массовый browsing и intent-search.

Блоки:

1. hero;
2. search + sort toolbar;
3. category chips;
4. result count;
5. listing;
6. pagination.

Состояния:

- loading;
- empty;
- normal.

## 16.3. Coupon Detail

### Подтверждено кодом

Назначение:

- conversion page.

Основные блоки:

1. breadcrumbs;
2. gallery;
3. merchant/title actions;
4. stats;
5. tabs;
6. detail content;
7. purchase options;
8. related offers.

Above the fold:

- gallery + title + price context + main CTA.

Контакты:

- contacts tab виден только авторизованному пользователю;
- источник контактов и адреса — `merchant.primaryLocation`, а не coupon-level contact fields.

## 16.4. Cart

### Подтверждено кодом

Блоки:

- header;
- items list;
- quantity controls;
- summary card;
- CTA checkout;
- empty state.

## 16.5. Checkout

### Подтверждено кодом

Блоки:

- top header/back;
- guest contacts step;
- payment methods;
- card form;
- order summary sidebar.

## 16.6. Profile

### Подтверждено кодом

Блоки:

- profile header;
- stats;
- tabs;
- purchased coupons list;
- empty states.

## 16.7. Favorites

### Подтверждено кодом

Блоки:

- title/count;
- empty state или grid карточек.

## 16.8. Search

### Подтверждено кодом

Блоки:

- header + search bar;
- suggestion categories;
- popular queries;
- coupon results;
- shop results;
- empty state.

## 16.9. Bazaar Directory

### Подтверждено кодом

Блоки:

- header;
- search and type filters;
- sidebar bazaar list;
- optional standalone shop section;
- map toolbar;
- area results panel.

## 16.10. Bazaar Detail

### Подтверждено кодом

Блоки:

- back link;
- hero;
- info/meta;
- map;
- shop search;
- shops grid.

## 16.11. Shop Detail

### Подтверждено кодом

Блоки:

- back link;
- hero/info card;
- description;
- assortment;
- gallery;
- map for standalone;
- contacts sidebar.

## 16.12. Admin Coupon Kanban

### Подтверждено кодом

Блоки:

- header/actions;
- kanban columns by status;
- coupon cards with action buttons.

## 16.13. Admin Coupon Form

### Подтверждено кодом

Блоки:

- header/status tag;
- main left column with content;
- right sidebar with dates, flags и options;
- save CTA;
- merchant quick-create modal.

### Уточнение product rule

- Coupon form больше не является источником contact data оффера.
- Контакты заведения управляются через merchant profile / `merchant_locations`.
- Quick-create merchant modal остается частью coupon flow, но сохраняет контактные данные как `primaryLocation`.

## 16.14. Partner Applications

### Подтверждено кодом

Блоки:

- list;
- filters/pagination;
- actions approve/reject.

---

## 17. Business Rules

Ниже правила в формате “условие → последствие”.

### BR-C-001

### Подтверждено кодом

Если купон не `ACTIVE`, он не должен попадать в публичный каталог.

### BR-C-002

### Рекомендация

Если пользователь открывает прямую ссылку на non-public купон, система должна показывать controlled unavailable state, а не полноценную продающую карточку.

### BR-C-003

### Подтверждено кодом

Купон может перейти из `LEAD` в `DRAFT` только через действие “взять в работу”.

### BR-C-004

### Подтверждено кодом

Купон может перейти в `WAITING_FOR_MERCHANT` только из `DRAFT` или `REVISION_REQUESTED`.

### BR-C-005

### Подтверждено кодом

Купон может стать `ACTIVE` только после merchant approval из `WAITING_FOR_MERCHANT`.

### Уточнение product rule

Merchant approval сам по себе недостаточен: переход в `ACTIVE` разрешён только если мерчант publication-ready.

Для текущего MVP publication-ready означает:

- есть мерчант;
- есть active `primaryLocation`;
- в `primaryLocation` заполнен адрес.

### BR-C-006

### Подтверждено кодом

Купон может перейти в `REVISION_REQUESTED` только из `WAITING_FOR_MERCHANT`.

### BR-C-007

### Подтверждено кодом

`SOLD_OUT` возникает автоматически при достижении лимита продаж.

### BR-C-008

### Подтверждено кодом

Редактировать купон можно только в `DRAFT`, `REVISION_REQUESTED`, `ACTIVE`.

### BR-C-009

### Подтверждено кодом

`MODERATOR` не может редактировать купон, закрепленный за другим модератором.

### BR-C-010

### Подтверждено кодом

Удаление купона запрещено для `ACTIVE` и `WAITING_FOR_MERCHANT`.

### BR-U-001

### Подтверждено кодом

Guest может сохранять купоны в избранное, но только до 10.

### BR-U-002

### Подтверждено кодом

Авторизованный пользователь может хранить до 50 favorite items.

### BR-U-003

### Подтверждено кодом

Контакты купона видны только авторизованному пользователю.

### BR-U-004

### Рекомендация

Если контакты скрыты для guest, карточка должна явно объяснять причину и предлагать login/registration.

### BR-O-001

### Подтверждено кодом

Заказ создается из серверной корзины.

### BR-O-002

### Рекомендация

Storefront не должен оставаться в состоянии, где local cart и server cart расходятся; нужно выбрать и зафиксировать одну каноническую модель.

### BR-R-001

### Подтверждено кодом

Новый отзыв всегда создается как `PENDING`.

### BR-R-002

### Подтверждено кодом

Публично показываются только `APPROVED` отзывы.

### BR-M-001

### Подтверждено кодом

Media GET публичен, upload требует auth, delete требует admin-level access.

### BR-P-001

### Подтверждено кодом

Partner application создается в `PENDING`.

### BR-P-002

### Подтверждено кодом

Partner application может стать только `APPROVED` или `REJECTED`.

### BR-D-001

### Подтверждено кодом

Directory API должен отдавать только активные bazaar/shop сущности.

### BR-LOC-001

### Рекомендация

Выбранный город должен влиять на каталог, directory и map-выдачу как минимум на уровне фильтра или ранжирования.

### BR-LOC-002

### Рекомендация

Если объект не имеет валидных geo-координат, он не должен участвовать в map display, но может оставаться в list view с явной пометкой.

---

## 18. Data / Entity Definitions

### Подтверждено кодом

Ключевые сущности:

- `User`
- `PartnerApplication`
- `Merchant`
- `Category`
- `CouponOffer`
- `CouponOption`
- `CouponImage`
- `Review`
- `Cart`
- `Order`
- `PurchasedCoupon`
- `RefundRequest`
- `Payment`
- `Notification`
- `Bazaar`
- `Shop`
- `BazaarMap`

### Рекомендация

Canonical relationships для MVP:

- User → favorites / orders / purchased coupons / notifications
- Merchant → coupon offers
- CouponOffer → options / images / reviews
- PurchasedCoupon → order / redemption
- Bazaar → shops
- Shop → merchant or linked coupon context (если применяется)

---

## 19. User Flows

## 19.1. Guest discovers coupon

### Подтверждено кодом

Start:

- home, catalog, search.

Steps:

1. browse list;
2. open detail;
3. inspect terms;
4. add to favorites/cart or proceed to buy.

Alternative:

- if guest needs contacts, must authenticate.

## 19.2. User saves coupon

### Подтверждено кодом

1. press favorite;
2. coupon stored locally or synced if authenticated;
3. user sees item in `/favorites`.

## 19.3. User uses coupon

### Подтверждено кодом

1. user purchases offer;
2. receives purchased coupon;
3. partner/admin redeems by code;
4. status becomes `USED`.

### Рекомендация

- UX должен явным образом объяснять шаг 3 пользователю.

## 19.4. User browses bazaar

### Подтверждено кодом

1. opens directory;
2. filters/searches;
3. opens bazaar;
4. searches shop inside bazaar;
5. opens shop detail.

## 19.5. User finds merchant on map

### Подтверждено кодом

1. opens map;
2. chooses area;
3. sees results;
4. opens bazaar or shop.

## 19.6. User reports incorrect info

### Рекомендация

Target flow for MVP:

1. user clicks “Сообщить о проблеме”;
2. chooses issue type;
3. writes comment;
4. system creates moderation task.

---

## 20. Merchant Flows

## 20.1. Merchant registration / lead

### Подтверждено кодом

Current flow:

- partner application or Telegram bot lead.

## 20.2. Merchant profile completion

### Подтверждено кодом / Уточнение

- Полноценного merchant web-account completion flow пока нет.
- Но для coupon flow уже существует обязательный operational минимум:
  - staff должен довести merchant до publication-ready состояния до публикации купона;
  - для этого нужен active `primaryLocation` с адресом.

## 20.3. Coupon submission

### Подтверждено кодом

- В MVP staff создает/редактирует купон, merchant его согласует.

### Уточнение product rule

- Merchant в MVP не обязан собирать весь оффер самостоятельно.
- Merchant-side участие ограничено согласованием, запросом правок и получением stats через bot.
- Финальная публикация зависит не только от approve, но и от readiness merchant profile.

## 20.4. Revision handling

### Подтверждено кодом

- merchant can send revision comment from Telegram;
- staff edits and resubmits.

## 20.5. Stats follow-up

### Подтверждено кодом

- merchant can request stats by bot.

---

## 21. Admin Flows

## 21.1. Review merchant application

### Подтверждено кодом

1. open partner applications;
2. inspect submission;
3. approve or reject.

## 21.2. Review and build coupon

### Подтверждено кодом

1. create or receive lead;
2. take to work;
3. fill card;
4. send to merchant;
5. resolve revision if needed;
6. publish after merchant approval.

### Уточнение product rule

Между шагами `5` и `6` должен соблюдаться дополнительный gate:

- перед публикацией staff обязан убедиться, что merchant publication-ready;
- если у мерчанта нет active `primaryLocation` с адресом, publish должен быть заблокирован.

## 21.3. Manage taxonomy

### Подтверждено кодом

1. create categories;
2. import categories.

## 21.4. Manage staff

### Подтверждено кодом

1. create admin/moderator;
2. change role;
3. block/unblock;
4. inspect audit.

---

## 22. States and Transitions

## 22.1. Merchant / partner application

### Подтверждено кодом

- `PENDING` — новая заявка
- `APPROVED` — одобрена
- `REJECTED` — отклонена

## 22.2. Coupon offer

### Подтверждено кодом

- `LEAD` — входящий лид / сырой оффер
- `DRAFT` — staff взял в работу
- `WAITING_FOR_MERCHANT` — отправлен мерчанту
- `REVISION_REQUESTED` — мерчант запросил правки
- `ACTIVE` — доступен пользователям
- `SOLD_OUT` — лимит продаж исчерпан

### Уточнение product rule

- `ACTIVE` означает не просто “merchant approved”, а “merchant approved + merchant publication-ready”.

## 22.3. Review

### Подтверждено кодом

- `PENDING`
- `APPROVED`
- `REJECTED`

## 22.4. Purchased coupon

### Подтверждено кодом

- `ACTIVE`
- `USED`
- `EXPIRED`
- `CANCELLED`

## 22.5. Order

### Подтверждено кодом

- `PENDING`
- `PAID`
- `COMPLETED`
- `CANCELLED`
- `REFUND_REQUESTED`
- `REFUNDED`

---

## 23. Edge Cases

### Рекомендация

Ожидаемое поведение для MVP:

- Если купон истек во время просмотра, purchase CTA блокируется, а пользователь видит “Срок покупки истек”.
- Если мерчант стал неактивным, его купоны должны скрываться из каталога после отдельной product rule валидации.
- Если нет адреса, объект может показываться в списке, но карта должна явно сообщать об отсутствии точной точки.
- Если нет координат, карта не показывает marker.
- Если изображение сломано, используется placeholder.
- Если категория невалидна или выключена, купон не должен попадать в каталог.
- Если поиск не дал результатов, показывается конкретный empty state с советом изменить запрос.
- Если пользователь не авторизован для gated action, UI объясняет ограничение и предлагает login.
- Если linked object deleted/inactive, карточка должна переходить в unavailable state.
- Если approved object отредактирован в критичных полях, должна существовать rule-based reapproval policy.
- Если bazaar пустой, detail page показывает корректный empty state, а не “мертвую” страницу.
- Если API недоступен, UI не должен silently маскировать production state демо-данными без явного указания среды.

---

## 24. Error States

### Рекомендация

Ключевые ошибки и ожидаемая реакция:

- Validation error:
  - user sees inline errors;
  - staff sees field-level explanation.
- Auth failure:
  - redirect to login or blocked action explanation.
- Permission denial:
  - dedicated forbidden state for admin-app;
  - contextual lock state for storefront.
- Network/API failure:
  - retry CTA;
  - stable non-breaking layout.
- Not found:
  - user-friendly 404 or unavailable page.
- Expired content:
  - explicit unavailable-with-context state.
- Payment failure:
  - order remains recoverable, user sees retry path.
- Moderation conflict:
  - staff sees actual current status and last action source.

---

## 25. UX Requirements

### Подтверждено референсом

- Купон должен быть легко читаемым.
- Доверие и условия должны быть не спрятаны, а вынесены в явные блоки.
- География и merchant context критичны.

### Рекомендация

- Mobile-first responsiveness обязателен.
- Навигация должна быть простой: coupons vs bazaar.
- CTA должны быть явными и единообразными.
- Empty states должны объяснять, что делать дальше.
- Search and filters должны быть discoverable без дополнительного обучения.
- Карточка и detail page должны сохранять визуальную и смысловую преемственность.
- Внутренние staff-статусы не должны перегружать пользовательские экраны.

---

## 26. Content Requirements

### Рекомендация

Обязательные тексты на coupon card:

- title;
- merchant name;
- price/economy;
- category or type context;
- optional trust indicator.

Обязательные тексты на coupon detail:

- что входит;
- условия;
- как использовать;
- до какого числа купить;
- до какого числа использовать;
- где использовать;
- кому показать/что предъявить.

Обязательные empty state тексты:

- нет результатов поиска;
- нет активных купонов;
- корзина пуста;
- избранное пусто;
- в выбранной области нет объектов.

Принципы CTA:

- коротко;
- ориентировано на следующий шаг;
- без двусмысленности.

---

## 27. Non-Functional Requirements

### Рекомендация

- Производительность:
  - first-screen pages должны открываться быстро на мобильном интернете.
- Адаптивность:
  - корректная работа на mobile и desktop.
- Доступность:
  - базовые aria labels, keyboard-friendly interactive controls.
- Локализация:
  - минимум `ru` и `uz`.
- Надежность:
  - graceful handling API failures.
- Безопасность:
  - role-based access, token refresh, header validation, restricted media delete.
- Наблюдаемость:
  - audit logs для staff actions;
  - операционные события по купонам/заказам/редемпшну.
- Data quality:
  - публикация не должна зависеть от невалидных или пустых критичных полей.

---

## 28. Metrics / KPIs

### Рекомендация

- views coupon detail;
- CTR coupon card → detail;
- add to favorites rate;
- add to cart rate;
- checkout start rate;
- order creation rate;
- payment success rate;
- purchased coupon redemption rate;
- refund rate;
- review creation rate;
- directory search usage;
- map area selection usage;
- bazaar detail → shop detail CTR;
- merchant lead volume;
- lead → active coupon conversion;
- moderation cycle time;
- revision rate from merchant;
- sold-out rate.

---

## 29. MVP Scope

### Рекомендация

Включить в MVP:

- storefront home/catalog/detail;
- favorites;
- basic checkout with one canonical cart/order model;
- profile with purchased coupons;
- coupon lifecycle through staff + merchant approval;
- partner application or Telegram lead as declared merchant entry;
- categories;
- bazaar directory with map and bazaar/shop detail;
- basic notifications for critical purchase events;
- staff roles, audit and coupon moderation.

### Условно включить

- limited merchant stats via bot;
- limited partner shops management, если подтвердится product ownership этого направления.

### Не включать в MVP как обязательное

- полноценный merchant web cabinet;
- full complaints/reviews support UI;
- advanced loyalty / wallet / referral;
- deep analytics dashboards for merchants;
- unified search across every entity if это ломает MVP сроки.

---

## 30. Post-MVP Scope

### Рекомендация

- merchant public pages;
- merchant self-service creation/editing;
- полноценный notification center;
- complaints workflow;
- advanced map features and clustering;
- stronger city-aware catalog;
- richer search ranking and suggestions;
- user review UI on storefront;
- richer order/backoffice panels;
- canonical bazaar domain consolidation if не успевает в MVP.

---

## 31. Risks

### Риск

- Дублирование bazaar/domain логики в двух сервисах.
- Расхождение storefront cart и backend order pipeline.
- Устаревшая внутренняя документация.
- Смесь реальных и mock/fallback-данных на публичном фронтенде.
- Неявная продуктовая граница merchant-side функциональности.
- Неопределенность с city/location rules.
- Route-level permission inconsistencies в admin-app.
- Неявный direct-link behavior для non-public coupon statuses.

---

## 32. Open Questions

### Открытый вопрос

- В какой момент и через какой flow из merchant lead создается полноценный `PARTNER` account?
- Какой bazaar/service layer canonical для public experience?
- Какая модель checkout canonical?
- Как должны вести себя direct links на non-public контент?
- Требуется ли повторное согласование после редактирования активного купона?
- Что видеть пользователю при `SOLD_OUT` и при выключенном мерчанте?
- Каков официальный статус города в product logic?
- Нужен ли отдельный merchant page в MVP?
- Нужен ли отдельный user report flow в MVP?
- Нужен ли полноценный notification center в MVP?

---

## 33. Glossary

### Подтверждено кодом / Рекомендация

- **Купон** — коммерческий оффер с условиями, ценой, сроками и purchase options.
- **Опция купона** — конкретный вариант покупки внутри купона.
- **Мерчант / партнер** — владелец предложения и/или торговой точки.
- **Базар** — крупный торговый объект-контейнер для магазинов и навигации по точкам.
- **Магазин / shop** — конкретная торговая точка внутри базара или standalone-объект.
- **Directory** — публичный контур базаров и магазинов.
- **Lead** — сырой входящий оффер или merchant request до полноценной подготовки.
- **Модерация** — операционный staff-процесс подготовки и контроля качества.
- **Согласование мерчантом** — этап, на котором партнер подтверждает или возвращает оффер на доработку.
- **Purchased coupon** — экземпляр купленного пользователем купона.
- **Redemption** — факт использования купона у партнера.
- **Sold out** — состояние, когда лимит продаж исчерпан.
- **Guest user** — неавторизованный или временно авторизованный пользователь с ограниченными правами.
- **Staff** — внутренняя команда: moderator/admin/super-admin.

---

## Заключение

### Подтверждено кодом

TopDim уже содержит значимый продуктовый фундамент, но он сформирован из нескольких пересекающихся контуров: consumer coupons, directory/map, staff moderation и merchant bot-flow.

### Рекомендация

Дальнейшая разработка должна опираться на этот PRD и сопровождающие research-документы как на новую продуктовую базу, а не на устаревшие markdown-описания отдельных фич. Главный ближайший результат работы команды после принятия PRD — привести storefront, admin, merchant-flow и backend rules к одной согласованной модели источников правды, статусов и пользовательских ожиданий.
