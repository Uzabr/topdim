# Admin launch readiness report

## Вердикт

`admin-app` готов к запусковой ручной приёмке купонного MVP. Автоматическая
матрица зелёная, известных P0-дефектов в текущей границе не осталось. Production
sign-off пока не дан: полный demo-data сценарий из
[admin-launch-manual-test-plan.md](admin-launch-manual-test-plan.md) ещё не выполнен.

## Соответствие продукту

Текущая структура панели соответствует операционной модели:

- MODERATOR: купоны, отзывы, жалобы;
- ADMIN: дополнительно возвраты, мерчанты, категории, заказы, пользователи и
  партнёрские заявки;
- SUPER_ADMIN: дополнительно staff и аудит действий с сотрудниками;
- PARTNER не входит в admin-app и работает в отдельном partner-app.

Добавлять перед приёмкой новые разделы не требуется. UI базаров/магазинов,
промокодов, системных настроек и финансовой отчётности — отдельный roadmap scope,
а не незавершённые пункты текущего меню. Partner redemption и partner approval
не должны возвращаться в admin-app.

## Что проверено автоматически

- весь Gradle-монорепозиторий: `./gradlew test --no-daemon`;
- identity/coupon/order business tests, PostgreSQL Testcontainers и JaCoCo;
- admin-app: полный Vitest suite, ESLint и production build;
- web-app: 149 Vitest tests, ESLint и production build;
- partner-app: ESLint и production build;
- demo launcher contract, Bash syntax и admin demo seed contract;
- полный branch diff без whitespace errors и с актуализированной продуктовой
  документацией.

## Открытые пункты

### До production sign-off

1. Выполнить ручной план с demo-data для трёх staff-ролей.
2. Зафиксировать результаты, screenshots и response bodies.
3. Исправить все P0/P1, повторить regression и дождаться зелёного remote CI.

### Принятые ограничения текущего MVP

- `Аудит сотрудников` содержит identity staff actions, но не является единым
  аудитом купонов, возвратов и support; actor email и client IP не возвращаются.
- Возврат завершается ручным подтверждением фактической выплаты; production
  payment provider не подключён.
- У жалоб нет take-to-work/assignment операции, поэтому `IN_REVIEW` не используется.
- Email/SMS delivery работает в stub/disabled режиме.
- Partner-app пока не имеет собственного автоматизированного component suite.
- Vite предупреждает о крупных JS chunks; это performance debt, не функциональный
  launch blocker для текущей приёмки.
- В web-app остаётся одно lint warning `useMemo` без lint error, вне изменённого
  admin scope.

## Решение о запуске

| Условие | Статус |
|---|---|
| Реализация текущего admin MVP | PASS |
| Автоматические regression/build checks | PASS |
| Локальные staff demo accounts | PASS |
| Ручная role/business приёмка | NOT RUN |
| Remote CI текущего финального commit | PENDING |
| Production launch | HOLD до ручной приёмки и CI |
