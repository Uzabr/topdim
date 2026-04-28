# Ответы на вопросы по Admin Panel (Канбан-доска)

---

## 1. Библиотека UI-компонентов

**Ant Design v6** (`antd: ^6.3.5`) + **@ant-design/icons v6**.

Вся админка построена на Ant Design: таблицы, формы, модалки, Layout, Menu, Button, Tag, Select и т.д. Никаких Tailwind, Bootstrap или Material UI — только Ant Design.

**Файл-подтверждение:** `frontend/admin-app/package.json`

---

## 2. Управление состоянием и запросами

Используются **три библиотеки**:

| Библиотека | Версия | Назначение |
|---|---|---|
| **@tanstack/react-query** | `^5.95.2` | Серверный стейт: запросы `useQuery`, мутации `useMutation`, кэш-инвалидация `queryClient.invalidateQueries` |
| **Axios** | `^1.14.1` | HTTP-клиент, обёрнут в `src/api/client.ts` с interceptors для JWT и auto-refresh |
| **Zustand** | `^5.0.12` | Клиентский стейт: хранение auth-токенов (`src/store/authStore.ts`) |

### Паттерн отправки запросов:

```tsx
// Загрузка данных:
const { data } = useQuery({
  queryKey: ['admin-coupons'],
  queryFn: () => api.get('/api/v1/admin/coupons').then(res => res.data.data)
});

// Мутация (создание/обновление):
const mutation = useMutation({
  mutationFn: (payload) => api.post('/api/v1/admin/coupons', payload),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-coupons'] })
});
```

Все запросы идут через `api` (axios instance) → `http://localhost:8080` (API Gateway) → микросервисы.

---

## 3. Drag-and-Drop

**Сейчас НЕ установлено** ни одной DnD-библиотеки. В `package.json` нет `dnd-kit`, `react-beautiful-dnd` или аналогов.

### Рекомендация:

**Вариант А (простой — без DnD):** Кнопки перемещения внутри карточки ("Отправить на согласование", "Одобрить", "Запросить правки"). Это быстрее, проще, и лучше подходит под нашу State Machine, где переходы строго ограничены.

**Вариант Б (с DnD):** Если нужно перетаскивание — рекомендую `@dnd-kit/core` + `@dnd-kit/sortable` (современная, поддерживаемая библиотека). Но с нашей моделью перетаскивание не очень подходит — нельзя перекинуть купон из DRAFT сразу в ACTIVE, перескочив WAITING_FOR_MERCHANT.

**Мой вердикт:** Вариант А (кнопки) — лучше для консьерж-модели.

---

## 4. Связь с формой (навигация)

### Сейчас реализовано:

| Действие | Роут | Компонент |
|---|---|---|
| Список купонов | `/moderation/coupons` | `CouponsListPage.tsx` |
| Создать новый | `/moderation/coupons/create` | `CouponFormPage.tsx` |
| Редактировать | `/moderation/coupons/edit/:id` | `CouponFormPage.tsx` |

### Рекомендация для Канбан-доски:

- **Клик по карточке** → `navigate(`/moderation/coupons/edit/${id}`)` — открывает **CouponFormPage** в режиме редактирования (уже работает, определяет edit-режим по наличию `id` в URL).
- **Кнопка "Создать"** → `navigate('/moderation/coupons/create')`.
- Канбан-доска заменяет или дополняет `CouponsListPage` — это страница-список, которая сейчас на `/moderation/coupons`.

Модальное окно **не нужно** — форма слишком большая (20+ полей, загрузка файлов). Переход на отдельную страницу — правильный подход.

---

## 5. Структура папок

### Текущая структура:

```
frontend/admin-app/src/
├── api/
│   └── client.ts                   # Axios instance
├── components/
│   └── layout/
│       └── AdminLayout.tsx         # Sidebar + header layout
├── features/
│   ├── auth/                       # Login, Forbidden
│   ├── catalog/                    # Категории
│   ├── coupons/                    # 👈 Купоны
│   │   ├── CouponsListPage.tsx     # Текущий список (таблица)
│   │   └── CouponFormPage.tsx      # Форма создания/редактирования
│   ├── dashboard/                  # Дашборд
│   ├── partners/                   # Заявки партнёров
│   └── system/                     # Staff, Audit
├── hooks/
├── routes/
├── store/
└── types/
```

### Рекомендация для Канбан-доски:

```
frontend/admin-app/src/features/coupons/
├── CouponsListPage.tsx         # Текущий список (можно оставить как альтернативный view)
├── CouponFormPage.tsx          # Форма создания/редактирования
└── CouponKanbanPage.tsx        # 👈 НОВЫЙ ФАЙЛ — Канбан-доска
```

Файл лежит в **существующей** папке `features/coupons/` — это логично, так как Канбан-доска — это другой view тех же купонов.

Роут: `/moderation/coupons/kanban` или заменить `/moderation/coupons`.

---

## Сводка решений

| Вопрос | Ответ |
|---|---|
| UI-библиотека | **Ant Design v6** |
| Запросы | **TanStack Query v5** + **Axios** + **Zustand** |
| Drag-and-Drop | **Без DnD** — кнопки перемещения (State Machine ограничивает произвольные переходы) |
| Навигация в форму | **Отдельная страница** `/moderation/coupons/edit/:id` (уже работает) |
| Расположение файла | `src/features/coupons/CouponKanbanPage.tsx` |
