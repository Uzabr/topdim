# Purchase Lifecycle Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the real coupon purchase lifecycle so `cart -> order -> payment -> purchased coupons -> coupon sales stats -> partner redemption -> redeemed stats` is consistent, idempotent, and covered by business tests.

**Architecture:** Keep `order-service` as the owner of orders, purchased coupons, and redemption records. Keep `coupon-service` as the owner of coupon offer counters, limits, and merchant/catalog stats. Synchronize cross-service business facts through narrow internal APIs or events with idempotency ledgers, not by trusting frontend state.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Flyway, RabbitMQ, OpenFeign, JUnit 5, Mockito, MockMvc, React 19, TypeScript, Zustand, Axios.

---

## Required Skills

- [ ] Read and follow `superpowers:using-superpowers` before starting.
- [ ] Use `superpowers:test-driven-development` for every backend behavior change.
- [ ] Use `superpowers:systematic-debugging` if any test, build, or runtime behavior is unexpected.
- [ ] Use `superpowers:verification-before-completion` before reporting the task as complete.
- [ ] Use `services/.agent/skills/api-design-principles/SKILL.md` for all endpoint/DTO changes.
- [ ] Use `services/.agent/skills/spring-boot-test-patterns/SKILL.md` for service/controller/listener tests.
- [ ] Use `services/.agent/skills/database-schema-designer/SKILL.md` for Flyway migrations and uniqueness constraints.
- [ ] Use `services/.agent/skills/sql-optimization-patterns/SKILL.md` if adding indexes or idempotency ledger queries.
- [ ] Use `frontend/.agent/skills/react/SKILL.md` if frontend cart behavior is changed.
- [ ] Use `frontend/.agent/skills/frontend-design/SKILL.md` only for visible UI changes; do not redesign unrelated screens.

## Current State From Code

- [ ] `order-service` already revalidates cart items through `coupon-service` purchase snapshot during add-to-cart and checkout.
- [ ] `order-service` already generates `PurchasedCoupon` after `PaymentCompletedEvent` and has an idempotency guard by `findByOrderId`.
- [ ] `order-service` already stores `merchantId`, merchant display fields, `expiresAt`, `couponCode`, and `qrToken` on purchased coupons.
- [ ] `partner` redeem flow currently accepts only `couponCode`; `findByQrToken` exists but is not used.
- [ ] `coupon-service` has `registerSale(...)`, `totalSold`, `quantitySold`, `totalTurnover`, and `redeemedCount`, but purchase/redemption lifecycle does not call them.
- [ ] `payment-service` creates payments from `order.created`, but `payments.order_id` is not unique and `createPayment(...)` itself is not idempotent.
- [ ] Auth cart quantity change in `frontend/web-app/src/store/cartStore.ts` is local-only; backend checkout still uses old backend quantity.

## Business Rules

- [ ] One order must have at most one payment row.
- [ ] Retrying payment completion must not create duplicate purchased coupons or duplicate business events.
- [ ] Checkout quantity must match backend cart quantity, not local frontend-only state.
- [ ] Coupon option `quantitySold` and offer `totalSold/totalTurnover` must increase once per paid order item.
- [ ] Replayed sale registration must not double-increment coupon counters.
- [ ] Partner redemption must support PIN code and QR token.
- [ ] A purchased coupon can be redeemed only once, only by its owning merchant, only while `ACTIVE`, and only before `expiresAt`.
- [ ] Coupon `redeemedCount` must increase once per successful purchased-coupon redemption.
- [ ] Replayed redemption events must not double-increment redeemed counters.

## Scope

- [ ] Implement payment idempotency hardening.
- [ ] Implement backend cart item quantity update.
- [ ] Implement idempotent sale registration between order-service and coupon-service.
- [ ] Implement QR-token redemption support.
- [ ] Implement idempotent redemption stats sync into coupon-service.
- [ ] Add focused tests for service, controller, repository/migration behavior where needed.

## Out Of Scope

- [ ] Do not build bazaar/shop service.
- [ ] Do not build full merchant cabinet.
- [ ] Do not add real payment provider integration.
- [ ] Do not redesign partner landing page or unrelated frontend screens.
- [ ] Do not change public contracts unless the task explicitly requires it.

---

### Task 0: Baseline Verification

**Files:** No code changes.

- [ ] Run `git status --short` and note existing user/AI changes; do not revert unrelated files.
- [ ] Run `./gradlew :services:order-service:test`.
- [ ] Run `./gradlew :services:payment-service:test`.
- [ ] Run `./gradlew :services:coupon-service:test`.
- [ ] If tests fail before changes, use `superpowers:systematic-debugging` and record whether the failure is pre-existing.

---

### Task 1: Make Payment Creation Idempotent

**Files:**
- Modify `services/payment-service/src/main/java/uz/topdim/payment/service/PaymentService.java`.
- Modify `services/payment-service/src/main/java/uz/topdim/payment/listener/OrderCreatedListener.java` only if listener behavior needs explicit tests.
- Modify `services/payment-service/src/main/java/uz/topdim/payment/repository/PaymentRepository.java` only if additional query methods are needed.
- Create `services/payment-service/src/main/resources/db/migration/V4__unique_payment_order_id.sql`.
- Modify `services/payment-service/src/test/java/uz/topdim/payment/service/PaymentServiceTest.java`.
- Create or modify `services/payment-service/src/test/java/uz/topdim/payment/listener/OrderCreatedListenerTest.java`.

**Implementation Steps:**
- [ ] Write a failing test: `createPayment_existingOrder_returnsExistingPaymentAndDoesNotSaveNewOne`.
- [ ] Write a failing test: `handleCallback_completedPayment_doesNotRepublishPaymentCompletedEvent`.
- [ ] Write a failing listener test: duplicate `OrderCreatedEvent` does not create a second payment.
- [ ] Add migration with `CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_order_id ON payments(order_id);`.
- [ ] Change `PaymentService.createPayment(...)` to return existing payment from `findByOrderId(orderId)` before creating a new row.
- [ ] Handle DB race defensively: if save hits a unique/order-id conflict, refetch by `orderId` and return existing payment.
- [ ] Change `PaymentService.handleCallback(...)` so already `COMPLETED` payment returns without publishing another `PaymentCompletedEvent`.
- [ ] Run `./gradlew :services:payment-service:test`.

**Done Criteria:**
- [ ] Repeated `createPayment(orderId, ...)` returns the same logical payment.
- [ ] Repeated provider callback for already completed payment does not publish duplicate completion event.
- [ ] Payment table protects `order_id` at DB level.

---

### Task 2: Add Backend Cart Quantity Update

**Files:**
- Modify `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`.
- Create `services/order-service/src/main/java/uz/topdim/order/dto/UpdateCartItemQuantityRequest.java`.
- Modify `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`.
- Create or modify `services/order-service/src/test/java/uz/topdim/order/controller/OrderControllerBusinessErrorTest.java`.
- Modify `frontend/web-app/src/api/orders.ts`.
- Modify `frontend/web-app/src/store/cartStore.ts`.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/api-design-principles/SKILL.md` before adding the endpoint.
- [ ] Create DTO `UpdateCartItemQuantityRequest` with `@Min(value = 1, message = "Количество должно быть больше 0") private int quantity;`.
- [ ] Add endpoint `PATCH /api/v1/cart/items/{itemId}` that receives trusted `X-User-Id` and the DTO.
- [ ] Add `OrderService.updateCartItemQuantity(Long userId, Long itemId, int quantity)`.
- [ ] Service must find the current user's cart, find the item inside that cart, reload purchase snapshot, validate `ACTIVE`, `buyUntil`, option status, and available quantity.
- [ ] If item does not belong to user's cart, return business not-found/conflict using the existing exception style.
- [ ] Update frontend `ordersApi.updateCartItemQuantity(itemId, quantity)`.
- [ ] Change auth-mode `cartStore.updateQuantity` to call backend API, then refresh backend cart state.
- [ ] Keep guest-mode localStorage behavior unchanged.
- [ ] Run `./gradlew :services:order-service:test`.
- [ ] If frontend changed, run the existing frontend verification command used by the project, normally `npm run build` inside `frontend/web-app`.

**Done Criteria:**
- [ ] Auth user quantity shown in UI equals backend checkout quantity.
- [ ] Checkout cannot silently use stale backend quantity after the user clicks plus/minus.
- [ ] Quantity above coupon availability fails before order creation.

---

### Task 3: Register Paid Coupon Sales In Coupon Service Idempotently

**Files:**
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponSale.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponSaleRepository.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/dto/RegisterCouponSaleRequest.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/controller/InternalCouponController.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`.
- Create `services/coupon-service/src/main/resources/db/migration/V13__coupon_sales_ledger.sql`.
- Modify or create coupon-service tests for sale registration.
- Modify `services/order-service/src/main/java/uz/topdim/order/client/CouponClient.java`.
- Create `services/order-service/src/main/java/uz/topdim/order/client/RegisterCouponSaleRequest.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`.
- Modify `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/database-schema-designer/SKILL.md` before creating the ledger migration.
- [ ] Create table `coupon_sales` with columns `id`, `order_id`, `coupon_offer_id`, `coupon_option_id`, `quantity`, `amount`, `created_at`.
- [ ] Add unique constraint/index on `(order_id, coupon_offer_id, coupon_option_id)`.
- [ ] Add internal endpoint `POST /api/v1/internal/coupons/{couponId}/options/{optionId}/sales`.
- [ ] Request body must contain `orderId`, `quantity`, and `amount`.
- [ ] Implement `CouponOfferService.registerSaleOnce(orderId, couponId, optionId, quantity, amount)`.
- [ ] If sale ledger already exists for the same `(orderId, couponId, optionId)`, return without changing counters.
- [ ] On first registration, create ledger row and increment option `quantitySold`, offer `totalSold`, offer `totalTurnover`.
- [ ] If option reaches `quantityLimit`, set option status to `SOLD_OUT` and update offer status only if existing business rules require it.
- [ ] In `OrderService.generatePurchasedCoupons(orderId)`, call coupon-service sale registration once per `OrderItem`, not once per generated purchased coupon.
- [ ] Ensure retrying `generatePurchasedCoupons(orderId)` with existing purchased coupons does not call sale registration again.
- [ ] Add tests for first sale, duplicate sale, sold-out boundary, and order-service sale call count.
- [ ] Run `./gradlew :services:coupon-service:test`.
- [ ] Run `./gradlew :services:order-service:test`.

**Done Criteria:**
- [ ] Paid orders update coupon sales counters exactly once.
- [ ] Duplicate payment/completion retry does not double-increment coupon stats.
- [ ] Coupon option limits are enforced after real purchases, not only at checkout time.

---

### Task 4: Support QR Token Redemption

**Files:**
- Modify `services/order-service/src/main/java/uz/topdim/order/dto/CreateRedemptionRequest.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/dto/RedeemCouponRequest.java` if legacy endpoint should support QR too.
- Modify `services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java` only if legacy endpoint is updated.
- Modify `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`.
- Modify `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`.
- Modify `services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java`.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/api-design-principles/SKILL.md` before changing request DTOs.
- [ ] Allow partner request to send either `couponCode` or `qrToken`.
- [ ] Add validation: exactly one or at least one identifier must be present; choose "at least one" unless product explicitly wants to reject both-present requests.
- [ ] Normalize `couponCode` with `trim().toUpperCase()`.
- [ ] Do not uppercase `qrToken`; treat QR token as opaque.
- [ ] Add `OrderService.redeemCouponByIdentifier(String couponCode, String qrToken, Long merchantId, String staffName)`.
- [ ] If `qrToken` is present, use `PurchasedCouponRepository.findByQrToken(qrToken)`.
- [ ] If `couponCode` is present, use `findByCouponCode(couponCode)`.
- [ ] Keep all existing status, expiry, merchant ownership, and one-time redemption checks.
- [ ] Preserve current PIN redemption behavior.
- [ ] Add tests for redeem by QR success, missing both fields validation, wrong merchant, expired coupon, already used coupon.
- [ ] Run `./gradlew :services:order-service:test`.

**Done Criteria:**
- [ ] Partner can redeem a coupon by manually entered PIN.
- [ ] Partner can redeem the same type of coupon by scanned QR token.
- [ ] QR support does not weaken merchant ownership or already-used protection.

---

### Task 5: Sync Redeemed Count To Coupon Service Idempotently

**Files:**
- Create or modify event in `shared/common-events/src/main/java/uz/topdim/common/events/CouponRedeemedEvent.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/config/RabbitMQConfig.java` if exchange/routing is missing.
- Modify `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponRedemptionLedger.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponRedemptionLedgerRepository.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/listener/CouponRedeemedListener.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/config/RabbitMQConfig.java` if queue binding is missing.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`.
- Create `services/coupon-service/src/main/resources/db/migration/V14__coupon_redemptions_ledger.sql`.
- Add order-service tests for event publishing.
- Add coupon-service listener/service tests for idempotent redeemed count.

**Implementation Steps:**
- [ ] Define `CouponRedeemedEvent` with `purchasedCouponId`, `orderId`, `couponOfferId`, `couponOptionId`, `merchantId`, and `redeemedAt`.
- [ ] Publish `coupon.redeemed` only after successful redemption state change and redemption row creation.
- [ ] Do not publish event for wrong merchant, expired, already used, or not found attempts.
- [ ] Create `coupon_redemption_ledger` with unique `purchased_coupon_id`.
- [ ] Coupon-service listener must ignore duplicate `purchasedCouponId` events without incrementing again.
- [ ] First-time event must increment `CouponOffer.redeemedCount` by 1.
- [ ] Add tests for event published on success, no event on failure, first event increments, duplicate event ignored.
- [ ] Run `./gradlew :services:order-service:test`.
- [ ] Run `./gradlew :services:coupon-service:test`.

**Done Criteria:**
- [ ] Coupon `redeemedCount` reflects successful partner redemptions.
- [ ] Duplicate/replayed RabbitMQ events do not corrupt stats.
- [ ] Failed redemption attempts never update coupon stats.

---

### Task 6: Final End-To-End Verification

**Files:** No new code unless verification exposes a bug.

- [ ] Run `./gradlew :services:payment-service:test`.
- [ ] Run `./gradlew :services:order-service:test`.
- [ ] Run `./gradlew :services:coupon-service:test`.
- [ ] If frontend cart API changed, run `npm run build` from `frontend/web-app`.
- [ ] Manually or with integration tests verify: add cart item, update quantity, create order, payment appears, demo-complete payment, purchased coupons are created, sale counters update, partner redeems by PIN, partner redeems by QR on another coupon, duplicate redeem is rejected, redeemed count increments once.
- [ ] Update API docs only after code is verified, and only for changed endpoints/contracts.

## Final Report Required From AI

- [ ] What changed in business logic.
- [ ] Which files were changed.
- [ ] Which tests were added.
- [ ] Exact verification commands and pass/fail output.
- [ ] Any unresolved risks or decisions that need product confirmation.

