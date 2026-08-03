# 14. Стратегия тестирования

## Существующее покрытие

| Уровень | Реализация |
|---|---|
| Unit | JUnit 5/Mockito для identity, coupon, order, payment, bazaar и gateway |
| MVC/security | `@WebMvcTest` для auth, validation, moderator security |
| Repository/integration | coupon `@DataJpaTest` + Testcontainers PostgreSQL |
| Migration | order PostgreSQL/SQL regression tests |
| Frontend | web-app Vitest + Testing Library для auth/session/profile/payment UI |
| Manual/E2E | чеклисты в `docs/qa`; автоматического browser E2E runner нет |

Notification/media не имеют обнаруженных tests; admin/partner package не имеют `test` script; Telegram bot tests не обнаружены.

## Команды

```bash
./gradlew test
./gradlew :services:order-service:test
./gradlew :services:coupon-service:test --tests '*CouponOfferServiceTest'
cd frontend/web-app && npm run test
```

CI также запускает `npm run lint` и `npm run build` для всех SPA.

## Обязательная матрица бизнес-сценариев

- Auth: duplicate email/phone, deleted/blocked user, brute force, revoked/expired/rotated refresh, Telegram replay.
- Coupon: каждый допустимый/запрещённый transition, merchant readiness, concurrent stock, duplicate sale/redemption.
- Order: empty cart, stale price/stock, duplicate payment event, ownership, expired/used/refund-pending redemption.
- Payment: duplicate order/event/callback, wrong amount/provider signature, demo/provider isolation.
- Support: duplicate refund/complaint/review, ownership, forbidden role, terminal transitions.
- Integrations: Feign timeout/5xx, Rabbit redelivery, Redis down, MinIO down/large/malicious upload, SMTP failure.
- Frontend: session race/account isolation, guarded routes, API error states, locale parity, checkout recovery.

## Рекомендуемая пирамида

Сохранять unit tests для domain rules; добавлять PostgreSQL integration tests для constraints/locking/migrations; contract tests для Feign/events; небольшой Playwright suite `login → cart → checkout → demo payment → coupon → redemption/refund`. Performance/security tests отсутствуют и должны быть отдельными pipeline jobs.

Coverage 10% — только минимальный gate, не цель качества. Для критичных services нужен risk-based threshold и branch coverage.
