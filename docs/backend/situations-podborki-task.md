# Backend-таск: «Ситуации / подборки» для главной («Что хотите сегодня?»)

> Статус: **не реализовано на бэкенде.** Фронтенд уже рендерит секцию по макету
> (заглушка-данные в `frontend/web-app/src/data/situations.ts`), но полноценная
> управляемая подборка требует бэкенда. Этот файл — пошаговый план для реализации
> в **отдельной ветке** (`feature/coupon-situations`).
>
> Сервис-владелец: **coupon-service** (`services/coupon-service`). Ситуации
> группируют купоны → это доменная зона coupon-service, не отдельный сервис и не
> кросс-сервисный доступ к чужой БД.

## 1. Контекст и зачем это

**Ситуация** — навигация по жизненному сценарию/интенту (design_handoff_sizbiz →
«Дизайн-документация», раздел «10.6 Навигация»): «Отдохнуть с детьми», «Привести
себя в порядок», «Проверить здоровье», «Сходить на ужин», «Выходные за городом».

Это **не категории** (категории — отдельный механизм, фильтр-табы ленты). На
макете «Главная - образец» ситуации — крупные плитки: первая тёмная на 2 колонки
(«ситуация-хиро»), остальные светлые, у каждой картинка, заголовок и счётчик
«N купонов».

**Что сейчас (заглушка на фронте):** 5 ситуаций захардкожены во фронтенде, клик
ведёт на `/search?q=<слово>`, счётчик берётся из реального поиска каталога
(`totalElements`) и скрывается при нуле. Фейковых чисел нет, но:
- набор ситуаций и привязка к купонам не управляются из админки;
- у ситуаций нет собственных картинок;
- счётчик = грубый поиск по слову, а не точный размер кураторского набора.

**Цель таска:** сделать ситуации управляемой сущностью с API, чтобы фронтенд
получал список из бэкенда (заголовок ru/uz, картинка, точный счётчик, порядок), а
клик открывал каталог, отфильтрованный по ситуации.

## 2. Решение по модели данных (выбрать перед стартом)

- **Вариант A — явная кураторская привязка** (рекомендую для MVP): таблица
  `situations` + join-таблица `situation_coupons(situation_id, coupon_id, sort_order)`.
  Полный редакторский контроль, точный счётчик = `COUNT` активных купонов в наборе.
  Минус: новые купоны не попадают в ситуацию автоматически — их назначает админ.
- **Вариант B — правило** (динамический набор): у ситуации — фильтр
  (`category_id` и/или поисковые теги), купоны подбираются запросом.
  Плюс: новые купоны попадают сами. Минус: слабее курирование, счётчик зависит от
  качества правила.
- **Вариант C — гибрид:** правило по умолчанию + ручные pin/exclude.

Дальнейшие шаги описаны под **Вариант A** (проще всего дать точный счётчик и
предсказуемый набор). Если выберете B/C — шаги 3–6 меняются на уровне схемы и
запроса выборки, контракт API (шаг 7) остаётся тем же.

## 3. Миграция БД (Flyway)

Следующий свободный номер — **V22** (последняя сейчас `V21__add_version_to_coupon_options.sql`).
Файл: `services/coupon-service/src/main/resources/db/migration/V22__create_situations.sql`.

```sql
CREATE TABLE situations (
    id          BIGSERIAL PRIMARY KEY,
    key         VARCHAR(64) NOT NULL UNIQUE,   -- стабильный слаг: kids, beauty, ...
    title       VARCHAR(128) NOT NULL,         -- ru
    title_uz    VARCHAR(128),                  -- uz
    image_url   VARCHAR(512),
    featured    BOOLEAN NOT NULL DEFAULT FALSE,-- тёмный хиро на 2 колонки
    sort_order  INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE situation_coupons (
    situation_id BIGINT NOT NULL REFERENCES situations(id) ON DELETE CASCADE,
    coupon_id    BIGINT NOT NULL REFERENCES coupon_offers(id) ON DELETE CASCADE,
    sort_order   INT NOT NULL DEFAULT 0,
    PRIMARY KEY (situation_id, coupon_id)
);

-- Частый фильтр «купоны ситуации» → индекс по coupon_id для обратных выборок.
CREATE INDEX idx_situation_coupons_coupon ON situation_coupons(coupon_id);
```

> Проверьте фактическое имя таблицы купонов (`coupon_offers`) по существующим
> миграциям перед FK. Уникальность `key` — критично (фронт обращается по ключу).

## 4. Entity + repository

Шаблон — фича `Category` (`entity/Category.java`, `repository/CategoryRepository.java`,
кэш в Redis TTL 1 час).

1. `entity/Situation.java` — поля из таблицы (`@Table(name = "situations")`).
   Связь с купонами — `@ManyToMany` через `situation_coupons` **или** отдельная
   entity `SituationCoupon` с `sort_order` (предпочтительно, чтобы хранить порядок).
2. `repository/SituationRepository.java`:
   - `List<Situation> findByActiveTrueOrderBySortOrderAsc();`
   - метод счётчика активных купонов на ситуацию — одним запросом на все ситуации
     (избегаем N+1), например проекция `situationId → count` через
     `@Query` с `GROUP BY` и join на купоны со `status = 'ACTIVE'` и не истёкшим
     `buy_until`. Не делайте отдельный запрос в цикле по ситуациям.

## 5. Service

`service/SituationService.java`:
- `@Transactional(readOnly = true)` метод `getActiveSituations()` →
  список DTO с посчитанным `couponCount` (одним агрегирующим запросом, п.4).
- Кэшируйте так же, как категории (Redis TTL ~1 час); инвалидация при изменении
  ситуации или её набора купонов.
- Учтите пустые ситуации: `couponCount = 0` — фронт скрывает счётчик, но саму
  плитку показывать/нет решите флагом (например, не отдавать неактивные).

## 6. Выборка купонов ситуации

Расширить существующий каталог, а не плодить эндпоинт:
`GET /api/v1/coupons` (`CouponController`) — добавить необязательный параметр
`situation=<key>`. Внутри — join на `situation_coupons` по ключу ситуации, с теми
же пагинацией/сортировкой/статус-фильтрами, что и обычный каталог. Так фронт-плитка
сможет вести на каталог, отфильтрованный по ситуации, а не на грубый `search`.

## 7. Контракт API (фронт ждёт именно это)

**`GET /api/v1/situations`** — публичный, кэшируемый. Ответ (обёртка `ApiResponse`
как у остальных эндпоинтов):

```json
{
  "data": [
    {
      "key": "kids",
      "title": "Отдохнуть с детьми",
      "titleUz": "Bolalar bilan dam olish",
      "imageUrl": "https://.../kids.jpg",
      "couponCount": 64,
      "featured": true,
      "sortOrder": 0
    }
  ]
}
```

DTO: `dto/SituationResponse.java`; маппинг в `mapper/`. Контроллер:
`controller/SituationController.java` (публичный) — по образцу `CategoryController`.

## 8. Админка (можно вторым этапом)

`controller/AdminSituationController.java` (по образцу `AdminCategoryController`):
CRUD ситуаций + назначение/снятие купонов и их порядок в наборе. Защита — та же
ролевая модель, что у `Admin*Controller` (проверьте `security/` и существующие
аннотации ролей; **не ослабляйте** доступ ради простоты).

## 9. Сид данных

Мигрировать 5 ситуаций из продуктовой доки (10.6) как стартовый сид (отдельная
миграция `V23__seed_situations.sql` или data-seeder, как принято в проекте):
`kids`, `beauty`, `health`, `dinner`, `weekend` — с ru/uz заголовками (см.
`frontend/web-app/src/locales/{ru,uz}.json` → `home.situations.*`) и `featured=true`
у `kids`. Привязку купонов заполнит админ.

## 10. Тесты (обязательно)

- Unit: `SituationService` — счётчик, порядок, пустые/неактивные ситуации.
- Slice `@DataJpaTest`: репозиторий-агрегат счётчика (нет N+1), FK-каскады.
- `@WebMvcTest`: `GET /api/v1/situations` — форма ответа, пустой список.
- Каталог с `?situation=` — фильтрация + пагинация; негативный кейс: несуществующий
  ключ → пустая страница, не 500.
- Testcontainers для БД-тяжёлых кейсов, если уже используется в сервисе.

## 11. Интеграция на фронте (после готовности API — отдельный фронт-PR)

Меняется только источник данных, разметка/CSS уже готовы:
1. `frontend/web-app/src/api/coupons.ts` — добавить `getSituations()` +
   тип `Situation` (`key/title/titleUz/imageUrl/couponCount/featured/sortOrder`).
2. `src/data/situations.ts` — **удалить** (заглушка больше не нужна).
3. `src/components/home/Situations.tsx` и блок плиток в `src/pages/SearchMobile.tsx`
   — брать список из `useQuery(getSituations)` вместо `SITUATIONS`; счётчик —
   `couponCount` из API; картинку-фон подставить из `imageUrl` (сейчас плитки без фото).
4. Ссылку плитки перевести на каталог с фильтром ситуации
   (`/coupons?situation=<key>`) — при этом каталог должен читать фильтр из URL
   (сейчас `CouponCatalogPage` его игнорирует — см. примечание ниже).

## Примечание (смежный фронт-баг, вне этого таска)

`frontend/web-app/src/pages/CouponCatalogPage.tsx` **не читает** query-параметры
(`categoryId`, `search`) из URL — фильтр держится только в локальном `useState`.
Поэтому существующие ссылки вида `/coupons?categoryId=…` не фильтруют каталог. Для
шага 11.4 (ссылка `?situation=`) это нужно будет починить (`useSearchParams` →
начальное состояние фильтра). Заведите отдельной задачей, если чините не здесь.
