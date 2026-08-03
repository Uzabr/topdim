# Scrum-пространство проекта SizBiz

> **Бренд продукта — SizBiz** (домены `sizbiz.uz`, `api.sizbiz.uz`, `admin.sizbiz.uz`, `partner.sizbiz.uz`).
> `topdim` — внутреннее кодовое имя монорепозитория, Docker-стека и БД (`topdim_identity`, `topdim_coupon`, ...).
> В Linear, коммуникации со стейкхолдерами и любых внешних артефактах используется **только SizBiz**.

Этот раздел — операционная документация того, **как команда работает**, а не того, **что делает продукт**.
Продуктовые требования живут в `docs/product/prd.md`, техническое состояние — в `docs/PROJECT_STATE.md`.

---

## Карта документов

| # | Документ | Для кого | Что внутри |
|---|---|---|---|
| 01 | [Устав проекта](01-project-charter.md) | Все | Зачем существует проект, цели, метрики успеха, границы, стейкхолдеры, риски |
| 02 | [Роли и зоны ответственности](02-roles-and-responsibilities.md) | Все | Product Owner, Scrum Master, Delivery Manager, Developers. Кто что решает, RACI |
| 03 | [Definition of Ready](03-definition-of-ready.md) | PO, команда | Когда задачу можно брать в спринт |
| 04 | [Definition of Done](04-definition-of-done.md) | Команда, SM | Когда задача считается завершённой |
| 05 | [События Scrum](05-ceremonies.md) | Все | Планирование, дейли, ревью, ретро, груминг: тайминги, повестка, артефакты |
| 06 | [Workflow и оценка](06-workflow-and-estimation.md) | Команда | Статусы задач, story points, velocity, WIP-лимиты, работа с багами |
| 07 | [Настройка Linear](07-linear-setup.md) | Scrum Master | Пошаговая конфигурация workspace `sizbiz`: проекты, labels, статусы, циклы, шаблоны |
| 08 | [Product Backlog](08-product-backlog.md) | PO, команда | Эпики и user stories с AC и оценками — готовы к переносу в Linear |
| 09 | [Шаблоны](09-templates.md) | Все | Issue, bug, spike, sprint goal, ретро, стейкхолдер-апдейт, инцидент |
| 10 | [Метрики и отчётность](10-metrics-and-reporting.md) | SM, PO, менеджер | Что измеряем, как читаем, какие решения принимаем |

---

## Быстрый старт для Scrum Master

1. Прочитать [01-project-charter.md](01-project-charter.md) и [02-roles-and-responsibilities.md](02-roles-and-responsibilities.md).
2. Настроить Linear по [07-linear-setup.md](07-linear-setup.md) — примерно 60–90 минут разовой работы.
3. Перенести эпики и истории из [08-product-backlog.md](08-product-backlog.md) в Linear.
4. Провести первое Sprint Planning по повестке из [05-ceremonies.md](05-ceremonies.md).
   Первый спринт начинается с двух спайков-сверок — см. [08](08-product-backlog.md).

## Быстрый старт для Product Owner

1. [01-project-charter.md](01-project-charter.md) — цели и метрики.
2. [08-product-backlog.md](08-product-backlog.md) — приоритизированный backlog, свериться и скорректировать порядок.
3. [03-definition-of-ready.md](03-definition-of-ready.md) — чек-лист перед тем, как задача попадёт в планирование.
4. Закрыть открытые продуктовые вопросы из §7 устава — они блокируют несколько эпиков.

---

## Основные параметры процесса

| Параметр | Значение |
|---|---|
| Фреймворк | Scrum (Scrum Guide 2020) |
| Длина спринта (Cycle в Linear) | **2 недели**, старт — понедельник |
| Размер команды | 5 человек (базовый расчёт; при другом размере WIP-лимиты и capacity пересчитываются) |
| Оценка | Story points, шкала Фибоначчи (1, 2, 3, 5, 8, 13) |
| Инструмент | Linear, workspace `sizbiz` |
| Структура Linear | Проект = продуктовый milestone, Cycle = спринт |
| Язык артефактов | Русский (код, коммиты, API — английский) |

---

## Дисциплина поддержки документов

- Устав проекта пересматривается **раз в квартал** или при смене бизнес-цели.
- DoR/DoD пересматриваются **на ретроспективе раз в 3 спринта**.
- Backlog в этом файле — **снимок на дату создания**. После переноса в Linear источником правды становится Linear, а `08-product-backlog.md` помечается как исторический.
- Технические факты (порты, сервисы, статус деплоя) **не дублируются** здесь — только ссылки на `docs/PROJECT_STATE.md`.
