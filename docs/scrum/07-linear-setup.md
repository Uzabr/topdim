# 07. Настройка Linear — workspace `sizbiz`

> **Исполнитель:** Scrum Master. **Время:** 60–90 минут разовой настройки.
> **Исходное состояние:** команда создана, проектов нет (`https://linear.app/sizbiz/projects/all`).
> Каждый шаг — что сделать и **почему именно так**.

---

## Шаг 0. Проверка команды

Settings → Teams. Убедиться, что:

- Название команды — **SizBiz** (не TopDim).
- Идентификатор (issue prefix) короткий и осмысленный: рекомендуется `SIZ` → задачи будут `SIZ-1`, `SIZ-2`.
- Часовой пояс команды — **Asia/Tashkent**.
- Приглашены все участники с ролью Member; SM и PO — Admin.

---

## Шаг 1. Включить Cycles (спринты)

Settings → Teams → SizBiz → **Cycles** → Enable.

| Параметр | Значение | Почему |
|---|---|---|
| Cycle length | **2 weeks** | Решение команды, см. устав |
| Start day | **Monday** | Планирование в понедельник утром |
| Cooldown | 0 | Работаем непрерывно, буфер заложен в capacity (0.85) |
| Upcoming cycles | 3 | Видимость на 6 недель вперёд, достаточно для груминга |
| Auto-add issues to current cycle | **Off** | Объём спринта определяет команда на планировании, не автоматика |
| Auto-archive completed | On, через 2 недели | Чистая доска |

> **Важно:** незавершённые задачи Linear по умолчанию переносит в следующий цикл. Это удобно, но опасно —
> команда перестаёт замечать carryover. На Sprint Review SM явно фиксирует, что перенеслось и почему.

---

## Шаг 2. Настроить Workflow States

Settings → Teams → SizBiz → **Workflow**. Привести к структуре из [06-workflow-and-estimation.md](06-workflow-and-estimation.md):

| Название | Категория | Цвет |
|---|---|---|
| Backlog | Backlog | серый |
| Blocked | Backlog | красный |
| Todo | Unstarted | серый |
| In Progress | Started | жёлтый |
| In Review | Started | оранжевый |
| QA / Verify | Started | фиолетовый |
| Done | Completed | зелёный |
| Canceled | Canceled | серый |

Ключевое отличие от дефолта: добавлены **Blocked**, **In Review** и **QA / Verify**.
Без `In Review` не видно, что ревью стало узким местом. Без `Blocked` заблокированные задачи
маскируются под «в работе».

---

## Шаг 3. Включить оценки

Settings → Teams → SizBiz → **Estimates**:

- Enable estimates: **On**
- Scale: **Fibonacci** (1, 2, 3, 5, 8, 13)
- Allow zero: **Off** — задача с нулевой оценкой искажает velocity
- Default estimate for new issues: none — оценивает команда, а не система

---

## Шаг 4. Создать проекты-milestone'ы

Projects → New project. **Проект = продуктовый milestone**, не сервис и не спринт.

Порядок соответствует приоритету из backlog.

> **Терминология:** цель проекта ниже — это **milestone objective**, а не Product Goal.
> Product Goal в Scrum один и действует до достижения; его формулирует PO поверх этих milestone'ов.

| # | Проект | Цель milestone'а | Ориентир |
|---|---|---|---|
| 1 | **Trust & Safety Baseline** | Закрыть критические находки пентеста и убрать возможность обхода регистрации и утечки ключей | 2 спринта |
| 2 | **Sales Integrity** | Покупка купона корректна при конкурентной нагрузке; checkout-модель зафиксирована и реализована | 2 спринта |
| 3 | **Payments Go-Live** | Переход с demo-режима на реального платёжного провайдера | 3 спринта (после ответа на Q8) |
| 4 | **Directory Domain Consolidation** | Один canonical владелец домена bazaar/shop, справочник доступен через gateway, `bazaar-service` в CD | 2 спринта |
| 5 | **Account Lifecycle** | Верификация контактов, удаление аккаунта, настройки уведомлений — закрытие GDPR-подобных пробелов | 2 спринта |
| 6 | **Buyer Experience Reliability** | Профиль, история заказов и купленные купоны показывают правду и работают с бэкендом | 2 спринта |
| 7 | **Admin & Partner Completeness** | Админка без мёртвых пунктов меню, партнёр видит нужную статистику | 2 спринта |
| 8 | **Quality Automation** | Автотесты закрывают ключевые сценарии, CI их гоняет, покрытие растёт | сквозной, 3+ спринта |
| 9 | **Delivery & Observability** | Все сервисы в CD, метрики и логи собираются, инциденты видны до жалоб пользователей | сквозной |
| 10 | **Event Reliability** | События не теряются и не обрабатываются дважды | 2 спринта |
| 11 | **Discovery & Content** | Поиск, подборки, фильтры каталога работают как обещано в PRD | 2 спринта |
| 12 | **Documentation Truth** | PRD и документация соответствуют коду; пробелы GAP-01…GAP-14 закрыты | сквозной |

Для каждого проекта заполнить: **описание** (цель одним абзацем), **lead** (PO для продуктовых, тех-лид для
инфраструктурных), **target date** ориентировочно, **status** (`Planned` / `In Progress`).

> Проекты 8, 9, 12 — сквозные: они не завершаются, а живут постоянно. Для них target date не ставится.

---

## Шаг 5. Создать Labels

Settings → Teams → SizBiz → **Labels**. Группы лейблов вместо плоского списка.

### Группа `type`

`type/epic` · `type/story` · `type/bug` · `type/tech-debt` · `type/spike` · `type/chore` · `type/incident`

### Группа `area` (где именно код)

`area/identity` · `area/coupon` · `area/order` · `area/payment` · `area/bazaar` · `area/notification` ·
`area/media` · `area/gateway` · `area/discovery` · `area/config` · `area/web-app` · `area/admin-app` ·
`area/partner-app` (каталог `frontend/partner`) · `area/telegram-bot` · `area/infra` · `area/docs`

### Группа `risk`

`risk/security` · `risk/data-migration` · `risk/breaking-api` · `risk/money`

Эти четыре — сигнал «нужен второй ревьюер» по DoD.

### Служебные (без группы)

`blocked` · `retro-action` · `dod-debt` · `needs-po-decision` · `good-first-issue`

> `needs-po-decision` вешается на истории, заблокированные открытыми вопросами Q1–Q8 из устава.
> Фильтр по этому лейблу — рабочий список PO.

---

## Шаг 6. Настроить Triage

Settings → Teams → SizBiz → **Triage** → Enable.

Всё, что приходит извне (баг-репорты, запросы стейкхолдеров, интеграции), попадает в Triage,
а не сразу в backlog. Разбирает **PO, минимум раз в 2 дня**. Это защищает backlog от мусора.

---

## Шаг 7. Создать шаблоны Issue

Settings → Teams → SizBiz → **Templates** → New template.
Содержимое шаблонов — в [09-templates.md](09-templates.md). Нужны четыре:

1. **User Story** — формат «Как… я хочу… чтобы…», AC, DoR/DoD чек-лист.
2. **Bug Report** — шаги, ожидаемое/фактическое, окружение, severity.
3. **Tech Debt** — риск для production, текущее поведение, предлагаемое решение.
4. **Spike** — вопрос, таймбокс, ожидаемый артефакт, критерий завершения.

---

## Шаг 8. Настроить Views (сохранённые представления)

Views → New view. Минимальный набор:

| Название | Фильтр | Для кого |
|---|---|---|
| **Текущий спринт** | Cycle = current | Все, дефолтная доска |
| **Готово к планированию** | State = Todo, estimate is set | SM перед планированием |
| **Заблокировано** | State = Blocked OR label = blocked | SM ежедневно |
| **Ждёт решения PO** | label = needs-po-decision | PO |
| **Ревью висит** | State = In Review, updated > 1d ago | Вся команда |
| **Security-долг** | label = risk/security, State ≠ Done | SM, тех-лид |
| **Техдолг** | label = type/tech-debt, State ≠ Done | Команда на груминге |
| **Escaped defects** | label = type/bug, created after release | SM для ретро |

---

## Шаг 9. Интеграции

| Интеграция | Зачем | Настройка |
|---|---|---|
| **GitHub** | Ветки и PR автоматически связываются с задачами; PR merged → задача в `Done` | Settings → Integrations → GitHub, подключить репозиторий, включить branch-name linking и PR automation |
| **Slack / Telegram** | Уведомления о смене статуса, упоминаниях | По желанию команды; не делать шумным |
| **Calendar** | Циклы как события | Опционально |

**Автоматизации GitHub, которые стоит включить:**

- Открыт PR со ссылкой на задачу → задача переходит в `In Review`.
- PR смержен → задача переходит в `QA / Verify` (не сразу в `Done` — DoD требует проверки поведения).

---

## Шаг 10. Перенос backlog

1. Открыть [08-product-backlog.md](08-product-backlog.md).
2. Создать **эпики** как Issues с лейблом `type/epic`, привязать к соответствующему проекту.
   В Linear нет отдельной сущности «эпик» — используется parent/sub-issue: эпик становится parent-задачей.
3. Создать истории как **sub-issues** эпиков.
4. Проставить лейблы, приоритет, оценку, проект.
5. Истории, зависящие от Q1–Q8, пометить `needs-po-decision` и перевести в `Blocked`.

**Массовый импорт.** Linear поддерживает CSV-импорт (Settings → Import/Export) и API. Для 40+ историй
быстрее импортировать CSV с колонками `Title, Description, Priority, Estimate, Labels, Project, Status`,
затем вручную выставить parent-связи эпиков.

---

## Шаг 11. Настроить первый цикл

1. Cycles → текущий цикл → в описание вписать **Sprint Goal**.
2. Провести Sprint Planning по повестке из [05-ceremonies.md](05-ceremonies.md).
3. Перетащить выбранные задачи в цикл.
4. Проверить: сумма оценок ≈ capacity, резерв 15–20% свободен.

---

## Шаг 12. Проверочный чек-лист настройки

- [ ] Cycles включены, 2 недели, старт понедельник, auto-add выключен
- [ ] Workflow содержит Blocked, In Review, QA / Verify
- [ ] Estimates = Fibonacci, zero запрещён
- [ ] Созданы 12 проектов-milestone'ов с описанием и lead
- [ ] Созданы группы лейблов type / area / risk + служебные
- [ ] Triage включён, ответственный назначен
- [ ] 4 шаблона issue созданы
- [ ] 8 views сохранены
- [ ] GitHub подключён, автоматизации PR настроены
- [ ] Backlog перенесён, эпики связаны с историями
- [ ] Первый цикл имеет Sprint Goal в описании
- [ ] Нигде в публичных названиях не встречается `topdim` вместо `SizBiz`

---

## Соглашения по именованию в Linear

- **Проект:** существительное, обозначающее результат — «Sales Integrity», не «Работа над заказами».
- **Эпик:** область работы — «Атомарность продаж и остатков».
- **История:** «Как <роль>, я хочу <действие>, чтобы <ценность>».
- **Баг:** симптом + место — «Каталог игнорирует `categoryId` из query-параметра».
- **Спайк:** вопрос — «Выяснить, какие security-пункты реально открыты в коде».
- Язык — русский. Технические идентификаторы (имена сервисов, эндпоинтов, классов) — как в коде.
- **`topdim` допустим только** внутри технических деталей (имена БД, docker-сервисов), никогда — как имя продукта.
