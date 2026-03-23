# Frontend Web App — Документация для разработчиков

> React SPA (mobile-first) для платформы TopDim

## Стек

| Компонент | Технология | Версия |
|---|---|---|
| Framework | React | 19.2 |
| Build Tool | Vite | 6.x |
| Routing | React Router | 7.x |
| State | Zustand | 5.0 |
| HTTP Client | Axios | 1.13 |
| Map | Leaflet + React-Leaflet | 1.9 |
| Validation | Zod | 4.3 |
| Icons | Lucide React | — |

## Запуск

```bash
cd frontend/web-app
npm install
npm run dev     # http://localhost:5173
```

## Структура проекта

```
frontend/web-app/src/
├── api/                  # API клиенты (Axios)
│   ├── client.ts         # Axios instance (baseURL, interceptors)
│   ├── auth.ts           # register, login, refresh, logout
│   ├── coupons.ts        # getCatalog, getCouponById, getCategories
│   ├── orders.ts         # cart, checkout, orders, coupons
│   └── bazaars.ts        # getBazaars, getShops
│
├── components/
│   ├── layout/           # Header, Footer, TabSwitcher
│   ├── coupon/           # CouponCard, CouponGrid, CouponFilters
│   └── cart/             # CartDrawer, CartItem
│
├── pages/                # Route pages
│   ├── HomePage.tsx      # Hero, категории, табы (Купоны/Базар)
│   ├── CouponCatalogPage.tsx  # Каталог с фильтрами и пагинацией
│   ├── CouponDetailPage.tsx   # Карточка купона
│   ├── CartPage.tsx      # Корзина
│   ├── CheckoutPage.tsx  # Оформление заказа
│   ├── ProfilePage.tsx   # Мои купоны (активные/использованные)
│   ├── BazaarMapPage.tsx # Карта базаров (Leaflet)
│   ├── BazaarDetailPage.tsx   # Детали базара + магазины
│   ├── ShopDetailPage.tsx     # Карточка магазина
│   ├── SearchPage.tsx    # Поиск купонов
│   └── LoginPage.tsx     # Вход / Регистрация
│
├── store/                # Zustand stores
│   ├── authStore.ts      # user, token, login/logout actions
│   └── cartStore.ts      # items, addToCart, removeFromCart
│
├── hooks/
│   └── useFormatPrice.ts # Форматирование цен (UZS)
│
├── utils/
│   └── format.ts         # Утилиты форматирования
│
├── styles/
│   └── index.css         # Глобальные стили + CSS variables
│
├── assets/               # Изображения (hero, логотипы)
├── App.tsx               # Router + Layout
├── main.tsx              # Entry point
└── index.css             # Root CSS
```

## API Layer (`api/`)

### client.ts — Axios Instance
```typescript
// Base URL: http://localhost:8080 (через API Gateway)
// Interceptors:
//   - Request: добавляет Authorization: Bearer {token}
//   - Response: при 401 → refresh token или redirect to login
```

### Endpoints используемые из backend

| Модуль | Метод | URL | Описание |
|---|---|---|---|
| auth | POST | `/api/v1/auth/login` | Вход |
| auth | POST | `/api/v1/auth/register` | Регистрация |
| auth | POST | `/api/v1/auth/refresh` | Обновить токен |
| coupons | GET | `/api/v1/coupons` | Каталог (page, categoryId, search) |
| coupons | GET | `/api/v1/coupons/{id}` | Детали купона |
| coupons | GET | `/api/v1/categories` | Категории |
| coupons | GET | `/api/v1/coupons/top-selling` | Топ продаж |
| orders | GET | `/api/v1/cart` | Корзина |
| orders | POST | `/api/v1/cart/items` | Добавить в корзину |
| orders | DELETE | `/api/v1/cart/items/{id}` | Удалить из корзины |
| orders | POST | `/api/v1/orders` | Оформить заказ |
| orders | GET | `/api/v1/orders/my-coupons` | Мои купоны |
| bazaars | GET | `/api/v1/bazaars` | Список базаров |
| bazaars | GET | `/api/v1/bazaars/{id}` | Детали базара |
| bazaars | GET | `/api/v1/bazaars/{id}/shops` | Магазины базара |

## State Management (Zustand)

### authStore
```typescript
// state: user, accessToken, refreshToken, isAuthenticated
// actions: login(email, password), register(...), logout(), refreshAuth()
```

### cartStore
```typescript
// state: items[], totalAmount
// actions: addToCart(item), removeItem(id), clearCart(), checkout()
```

## Роутинг (React Router)

| URL | Страница | Auth |
|---|---|---|
| `/` | HomePage | ❌ |
| `/coupons` | CouponCatalogPage | ❌ |
| `/coupons/:id` | CouponDetailPage | ❌ |
| `/cart` | CartPage | ✅ |
| `/checkout` | CheckoutPage | ✅ |
| `/profile` | ProfilePage | ✅ |
| `/bazaar` | BazaarMapPage | ❌ |
| `/bazaar/:id` | BazaarDetailPage | ❌ |
| `/shop/:id` | ShopDetailPage | ❌ |
| `/search` | SearchPage | ❌ |
| `/login` | LoginPage | ❌ |

## Стили

- **Подход:** Vanilla CSS + CSS Variables (дизайн-токены)
- **Mobile-first:** Все страницы адаптированы под мобильные устройства
- **Каждая страница** имеет свой `.css` файл (HomePage.css, CartPage.css и т.д.)

### CSS Variables (из index.css)
```css
:root {
  --primary: #2563eb;
  --primary-dark: #1d4ed8;
  --secondary: #f59e0b;
  --success: #10b981;
  --danger: #ef4444;
  --bg: #f8fafc;
  --text: #1e293b;
  --text-light: #64748b;
  --border: #e2e8f0;
  --radius: 12px;
  --shadow: 0 2px 8px rgba(0,0,0,0.08);
}
```

## TODO (из аудита)

- [ ] UI Kit (`components/ui/` — Button, Card, Modal, Input, Toast)
- [ ] Bazaar components (InteriorMap SVG)
- [ ] i18n (react-i18next, ru/uz)
- [ ] React Hook Form + Zod (формы)
- [ ] CSS Modules
- [ ] Admin Panel (`frontend/admin-app/`)
