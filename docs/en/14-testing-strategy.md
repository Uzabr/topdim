# 14. Testing strategy

| Level | Existing implementation |
|---|---|
| Unit | JUnit 5/Mockito across identity, coupon, order, payment, bazaar, gateway |
| MVC/security | `@WebMvcTest` for authentication, validation, moderator security |
| Repository/integration | coupon `@DataJpaTest` with Testcontainers PostgreSQL |
| Migration | order PostgreSQL/SQL regression tests |
| Frontend | buyer Vitest/Testing Library for auth/session/profile/payment UI |
| Manual/E2E | `docs/qa` checklists; no automated browser runner |

No tests were found for notification/media/bot. Admin and partner have no test script.

```bash
./gradlew test
./gradlew :services:order-service:test
./gradlew :services:coupon-service:test --tests '*CouponOfferServiceTest'
cd frontend/web-app && npm run test
```

Critical coverage should include duplicate/deleted/blocked identity and token cases; every coupon transition and concurrent stock; stale/empty cart and duplicate payment events; redemption ownership/expiry/refund state; callback signature/amount/idempotency; duplicate support requests; Feign/Rabbit/Redis/MinIO/SMTP failures; and frontend session races, route guards, error states, locale parity, and recovery.

Use unit tests for rules, PostgreSQL integration tests for constraints/locking/migrations, contract tests for Feign/events, and a small Playwright flow covering login → checkout → demo payment → issued coupon → redemption/refund. Performance and security suites are currently absent.

The 10% JaCoCo gate is a floor, not a quality target.
