# MVP Beta Readiness Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the coupon MVP for first real-user beta testing by tightening business rules, support visibility, QA documentation, and regression coverage around purchase, redemption, partner access, and admin support.

**Architecture:** Keep scope focused on the coupon/merchant MVP. Do not add bazaar features or visual redesign work. Use existing services and apps: `coupon-service`, `order-service`, `identity-service`, `frontend/web-app`, `frontend/partner`, `frontend/admin-app`, and `docs/qa`.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, MockMvc, Flyway, React, TypeScript, Vite, Ant Design in admin/partner apps, TanStack Query, Zustand.

---

## Required Skills And Context

- Read root `AGENTS.md` first. Work as senior QA/Software Engineer focused on business logic.
- Use `superpowers:using-superpowers` if available.
- Use `superpowers:systematic-debugging` for any failing or unclear scenario.
- Use `superpowers:test-driven-development` for business rule changes.
- Use `superpowers:verification-before-completion` before reporting done.
- Use folder skills when touching related areas:
  - `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
  - `services/.agent/skills/api-design-principles/SKILL.md`
  - `frontend/.agent/skills/react/SKILL.md`
  - `frontend/.agent/skills/react-hook-form-zod/SKILL.md` if forms are changed.

## Scope

### In Scope

- Buyer purchase and profile coupon regression.
- Partner owner/cashier redemption regression.
- Staff creation rules for cashier beta usage.
- Admin/support lookup for orders and purchased coupons.
- QA docs and smoke checklist for beta testers.
- Security consistency around partner-only redemption.

### Out Of Scope

- Bazaar/directory work.
- Homepage/partner landing redesign.
- Real payment provider integration beyond existing demo/payment callbacks.
- Bonus program.
- Full manager dashboard. Manager role can remain internal/future unless already safely supported.

## Current State Summary

Manual E2E was passed:

- Buyer can buy coupon.
- Buyer sees PIN and real QR in profile.
- Partner app can redeem by PIN and QR scan.
- Backend blocks wrong merchant, used coupon, and expired coupon.
- Public coupon catalog hides `ACTIVE` coupons after `buyUntil`.

Important code points:

- Buyer QR UI: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- Partner redemption UI: `frontend/partner/src/pages/RedeemPage.tsx`
- Redemption business logic: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Partner access gate: `services/order-service/src/main/java/uz/topdim/order/service/PartnerAccessResolver.java`
- Public coupon visibility: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Staff context: `services/identity-service/src/main/java/uz/topdim/identity/service/PartnerStaffService.java`
- QA docs: `docs/qa/manual-test-plan.md`, `docs/qa/purchase-flow-checklist.md`

---

## Phase 1: QA Source Of Truth

### Task 1: Update Manual QA Plan For Actual Partner App Flow

**Files:**
- Modify: `docs/qa/manual-test-plan.md`
- Modify: `docs/qa/purchase-flow-checklist.md`
- Create: `docs/qa/beta-smoke-checklist.md`

- [ ] Replace outdated redemption instructions that mention admin-app/manual API as the main flow.
- [ ] Document the actual MVP redemption flow:
  - Buyer opens `/profile`.
  - Buyer shows PIN or QR.
  - Cashier/partner opens `frontend/partner`.
  - Cashier scans QR or enters PIN at `/redeem`.
  - Buyer profile moves coupon from `ACTIVE` to `USED`.
- [ ] Create `docs/qa/beta-smoke-checklist.md` with these sections:
  - Test accounts and roles.
  - Buyer happy path.
  - Partner owner happy path.
  - Cashier happy path.
  - Admin/moderator coupon approval path.
  - Negative tests.
  - Release blockers.

Required checklist content:

```md
# Beta Smoke Checklist

## Roles
- USER: buyer account with email and phone.
- PARTNER OWNER: merchant owner with access to partner dashboard.
- PARTNER CASHIER: staff login bound to a merchant location.
- MODERATOR/ADMIN: TopDim staff for coupon moderation.

## Buyer Smoke
- [ ] Login works.
- [ ] Catalog shows only buyable active coupons.
- [ ] Coupon detail opens.
- [ ] Coupon option can be added to cart.
- [ ] Checkout creates order.
- [ ] Demo payment completes.
- [ ] Purchased coupon appears in profile.
- [ ] PIN is visible.
- [ ] QR is rendered as QR image, not raw token.

## Partner Redemption Smoke
- [ ] Owner can open dashboard.
- [ ] Cashier opens directly to redemption page.
- [ ] PIN redemption succeeds for own merchant coupon.
- [ ] QR redemption succeeds for own merchant coupon.
- [ ] Reusing same coupon returns business error.
- [ ] Wrong merchant coupon returns business error.
- [ ] Expired coupon returns business error and moves to expired.

## Admin/Moderator Smoke
- [ ] Partner application can be approved.
- [ ] Partner coupon request can be taken to work.
- [ ] Coupon can be sent to merchant approval.
- [ ] Merchant can approve coupon.
- [ ] Active coupon appears publicly before buyUntil.
- [ ] Active coupon disappears publicly after buyUntil.
```

Verification:

- [ ] `rg -n "Админку|orders/redeem|/api/v1/orders/redeem" docs/qa`
- [ ] Any remaining mention of legacy redemption must clearly say “legacy/support only, not main MVP flow”.

---

## Phase 2: Partner Staff Beta Rules

### Task 2: Make Cashier Creation Beta-Safe

**Business Rule:**

For beta, a cashier must be able to log in and must be bound to exactly one branch/location. A cashier without login credentials cannot use the partner app; a cashier without location creates wrong-branch redemption risk.

**Files:**
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/service/PartnerStaffService.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/dto/CreateStaffRequest.java`
- Modify: `services/identity-service/src/test/java/uz/topdim/identity/service/PartnerStaffServiceTest.java`
- Modify: `frontend/partner/src/pages/StaffPage.tsx`

- [ ] Add backend validation:
  - `role=CASHIER` requires `merchantLocationId`.
  - `role=CASHIER` requires `loginEmail`.
  - `role=CASHIER` requires `temporaryPassword`.
  - `temporaryPassword` must be at least 6 characters if provided.
- [ ] Keep `MANAGER` out of beta UI unless fully supported. Recommended: hide `MANAGER` from `StaffPage` role select for now.
- [ ] Update `StaffPage` labels:
  - Email for login: required.
  - Temporary password: required.
  - Branch: required.
- [ ] Add tests:
  - `addStaff_cashierWithoutLocation_throws`
  - `addStaff_cashierWithoutLoginEmail_throws`
  - `addStaff_cashierWithoutTemporaryPassword_throws`
  - `addStaff_cashierWithShortPassword_throws`
  - existing happy path still passes.

Expected backend validation style:

```java
private void validateStaffRequest(CreateStaffRequest request) {
    String role = request.getRole() != null ? request.getRole().trim().toUpperCase() : "CASHIER";
    if ("CASHIER".equals(role)) {
        if (request.getMerchantLocationId() == null) {
            throw new IllegalArgumentException("Кассир должен быть привязан к филиалу");
        }
        if (request.getLoginEmail() == null || request.getLoginEmail().isBlank()) {
            throw new IllegalArgumentException("Для кассира обязателен email для входа");
        }
        if (request.getTemporaryPassword() == null || request.getTemporaryPassword().length() < 6) {
            throw new IllegalArgumentException("Временный пароль кассира должен быть не короче 6 символов");
        }
    }
}
```

Verification:

- [ ] `./gradlew :services:identity-service:test --tests "uz.topdim.identity.service.PartnerStaffServiceTest"`
- [ ] `cd frontend/partner && npm run build`

---

## Phase 3: Redemption Security And Error Hardening

### Task 3: Align Legacy Redemption Endpoint With Partner-Only Policy

**Business Rule:**

Admins can remove coupons from sale and support users, but they should not redeem coupons for merchants in normal MVP flow. Redemption belongs to partner owner/cashier through trusted partner context.

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/controller/RedeemCouponControllerTest.java`
- Modify: `docs/backend/api-contract.md`
- Modify: `docs/qa/manual-test-plan.md`

- [ ] In comments and annotations, make legacy `POST /api/v1/orders/redeem` partner-only.
- [ ] Keep preferred endpoint documented as `POST /api/v1/partner/redemptions`.
- [ ] Do not add admin redemption UI.
- [ ] If admin support needs redemption in future, require a separate audited support-mode plan, not this endpoint.
- [ ] Add/adjust tests to verify legacy endpoint still uses `X-Merchant-Id` for partner-only compatibility.
- [ ] Add documentation note:

```md
`POST /api/v1/orders/redeem` is legacy partner-only compatibility.
Main MVP flow uses `POST /api/v1/partner/redemptions` and `POST /api/v1/partner/redemptions/qr`.
Admins must not redeem customer coupons as merchants in MVP.
```

Verification:

- [ ] `./gradlew :services:order-service:test --tests "uz.topdim.order.controller.RedeemCouponControllerTest"`
- [ ] `./gradlew :services:order-service:test --tests "uz.topdim.order.controller.PartnerControllerTest"`

### Task 4: Improve Partner Redemption UX Errors

**Files:**
- Modify: `frontend/partner/src/pages/RedeemPage.tsx`

- [ ] Normalize common backend messages into cashier-friendly text:
  - “Купон не найден” → “Код не найден. Проверьте PIN или попросите клиента показать QR.”
  - “Купон принадлежит другому мерчанту” → “Этот купон относится к другому партнёру.”
  - “Статус: USED” → “Этот купон уже был использован.”
  - “Срок действия купона истёк” → “Срок действия купона истёк.”
- [ ] Keep raw backend message in console only if needed; do not expose technical traces.
- [ ] After successful redemption, show method-specific success:
  - PIN: “Купон погашен по PIN.”
  - QR: “Купон погашен по QR.”
- [ ] Keep scanner fallback to PIN.

Verification:

- [ ] `cd frontend/partner && npm run build`
- [ ] Manual: enter wrong PIN, used PIN, wrong merchant PIN.

---

## Phase 4: Admin Support Visibility For Beta

### Task 5: Add Minimal Admin Order And Purchased Coupon Lookup

**Business Need:**

During beta, support must quickly answer: who bought this coupon, what order it belongs to, what status it has, when it expires, whether it was redeemed, and by which merchant/staff.

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/PurchasedCouponRepository.java`
- Create: `services/order-service/src/main/java/uz/topdim/order/dto/AdminPurchasedCouponLookupResponse.java`
- Create/modify tests in `services/order-service/src/test/java/uz/topdim/order/controller/OrderControllerBusinessErrorTest.java` or a new `AdminPurchasedCouponControllerTest.java`
- Create: `frontend/admin-app/src/features/orders/OrdersPage.tsx`
- Create: `frontend/admin-app/src/features/orders/OrderDetailPage.tsx`
- Create: `frontend/admin-app/src/features/orders/PurchasedCouponLookupPage.tsx`
- Create: `frontend/admin-app/src/features/orders/api.ts`
- Modify: `frontend/admin-app/src/App.tsx`
- Modify: `frontend/admin-app/src/components/layout/AdminLayout.tsx`

- [ ] Backend endpoint:

```http
GET /api/v1/admin/purchased-coupons/lookup?couponCode=CP-XXXX
```

- [ ] Response must include:
  - purchasedCouponId
  - orderId
  - userId
  - couponOfferId
  - couponOptionId
  - couponTitle
  - optionTitle
  - couponCode
  - status
  - merchantId
  - merchantName
  - merchantAddress
  - purchasedAt
  - expiresAt
  - usedAt
- [ ] Do not expose `qrToken` in admin lookup response.
- [ ] If coupon not found, return 404 with clear message.
- [ ] Admin UI:
  - Add `/orders/list`.
  - Add `/orders/coupon-lookup`.
  - Search by coupon code.
  - Display status, buyer userId, orderId, merchant, expiry, usedAt.
  - Provide links to order detail if available.
- [ ] Keep admin lookup read-only. No redemption button.

Verification:

- [ ] `./gradlew :services:order-service:test`
- [ ] `cd frontend/admin-app && npm run build`
- [ ] Manual: search a real purchased coupon code.

---

## Phase 5: Regression Test Gaps

### Task 6: Add Business Regression Tests For Critical MVP Risks

**Files:**
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponSaleRegistrationTest.java`
- Modify: `services/identity-service/src/test/java/uz/topdim/identity/service/PartnerStaffServiceTest.java`

Add or confirm tests for:

- [ ] Checkout rejects expired `buyUntil`.
- [ ] Checkout rejects inactive coupon option.
- [ ] Checkout rejects quantity over remaining limit.
- [ ] Payment coupon generation is idempotent.
- [ ] Duplicate `registerSale` does not increment sold count twice.
- [ ] Redeem used coupon fails and does not publish `CouponRedeemedEvent`.
- [ ] Redeem wrong merchant fails and does not publish event.
- [ ] Redeem expired coupon marks `EXPIRED` and does not publish event.
- [ ] QR redemption wrong merchant fails.
- [ ] Cashier without location is rejected.
- [ ] Inactive cashier is rejected.

Important: Some of these already exist. Do not duplicate tests blindly. First inspect existing test classes, then add only missing coverage or strengthen assertions.

Verification:

- [ ] `./gradlew :services:order-service:test`
- [ ] `./gradlew :services:coupon-service:test`
- [ ] `./gradlew :services:identity-service:test`

---

## Phase 6: Beta Release Checklist

### Task 7: Create Release Readiness Report

**Files:**
- Create: `docs/qa/beta-readiness-report.md`

- [ ] Document current beta status:
  - What is ready.
  - What is intentionally out of scope.
  - Known risks.
  - Test accounts needed.
  - Smoke test result table.
  - Commands run.
- [ ] Include exact verification commands:

```bash
./gradlew :services:order-service:test
./gradlew :services:coupon-service:test
./gradlew :services:identity-service:test
cd frontend/web-app && npm run build
cd frontend/partner && npm run build
cd frontend/admin-app && npm run build
```

- [ ] Include manual sign-off checklist:
  - Buyer purchase passed.
  - Buyer QR/PIN passed.
  - Partner PIN redemption passed.
  - Partner QR redemption passed.
  - Used coupon regression passed.
  - Wrong merchant regression passed.
  - Admin lookup passed.

---

## Priority Order

Implement in this order:

1. Phase 1: QA source of truth.
2. Phase 2: cashier staff rules.
3. Phase 3: redemption security and UX errors.
4. Phase 5: regression test gaps.
5. Phase 4: admin support lookup.
6. Phase 6: beta readiness report.

Reason: docs and cashier rules unblock clean beta setup; redemption hardening protects money/business logic; tests lock behavior; support lookup helps once real people start testing.

## Acceptance Criteria

- QA docs match the actual product flow.
- Cashier cannot be created in a way that prevents login or branch-safe redemption.
- Admins do not have a normal redemption flow.
- Partner redemption errors are understandable to a cashier.
- Admin support can look up a purchased coupon by code without seeing QR token.
- Critical backend regression tests pass.
- Web, partner, and admin apps build.
- A beta readiness report exists and can be used before inviting first testers.

