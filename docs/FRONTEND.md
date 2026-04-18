# Frontend Web App — Документация для разработчиков

> React SPA (mobile-first) для платформы TopDim

## Стек

| Компонент | Технология | Версия |
|---|---|---|
| Framework | React | 19.2 |
| Build Tool | Vite | 6.x |
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

## Структура проекта

```text
frontend/web-app/src/
├── api/             # API клиенты (Axios)
│   ├── _client.ts   # Axios instance (baseURL, interceptors). Обрабатывает JWT.
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
├── locales/         # JSON-файлы с переводами 
│   ├── ru.json
│   └── uz.json
│
├── pages/           # Страницы Маршрутизатора
│   ├── HomePage.tsx           # Главная страница
│   ├── CouponCatalogPage.tsx  # Каталог с фильтрацией
│   ├── CouponDetailPage.tsx   # Детальная страница купона
│   ├── CartPage.tsx           # Корзина покупок
│   ├── CheckoutPage.tsx       # Оформление заказа
│   ├── ProfilePage.tsx        # Профиль и мои приобретенные купоны
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

### _client.ts — Axios Instance
```typescript
// Base URL: извлекается из import.meta.env, либо fallback на "http://localhost:8080/api/v1"
// Interceptors:
//   - Request: Добавляет `Authorization: Bearer {token}` если юзер залогинен из localStorage
//   - Response: Перехватчик ошибок для обработки Token Expiration и логики рефреша
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
- **`favoritesStore`**: список избранного, ограничение 5 товаров для неавторизованных пользователей.
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
| `/:lang/checkout` | CheckoutPage | ❌ |
| `/:lang/profile` | ProfilePage | ✅ |
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
