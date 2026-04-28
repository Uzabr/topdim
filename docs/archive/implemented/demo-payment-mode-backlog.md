# Backlog разработки временного demo payment mode

## 1. Цель документа

Это рабочий backlog по временному сценарию:

`checkout -> payment(PENDING) -> demo confirm -> payment completed -> purchased coupons -> profile -> notifications`

Документ разбит по:

- этапам;
- backend/frontend/QA задачам;
- зависимостям;
- порядку выполнения;
- тому, что нельзя ломать при подключении реальной ПС позже.

Связанный план:

- [demo-payment-mode-implementation-plan.md](/Users/abror/Projects/topdim/docs/demo-payment-mode-implementation-plan.md)

---

## 2. Зафиксированные baseline-решения

### Подтверждено решением

- реальная ПС пока не подключается;
- payment domain model сохраняется;
- `OrderCreatedEvent -> Payment -> PaymentCompletedEvent -> PurchasedCoupon` остается канонической цепочкой;
- пользователь после успешной demo-покупки должен попадать в профиль / раздел покупок;
- email/SMS должны остаться в той же цепочке;
- wording “чек” нельзя использовать как подтвержденный юридический факт без отдельного согласования.

### Подтверждено кодом

В коде уже есть foundation, на которую должен опираться backlog:

- `payment-service` уже умеет читать payment по `orderId`;
- `PaymentPage` уже построена вокруг `orderId`;
- `PaymentCompletedEvent -> PurchasedCoupon` уже существует;
- `CouponPurchasedEvent -> Email/SMS` уже существует;
- notification-сервисы уже поддерживают stub mode.

### Следствие

Backlog должен добивать существующую цепочку, а не создавать новую параллельную оплату “мимо payment-service”.

---

## 3. Стратегия выполнения

### Рекомендация

Выполнять работу в таком порядке:

1. Сначала backend demo completion mode.
2. Потом frontend demo payment UX.
3. Потом success/profile flow.
4. Потом notifications / content alignment.
5. Потом QA hardening.

### Риск

- Если начать с frontend success screen без backend demo completion, команда получит очередной fake flow вместо рабочей цепочки.

---

## 4. Этап A — Backend demo completion foundation

## A1. Ввести demo/provider mode

### Тип

`Backend`

### Задачи

- Добавить конфигурационный флаг:
  - `payment.mode=demo|provider`
- Зафиксировать default для dev/demo environments.
- Запретить demo completion path в `provider` mode.

### Acceptance criteria

- backend однозначно знает, в каком payment режиме он работает.

## A2. Добавить demo completion endpoint

### Тип

`Backend`

### Задачи

- Добавить endpoint вроде:
  - `POST /api/v1/payments/order/{orderId}/demo-complete`
- Использовать `orderId` как внешний идентификатор checkout flow.
- Возвращать storefront-safe `PaymentResponse`.
- Добавить явную защиту по конфигу:
  - endpoint доступен только в `payment.mode=demo`.

### Acceptance criteria

- storefront может завершить payment без реального callback от ПС.

## A3. Переиспользовать completion chain

### Тип

`Backend`

### Задачи

- Не дублировать completion business logic.
- Demo completion должен использовать ту же логику:
  - перевод в `COMPLETED`;
  - публикация `PaymentCompletedEvent`.
- Не допускать обходной логики:
  - `checkout -> сразу PurchasedCoupon`
  - `frontend -> напрямую в profile без payment completion`

### Acceptance criteria

- нет отдельной “параллельной” ветки для demo purchases.

## A4. Идемпотентность demo completion

### Тип

`Backend`

### Задачи

- Повторный вызов не должен создавать:
  - duplicate payment completion;
  - duplicate purchased coupons;
  - duplicate notifications без контроля.
- Поведение повторного запроса должно быть заранее определено:
  - либо безопасный `200 OK` c уже завершенным платежом;
  - либо явный бизнес-ответ “платеж уже завершен”.

### Acceptance criteria

- повторное подтверждение безопасно и предсказуемо.

## A5. Зафиксировать технический exit path из demo-mode

### Тип

`Backend + Product`

### Задачи

- Описать, как demo endpoint выключается при переходе к реальной ПС.
- Убедиться, что `provider` mode не зависит от demo-specific данных.
- Зафиксировать, что order/payment domain model остается общей для обоих режимов.

### Acceptance criteria

- переход к реальной ПС не требует переписывать всю purchase архитектуру.

---

## 5. Этап B — Frontend demo payment UX

## B1. Определение режима на payment page

### Тип

`Frontend`

### Задачи

- На `PaymentPage` определить, что сейчас активен demo-mode.
- Выводить соответствующий сценарий без внешнего redirect.
- Не пытаться автоматически переводить пользователя на `paymentUrl`, если активен demo-mode.

### Acceptance criteria

- payment page не пытается вести пользователя в несуществующую реальную ПС.

## B2. Demo confirm CTA

### Тип

`Frontend`

### Задачи

- Показать кнопку:
  - `Подтвердить покупку`
- Кнопка вызывает demo completion endpoint.
- Добавить loading / disabled state.
- Добавить защиту от повторного клика во время in-flight запроса.

### Acceptance criteria

- пользователь может завершить покупку одним явным действием.

## B3. Demo messaging

### Тип

`Frontend + Content`

### Задачи

- Показать понятное пояснение:
  - это временный демонстрационный payment flow;
  - после подтверждения купоны появятся в профиле.
- Не использовать copy, который создает впечатление реальной банковской транзакции.

### Acceptance criteria

- пользователь понимает, что произойдет после нажатия.

## B4. Обработка пограничных состояний payment page

### Тип

`Frontend`

### Задачи

- Обработать сценарии:
  - payment уже `COMPLETED`;
  - payment еще не создан;
  - demo endpoint недоступен;
  - demo completion завершился ошибкой;
  - пользователь обновил страницу после success.

### Acceptance criteria

- `PaymentPage` остается предсказуемой в happy-path и основных error-path сценариях.

---

## 6. Этап C — Success и профиль

## C1. Success state после demo completion

### Тип

`Frontend`

### Задачи

- После успешного ответа показать success state.
- Показать:
  - номер заказа;
  - что покупка подтверждена;
  - где искать купоны.

### Acceptance criteria

- пользователь видит завершенный сценарий, а не “черную дыру”.

## C2. Переход в профиль / покупки

### Тип

`Frontend`

### Задачи

- Основной CTA после успеха:
  - `Перейти в профиль`
  - или `Открыть покупки`
- Убедиться, что купоны отображаются без лишних ручных действий.
- Проверить, что после успеха профиль делает актуальный запрос, а не остается на старом кэше.

### Acceptance criteria

- после demo-покупки пользователь реально видит результат.

## C3. Уточнить отображение результата покупки

### Тип

`Frontend + Product`

### Задачи

- На success state показать:
  - номер заказа;
  - статус покупки;
  - что купоны доступны в профиле;
  - что уведомление отправлено или будет отправлено.
- Не обещать юридически значимый чек.

### Acceptance criteria

- success-state закрывает пользовательское ожидание “покупка завершена, что дальше?”.

---

## 7. Этап D — Notifications и content alignment

## D1. Проверить email flow

### Тип

`Backend + QA`

### Задачи

- Убедиться, что после `CouponPurchasedEvent` email сервис отрабатывает.
- Если email disabled:
  - работает stub mode;
  - UI flow не ломается.

### Acceptance criteria

- email часть цепочки предсказуема.

## D2. Проверить SMS flow

### Тип

`Backend + QA`

### Задачи

- Убедиться, что SMS сервис отрабатывает.
- Если SMS disabled:
  - работает stub mode;
  - успешная покупка не откатывается.

### Acceptance criteria

- SMS часть цепочки предсказуема.

## D3. Согласовать формулировки

### Тип

`Product + Content`

### Задачи

- Убрать ambiguous wording:
  - `чек`, если он не является реальным фискальным документом;
- Зафиксировать:
  - success text;
  - email subject/body;
  - SMS text.
- Зафиксировать единый термин для временного режима:
  - `Демо-оплата` на экране оплаты;
  - `Подтверждение покупки` в success и уведомлениях.

### Acceptance criteria

- коммуникация не вводит пользователя в заблуждение.

---

## 8. Этап E — QA hardening

## E1. Happy-path сценарий

### Тип

`QA`

### Задачи

- auth user -> checkout -> payment page -> demo confirm -> profile -> notifications

### Acceptance criteria

- happy path проходит без ручных технических обходов.

## E2. Error-path сценарии

### Тип

`QA`

### Задачи

- повторное нажатие `Подтвердить покупку`
- payment already completed
- invalid orderId
- demo endpoint disabled
- notification failure
- profile opened before fresh data loaded
- timeout между созданием order и появлением payment

### Acceptance criteria

- error handling предсказуемый и не ломает order/payment data.

## E3. Switch-off сценарий

### Тип

`QA + Backend`

### Задачи

- проверить, что в `provider` mode demo endpoint выключен;
- проверить, что frontend может быть переключен на provider flow без ломания базовой структуры.

### Acceptance criteria

- demo mode отключается без разрушения текущей архитектуры.

---

## 9. Параллелизация работ

### Можно параллельно

- Backend `A1/A2` и Content draft для success texts
- Frontend `B1/B2` и QA draft checklist
- Notifications verification `D1/D2` параллельно `C1/C2`

### Нельзя эффективно параллельно

- Success/profile UX без готового backend demo completion endpoint
- Notifications acceptance без working `CouponPurchasedEvent` chain
- Финальный показ demo flow без согласованного copy по “чеку” / подтверждению

---

## 10. Рекомендуемый порядок PR / merge

### PR-1

- backend demo/provider mode
- demo completion endpoint
- idempotency

### PR-2

- payment page demo CTA
- demo loading/error/success states

### PR-3

- profile / post-purchase success flow
- copy alignment

### PR-4

- notifications verification
- QA fixes

---

## 11. Definition of Done для demo-mode инициативы

### Demo-mode инициатива считается завершенной, если:

- пользователь может завершить покупку без реальной ПС;
- payment completion идет через существующую payment domain chain;
- purchased coupons создаются как в основном flow;
- купоны видны пользователю в профиле;
- email/SMS отрабатывают или безопасно деградируют;
- duplicate completion не создает дубликатов купонов;
- demo path можно отключить при переходе к реальной ПС.

---

## 12. Ближайший следующий шаг

### Рекомендация

Backlog можно переводить в engineering tickets по группам:

- backend;
- frontend;
- notifications/content;
- QA.

### Рекомендация

Первым PR должен быть именно backend demo completion foundation:

- `payment.mode`
- `demo-complete endpoint`
- идемпотентность
- переиспользование существующей completion chain
