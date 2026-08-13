# Frontend Documentation

Этот раздел для frontend-разработчиков sizbiz. Перед изменениями сначала
проверь продуктовый флоу, затем API contract, потом код нужного приложения.

## Приложения

| App | Путь | Назначение | Основной стек |
|---|---|---|---|
| Web app | `frontend/web-app` | публичный сайт, каталог, покупка, профиль пользователя | React 19, Vite 8, React Router 6, Zustand, React Query, Zod |
| Admin app | `frontend/admin-app` | админка, модерация, merchant operations | React 19, Vite 8, React Router 7, Ant Design, React Query |
| Partner app | `frontend/partner` | отдельный партнёрский портал | React 19, Vite 8, React Router 7, Ant Design, React Query |

`frontend/web-app.bak` — backup старого web-app. Его нельзя считать актуальным приложением при ревью фич и документации.

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
npm test
npm run build
npm run lint

cd ../partner
npm test
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
9. При необходимости создаёт отзыв, жалобу или запрос на возврат и видит уведомления.

### Партнёр

1. Оставляет заявку на партнёрской landing page.
2. Получает аккаунт после обработки админом.
3. Заходит в partner app.
4. Создаёт заявку на купон.
5. Смотрит статус модерации.
6. Согласовывает готовый купон или просит правки.
7. Владелец или менеджер открывает «Моя компания», создаёт полный снимок профиля
   и филиалов, сохраняет/предпросматривает его и отправляет на модерацию.
8. После возврата на доработку исправляет ту же заявку; терминальную заявку может
   скопировать на актуальную версию. Кассир раздел компании не видит.
9. Кассир гасит купоны по PIN/QR.

## Известные frontend gaps

- В `admin-app` `/users/list` подключён. UI базаров/магазинов и промокодов не
  показывается и остаётся отдельным roadmap-модулем вне текущего admin MVP.
- В `web-app` checkout/profile/payment flow реализован, но перед релизом нужен e2e smoke: каталог → корзина → checkout → demo payment → profile coupons → partner redemption.
- В `web-app` избранное синхронизируется localStorage ↔ backend; при правках auth/favorites обязательно проверять merge guest favorites после login.
- `admin-app`, `partner` и `web-app` имеют Vitest regression suites. Для
  профиля компании дополнительно обязателен ручной multi-role acceptance:
  OWNER + MANAGER + CASHIER + MODERATOR.
- В admin detail изменений компании показываются текущий снимок, field/location
  diff и moderation comment, но пока нет полной ленты переходов из history table.
- Admin UI не получает отдельный preflight с количеством активных кассиров или
  опубликованных предложений; запрет отключения филиала с кассиром проверяется
  backend при submit/approve и отображается как бизнес-ошибка.
- Поле загрузки логотипа/обложки ограничено `image/*` только на клиенте.
  media-service пока не валидирует MIME/signature/размер файла на сервере, поэтому
  это не считается достаточной production-защитой.
- Admin reassign сейчас вводится как числовой user ID; backend не проверяет роль и
  активность нового исполнителя через identity-service. Невалидное назначение
  можно исправить release/reassign, но перед production нужен staff selector и
  серверная проверка.
- Production bundles partner/admin превышают 500 kB gzip-warning threshold;
  сборка успешна, но route-level code splitting остаётся performance-задачей.

### Админ / модератор

1. Проверяет партнёрские заявки.
2. Создаёт или дополняет мерчанта.
3. Берёт coupon request в работу.
4. Готовит купон.
5. Отправляет на согласование партнёру.
6. Публикует, архивирует или снимает с продажи по правилам статусов.
7. В отдельном разделе «Изменения компаний» фильтрует заявки, берёт одну в работу,
   сравнивает опубликованный и предложенный профиль и одобряет, возвращает на
   доработку либо отклоняет. ADMIN/SUPER_ADMIN также может освободить или
   переназначить заявку.

## Что проверять перед сдачей frontend-задачи

- `npm run build` проходит в изменённом приложении.
- Нет моковых отзывов, фейковых метрик или выдуманных бизнес-данных в production UI.
- Empty/loading/error states понятны пользователю.
- Protected pages корректно ведут гостя на login.
- Public pages доступны без авторизации.
- Mobile layout не ломает основные CTA.
- При изменении формы есть клиентская валидация и понятная ошибка backend validation.
