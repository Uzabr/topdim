# Frontend Documentation

Этот раздел для frontend-разработчиков TopDim. Перед изменениями сначала проверь продуктовый флоу, затем API contract, потом код нужного приложения.

## Приложения

| App | Путь | Назначение | Основной стек |
|---|---|---|---|
| Web app | `frontend/web-app` | публичный сайт, каталог, покупка, профиль пользователя | React 19, Vite, React Router 6, Zustand, React Query, Zod |
| Admin app | `frontend/admin-app` | админка, модерация, merchant operations | React 19, Vite, React Router 7, Ant Design, React Query |
| Partner app | `frontend/partner` | отдельный партнёрский портал | React 19, Vite, React Router 7, Ant Design, React Query |

Подробная документация по `web-app` сохранена в [web-app.md](web-app.md).

## Локальные skills

При задачах во frontend используй инструкции из:

- `frontend/.agent/skills/react/SKILL.md`
- `frontend/.agent/skills/react-hook-form-zod/SKILL.md`
- `frontend/.agent/skills/frontend-design/SKILL.md`

Для форм предпочтительно использовать React Hook Form + Zod там, где проект уже идёт этим путём. Для UI внутри существующего приложения сохраняй текущую дизайн-систему и не делай редизайн без отдельного решения.

## Команды

```bash
cd frontend/web-app
npm run build
npm run lint

cd ../admin-app
npm run build
npm run lint

cd ../partner
npm run build
npm run lint
```

## API правила

- Все клиентские запросы должны идти через общий `api/client`.
- Backend возвращает `ApiResponse<T>`, поэтому frontend должен читать данные через `response.data.data`.
- Для защищённых действий нужен JWT.
- Гостевой просмотр публичных страниц не должен агрессивно редиректить на login, если пользователь просто смотрит каталог или купон.
- При изменении DTO сначала сверяйся с [backend/api-contract.md](../backend/api-contract.md).

## Ключевые пользовательские флоу

### Покупатель

1. Открывает каталог купонов.
2. Открывает детальную страницу купона.
3. Выбирает вариант купона.
4. Добавляет в корзину или покупает.
5. Проходит checkout.
6. Подтверждает demo/payment.
7. Видит купон в профиле.
8. Показывает партнёру PIN/QR.

### Партнёр

1. Оставляет заявку на партнёрской landing page.
2. Получает аккаунт после обработки админом.
3. Заходит в partner app.
4. Создаёт заявку на купон.
5. Смотрит статус модерации.
6. Согласовывает готовый купон или просит правки.
7. Кассир гасит купоны по PIN/QR.

### Админ / модератор

1. Проверяет партнёрские заявки.
2. Создаёт или дополняет мерчанта.
3. Берёт coupon request в работу.
4. Готовит купон.
5. Отправляет на согласование партнёру.
6. Публикует, архивирует или снимает с продажи по правилам статусов.

## Что проверять перед сдачей frontend-задачи

- `npm run build` проходит в изменённом приложении.
- Нет моковых отзывов, фейковых метрик или выдуманных бизнес-данных в production UI.
- Empty/loading/error states понятны пользователю.
- Protected pages корректно ведут гостя на login.
- Public pages доступны без авторизации.
- Mobile layout не ломает основные CTA.
- При изменении формы есть клиентская валидация и понятная ошибка backend validation.
