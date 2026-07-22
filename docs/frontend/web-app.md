# Frontend Web App — Документация для разработчиков

> React SPA (mobile-first) для платформы TopDim

## Стек

| Компонент | Технология | Версия |
|---|---|---|
| Framework | React | 19.2 |
| Build Tool | Vite | 8.x |
| Routing | React Router DOM | 6.30 |
| State | Zustand | 5.0 |
| Server State | TanStack React Query | 5.94 |
| HTTP Client | Axios | 1.13 |
| Map | 2GIS MapGL | 1.72 |
| Validation | React Hook Form + Zod | 7.72 / 4.3 |
| Icons | Lucide React | 0.577 |
| i18n | react-i18next | 16.6 |

## Запуск

```bash
cd frontend/web-app
npm install
npm run dev     # Автоматически запустит сервер на http://localhost:5173
```

## Структура репозитория

В монорепозитории лежат три актуальных React-приложения:
- `frontend/web-app/` — основной клиентский портал (магазин, каталог, карта, базары). Запускается на порту `5173` (dev) / `80` (prod).
- `frontend/admin-app/` — панель управления для `MODERATOR`, `ADMIN`, `SUPER_ADMIN`. Запускается на порту `3001`.
- `frontend/partner/` — партнёрский портал для владельца и кассира. Запускается на порту `3002`.

`frontend/web-app.bak/` — backup и не является источником актуального frontend-кода. Документация ниже сфокусирована на `web-app`, так как он имеет более сложную структуру стейта и публичный buyer flow.

```text
frontend/web-app/src/
├── api/             # API клиенты (Axios)
│   ├── client.ts    # Axios instance (baseURL, interceptors). Обрабатывает JWT.
│   ├── auth.ts      # Авторизация: register, login
│   ├── bazaars.ts   # Работа со справочником (базары, магазины)
│   ├── coupons.ts   # Запросы к купонам и категориям
│   └── orders.ts    # Заказы, корзина
│
├── components/
│   ├── cart/        # Корзина
│   ├── coupon/      # Карточки купонов (CouponCard)
│   ├── directory/   # Карточки базаров и магазинов
│   ├── layout/      # Header, Footer, BottomNav, LocaleLayout
│   ├── map/         # TwoGisMap (инкапсуляция карты 2GIS)
│   └── ui/          # UI Kit: кнопки, модалки, селекторы, LanguageSelector
│
├── hooks/
│   └── useLocalePath.ts # Хук для локализации параметров роутинга
│
├── i18n.ts          # react-i18next init
├── i18n/config.ts   # RU/UZ resources
│
├── pages/           # Страницы Маршрутизатора
│   ├── HomePage.tsx           # Главная страница
│   ├── CouponCatalogPage.tsx  # Каталог с фильтрацией
│   ├── CouponDetailPage.tsx   # Детальная страница купона (состоит из Hero, Info, Variants, Reviews)
│   ├── CartPage.tsx           # Корзина покупок
│   ├── CheckoutPage.tsx       # Оформление заказа
│   ├── PaymentPage.tsx        # Demo/provider payment UX
│   ├── ProfilePage.tsx        # Профиль, купоны, заказы, возвраты, жалобы, уведомления
│   ├── FavoritesPage.tsx      # Избранные купоны
│   ├── BazaarMapPage.tsx      # Карта базаров (2GIS) со списком
│   ├── BazaarDetailPage.tsx   # Детали базара и список магазинов
│   ├── ShopDetailPage.tsx     # Детали магазина
│   ├── SearchPage.tsx         # Экран поиска
│   ├── LoginPage.tsx          # Вход / Регистрация
│   ├── NotFoundPage.tsx       # 404
│   └── legal/                 # Информационные страницы
│       ├── FAQPage.tsx
│       ├── PartnersPage.tsx
│       ├── PrivacyPage.tsx
│       └── TermsPage.tsx
│
├── store/           # Zustand stores
│   ├── authStore.ts       # user, login, register, logout
│   ├── cartStore.ts       # корзина, добавление, удаление, оформление
│   ├── cityStore.ts       # выбор города
│   ├── directoryStore.ts  # справочник базаров и магазинов
│   ├── marketplaceStore.ts # состояние маркетплейс-каталога
│   └── favoritesStore.ts  # логика избранного (с лимитом для гостя)
│
├── utils/           # formatPrice, parseDate и прочие утилиты
├── App.tsx          # Главный роутер + <LocaleLayout>
├── index.css        # Глобальные CSS-токены дизайн-системы Chocolife
└── main.tsx         # Рендер-корневой узел, QueryClientProvider, Store Init
```

## API Layer (`api/`)

### client.ts — Axios Instance
```typescript
// Base URL: import.meta.env.VITE_API_URL || ''
// В dev обычно задаётся VITE_API_URL=http://localhost:8080.
// Без env запросы идут same-origin, что удобно для prod за Nginx/API gateway.
// Interceptors:
//   - Request: Добавляет `Authorization: Bearer {token}` если юзер залогинен из localStorage
//   - Response: 
//       - 401 Unauthorized: Проверяет, был ли у пользователя токен до этого. Если токена не было (гость), то перехватчик игнорирует ошибку, чтобы не прерывать просмотр публичных страниц (например, отзывов на купон). Если токен был — пытается сделать refresh или разлогинивает.
```

## State Management

В проекте используется два слоя State Management:

### 1. Серверное состояние (TanStack React Query v5)
Данные, которые мы получаем с API (каталоги, детали, локации), контролируются через React Query (`useQuery`, `useMutation`). 
- Кэширование на стороне клиента.
- Мгновенная загрузка страниц из кеша.

### 2. Клиентское состояние (Zustand)
Состояние UI, которое не связано с кэшированием API:
- **`authStore`**: токен аутентификации, статус `isLoading`, пользовательские данные.
- **`cartStore`**: товары в корзине, общая стоимость, сохраняется в localStorage.
- **`favoritesStore`**: список избранного, ограничение 10 товаров для гостя и 50 для авторизованного пользователя; после login localStorage избранное синхронизируется в backend.
- **`cityStore`**: выбор города (Ташкент).
- **`directoryStore`**: кэш базаров и магазинов для справочника.
- **`marketplaceStore`**: состояние фильтров и сортировки в каталоге купонов.

## Роутинг (React Router v6)

Маршрутизация локализована — все запросы проходят через структуру `:lang/*`. 
Написание навигации требует оборачивания пути в `useLocalePath()`!

| Локализованный Путь | Страница | Требует Auth |
|---|---|---|
| `/:lang/` | HomePage | ❌ |
| `/:lang/coupons` | CouponCatalogPage | ❌ |
| `/:lang/coupons/:id` | CouponDetailPage | ❌ |
| `/:lang/cart` | CartPage | ❌ |
| `/:lang/checkout` | CheckoutPage | ✅ |
| `/:lang/payment/:orderId` | PaymentPage | ✅ |
| `/:lang/profile` | ProfilePage | ✅ |
| `/:lang/confirm-email` | EmailConfirmationPage | ❌ (подтверждение по токену публичное) |
| `/:lang/favorites` | FavoritesPage | ❌ |
| `/:lang/bazaar` | BazaarMapPage | ❌ |
| `/:lang/bazaar/:id` | BazaarDetailPage | ❌ |
| `/:lang/shops/:id` | ShopDetailPage | ❌ |
| `/:lang/login` | LoginPage | ❌ |
| `/:lang/search` | SearchPage | ❌ |
| `/:lang/partners` | legal/PartnersPage | ❌ |
| `/:lang/faq` | legal/FAQPage | ❌ |
| `/:lang/terms` | legal/TermsPage | ❌ |
| `/:lang/privacy` | legal/PrivacyPage | ❌ |

*(При переходе в корень `/` происходит редирект на сохраненный язык локали)*

## Стили (Дизайн-система)

- **Подход:** Vanilla CSS + Глобальные переменные (CSS Variables). Разработка по BEM-подобной методологии (Component-level isolation). 
- **Адаптивность:** Mobile-first (Сначала оптимизация под экраны телефонов, затем под десктоп).
- Tailwind **не используется**, чтобы обеспечить максимальную кастомизацию анимаций, градиентов (например Chocolife style) и чистоту DOM.

### Пример CSS Variables (из index.css)
```css
:root {
  /* Палитра */
  --bg-default: #f3f4f6;
  --bg-surface: #ffffff;
  --text-primary: #1f2937;
  --text-secondary: #6b7280;

  /* Градиенты акцентные */
  --gradient-primary: linear-gradient(135deg, #FF6660 0%, #FF3D33 100%);
  --gradient-secondary: linear-gradient(135deg, #1A1A1A 0%, #333333 100%);

  /* Скругления и пространство */
  --space-md: 16px;
  --radius-md: 12px;
  --radius-pill: 100px;
}
```

## Работа с Формами

Для работы с формами используется подход **Controlled Components** + валидация.
- Провайдер: `react-hook-form`
- Валидатор: `zodResolver(zodSchema)`
- Маска для телефонов: `react-imask`

Показ ошибок реализован через абсолютное позиционирование подсказок (`.form-group`), чтобы скрыть/показать ошибку без прыжка всего контента под формой.

## Зависит от бэкенда (TODO — не забыть реализовать)

UI подготовлен под поля, которых пока нет в API. Фронт использует их **при наличии**, иначе показывает честный фолбэк (без фейковых данных). Маркеры — `TODO(backend)` в `src/api/orders.ts`.

| Поле | Где нужно | Сейчас (фолбэк) | Что добавить на бэкенде |
|---|---|---|---|
| `PurchasedCoupon.pricePaid?: number` | Тикет купона в профиле (`CouponTicket`) — макет показывает уплаченную цену «49 000 сум» (16px bold) | `optionTitle` (название опции) | order-service: отдать уплаченную цену позиции (`unitPrice` на момент покупки) в `GET /api/v1/orders/my-coupons` |
| `OrderResponse.title?: string` | Строка заказа в профиле (`ProfileDesktop`/`ProfileMobile`) — макет ведёт строку названием оффера | `Заказ №{orderNumber}` | order-service: добавить представительное название (первая позиция + «и ещё N») в `OrderResponse` |

Когда поля появятся в ответах API — UI автоматически начнёт показывать их как в макете, доработки фронта не потребуется.

## Backend готов: статус подключения UI

| Фича | Backend-контракт | Статус web-app |
|---|---|---|
| Q&A на странице купона | `POST /api/v1/questions`, `GET /api/v1/questions/coupon/{offerId}`, `GET /api/v1/questions/my`; модерация в `admin-app` через `/api/v1/mod/questions` | В `frontend/web-app` Q&A-компонент и `questions` API-клиент пока не найдены; нужно добавить блок на `CouponDetailPage`/мобильной детали и форму вопроса для авторизованного пользователя |
| Избранное | `GET/POST/DELETE /api/v1/users/me/favorites` | ✅ Гидратация и объединение с гостевым localStorage подключены при старте авторизованной сессии |
| Смена пароля | `PUT /api/v1/auth/change-password` | ✅ Форма, backend-совместимая валидация и обязательный повторный вход подключены |
| Аватар | `POST /api/v1/media/upload` + `PUT /api/v1/users/me` | ✅ Загрузка, клиентские ограничения и рендер во всех местах профиля подключены |
| Подтверждение email | `POST /api/v1/auth/confirm/request` + `POST /api/v1/auth/confirm/email` | ✅ Статус, запрос кода и маршрут `/:lang/confirm-email` подключены |
