# User Profile Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the user profile business-correct, resilient to API failures, and behaviorally consistent across desktop and mobile.

**Architecture:** Backend services remain the source of truth for authorization, lifecycle transitions, and idempotency; frontend helpers only mirror those rules for presentation. Public APIs return DTOs rather than persistence entities, and every asynchronous profile section distinguishes loading, error, empty, and populated states.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway/PostgreSQL, React 19, TypeScript, TanStack Query 5, Vitest, Testing Library.

## Global Constraints

- Do not perform broad refactors or change unrelated services.
- Do not remove existing tests.
- Add a failing regression test before each production-code fix.
- Preserve existing public contracts except where Task 1 corrects the orders list to the already-declared `Page<OrderResponse>` frontend contract.
- Backend business guards are mandatory even when frontend buttons are hidden.
- Desktop and mobile must expose the same allowed coupon actions.
- Use existing project exception, localization, component, and test patterns.
- Each task must end with its focused tests passing and one self-contained commit.

---

### Task 1: Return order DTOs from the user orders endpoint

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java:101`
- Create: `services/order-service/src/test/java/uz/topdim/order/controller/OrderControllerResponseTest.java`

**Interfaces:**
- Consumes: `OrderService#getUserOrders(Long, int, int)` and `OrderService#mapToOrderResponse(Order)`.
- Produces: `GET /api/v1/orders` as `ApiResponse<Page<OrderResponse>>`.

- [ ] **Step 1: Write the failing controller test**

Create a MockMvc test named `getUserOrders_returnsMappedOrderResponsePage`. Mock `getUserOrders` with one `Order`, mock `mapToOrderResponse` with an `OrderResponse` containing `id`, `orderNumber`, `title`, and `itemCount`, then assert:

```java
mockMvc.perform(get("/api/v1/orders")
        .header("X-User-Id", 7L)
        .param("page", "0")
        .param("size", "20"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.content[0].id").value(11))
    .andExpect(jsonPath("$.data.content[0].title").value("Spa package"))
    .andExpect(jsonPath("$.data.content[0].itemCount").value(2))
    .andExpect(jsonPath("$.data.content[0].items").doesNotExist());
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run:

```bash
./gradlew :services:order-service:test \
  --tests 'uz.topdim.order.controller.OrderControllerResponseTest' \
  -x jacocoTestReport \
  -x jacocoTestCoverageVerification
```

Expected: the test fails because the controller returns the raw `Order` page and does not invoke `mapToOrderResponse`.

- [ ] **Step 3: Map the page to DTOs**

Change the controller signature and body to:

```java
public ResponseEntity<ApiResponse<Page<OrderResponse>>> getUserOrders(
        @RequestHeader("X-User-Id") Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    Page<OrderResponse> response = orderService.getUserOrders(userId, page, size)
            .map(orderService::mapToOrderResponse);
    return ResponseEntity.ok(ApiResponse.success(response));
}
```

- [ ] **Step 4: Run focused and full order tests**

Run the focused command above, then:

```bash
./gradlew :services:order-service:test
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java services/order-service/src/test/java/uz/topdim/order/controller/OrderControllerResponseTest.java
git commit -m "fix: return order DTOs from profile history"
```

### Task 2: Enforce profile field invariants

**Files:**
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/dto/UpdateProfileRequest.java`
- Modify: `services/identity-service/src/main/java/uz/topdim/identity/service/UserService.java`
- Modify: `services/identity-service/src/test/java/uz/topdim/identity/service/UserServiceTest.java`
- Create: `services/identity-service/src/test/java/uz/topdim/identity/controller/UserControllerValidationTest.java`
- Modify: `frontend/web-app/src/components/profile/ProfileSettingsSection.tsx`
- Modify: `frontend/web-app/src/components/profile/ProfileSettingsSection.test.tsx`

**Interfaces:**
- Consumes: nullable fields in `UpdateProfileRequest`; `null` means unchanged.
- Produces: canonical Uzbekistan phone `+998` followed by 9 digits; changed phones are unverified; an empty submitted last name clears the stored last name.

- [ ] **Step 1: Add failing service and controller tests**

Add tests proving:

```text
changed verified phone -> phoneVerified=false
unchanged phone -> verification preserved
alphabetic or short phone -> HTTP 400
lastName="" -> stored lastName=null
lastName=null -> existing last name unchanged
```

- [ ] **Step 2: Run the focused tests and confirm failure**

```bash
./gradlew :services:identity-service:test \
  --tests 'uz.topdim.identity.service.UserServiceTest' \
  --tests 'uz.topdim.identity.controller.UserControllerValidationTest' \
  -x jacocoTestReport \
  -x jacocoTestCoverageVerification
```

- [ ] **Step 3: Implement validation and normalization**

Add to `UpdateProfileRequest.phone`:

```java
@Pattern(regexp = "^\\+998\\d{9}$", message = "Телефон должен быть в формате +998XXXXXXXXX")
```

In `UserService#updateProfile`, compare the canonical new phone with the stored phone using `Objects.equals`; check uniqueness only when changed, save the phone, and set `phoneVerified` to false when changed. When `lastName` is non-null, trim it and store `null` if the trimmed string is empty.

In `ProfileSettingsSection`, send `lastName.trim()` instead of `lastName.trim() || undefined`.
Replace the current length-only phone check with the same `/^\+998\d{9}$/` rule before submitting, and show the existing inline validation error area when it fails.

- [ ] **Step 4: Run backend and frontend tests**

```bash
./gradlew :services:identity-service:test
cd frontend/web-app && npm run test -- ProfileSettingsSection.test.tsx
```

- [ ] **Step 5: Commit**

```bash
git add services/identity-service frontend/web-app/src/components/profile/ProfileSettingsSection.tsx frontend/web-app/src/components/profile/ProfileSettingsSection.test.tsx
git commit -m "fix: enforce profile contact invariants"
```

### Task 3: Prevent duplicate pending complaints

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/ComplaintRepository.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/ComplaintService.java`
- Create: `services/order-service/src/main/resources/db/migration/V13__prevent_duplicate_pending_complaints.sql`
- Create: `services/order-service/src/test/java/uz/topdim/order/service/ComplaintServiceTest.java`

**Interfaces:**
- Consumes: per-coupon complaint creation and `ComplaintStatus.PENDING`.
- Produces: at most one pending complaint per purchased coupon.

- [ ] **Step 1: Write failing service tests**

Cover first complaint success, duplicate pending rejection, resolved complaint allowing a new complaint, foreign coupon rejection, missing coupon, and notification emitted only after successful persistence.

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew :services:order-service:test \
  --tests 'uz.topdim.order.service.ComplaintServiceTest' \
  -x jacocoTestReport \
  -x jacocoTestCoverageVerification
```

- [ ] **Step 3: Implement application and database guards**

Add:

```java
boolean existsByPurchasedCouponIdAndStatus(Long purchasedCouponId, ComplaintStatus status);
```

Reject an existing pending complaint with:

```java
throw new IllegalStateException("По этому купону уже есть открытое обращение");
```

Use `saveAndFlush` before publishing the notification. Add:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_complaints_pending_coupon
    ON complaints (purchased_coupon_id)
    WHERE purchased_coupon_id IS NOT NULL
      AND status = 'PENDING';
```

- [ ] **Step 4: Run focused and full order tests**

```bash
./gradlew :services:order-service:test \
  --tests 'uz.topdim.order.service.ComplaintServiceTest' \
  -x jacocoTestReport \
  -x jacocoTestCoverageVerification
./gradlew :services:order-service:test
```

- [ ] **Step 5: Commit**

```bash
git add services/order-service
git commit -m "fix: prevent duplicate pending complaints"
```

### Task 4: Centralize coupon action policy

**Files:**
- Create: `frontend/web-app/src/components/profile/couponActions.ts`
- Create: `frontend/web-app/src/components/profile/couponActions.test.ts`
- Modify: `frontend/web-app/src/components/profile/CouponTicket.tsx`
- Modify: `frontend/web-app/src/components/profile/ActiveCouponCard.tsx`
- Modify: `frontend/web-app/src/pages/ProfileMobile.tsx`
- Create: `frontend/web-app/src/components/profile/CouponTicket.test.tsx`
- Create: `frontend/web-app/src/components/profile/ActiveCouponCard.test.tsx`

**Interfaces:**
- Produces: `getCouponActions(status, hasOpenComplaint, hasReview): { canRefund; canComplain; canReview }`.
- Rules: refund only for `ACTIVE`; complaint when there is no pending complaint; review only for `USED` when no review exists. Backend services remain authoritative for each operation.

- [ ] **Step 1: Write failing policy and component tests**

Cover `ACTIVE`, `REFUND_PENDING`, `USED`, `CANCELLED`, pending complaint, and existing review. `CANCELLED` must not allow refund or review, but complaint availability still depends on whether a pending complaint exists. Assert that mobile and desktop expose the same refund and complaint controls.

- [ ] **Step 2: Run and confirm failure**

```bash
cd frontend/web-app && npm run test -- couponActions.test.ts CouponTicket.test.tsx ActiveCouponCard.test.tsx
```

- [ ] **Step 3: Implement and consume the shared policy**

Implement a pure typed helper. Replace inline status checks in both coupon components and the featured/secondary mobile coupon controls. Disabled actions must be absent; a pending complaint indicator remains visible.

- [ ] **Step 4: Run frontend tests**

```bash
cd frontend/web-app && npm run test
```

- [ ] **Step 5: Commit**

```bash
git add frontend/web-app/src/components/profile frontend/web-app/src/pages/ProfileMobile.tsx
git commit -m "fix: align coupon actions across profile layouts"
```

### Task 5: Make order history actionable and asynchronous states truthful

**Files:**
- Modify: `frontend/web-app/src/pages/ProfileDesktop.tsx`
- Modify: `frontend/web-app/src/pages/ProfileMobile.tsx`
- Modify: `frontend/web-app/src/pages/ProfilePage.css`
- Modify: `frontend/web-app/src/pages/ProfileMobile.css`
- Modify: `frontend/web-app/src/locales/ru.json`
- Create: `frontend/web-app/src/pages/ProfileDesktop.test.tsx`
- Create: `frontend/web-app/src/pages/ProfileMobile.test.tsx`

**Interfaces:**
- Consumes: existing localized route helper `lp` and route `/payment/:orderId`.
- Produces: distinct loading/error/empty/populated states; `PENDING` orders link to localized payment recovery; paged order history with 20 records per page and a load-more action.

- [ ] **Step 1: Add failing desktop and mobile tests**

Assert loading never renders empty copy, HTTP failure renders retry, empty success renders empty copy, `PENDING` renders `profile.orders.continuePayment`, and clicking it navigates to localized `/payment/{id}`. Assert load-more requests page 1 after an initial page 0 response where `last=false`.

- [ ] **Step 2: Run and confirm failure**

```bash
cd frontend/web-app && npm run test -- ProfileDesktop.test.tsx ProfileMobile.test.tsx
```

- [ ] **Step 3: Implement states, recovery, and pagination**

Use 20-item pages. Render retry controls wired to `refetch`. Render payment recovery only for `PENDING`; other rows remain readable purchase records. Add translation keys for loading error, retry, continue payment, and load more.

- [ ] **Step 4: Run frontend tests**

```bash
cd frontend/web-app && npm run test
```

- [ ] **Step 5: Commit**

```bash
git add frontend/web-app/src/pages frontend/web-app/src/locales/ru.json
git commit -m "feat: make profile order history recoverable"
```

### Task 6: Make password logout and notifications reliable

**Files:**
- Modify: `frontend/web-app/src/components/profile/ProfileSettingsSection.tsx`
- Modify: `frontend/web-app/src/components/profile/ProfileSettingsSection.test.tsx`
- Modify: `frontend/web-app/src/components/profile/NotificationsSection.tsx`
- Modify: `frontend/web-app/src/components/profile/NotificationsSection.css`
- Create: `frontend/web-app/src/components/profile/NotificationsSection.test.tsx`
- Modify: `frontend/web-app/src/locales/ru.json`

**Interfaces:**
- Produces: immediate logout after a successful password change; notifications distinguish error from empty and paginate by 20; the current button is labeled as viewing rather than configuring notifications.

- [ ] **Step 1: Add failing tests**

Assert successful password change immediately invokes logout and navigation even if the component unmounts. Assert notification loading, error/retry, empty, unread filter, mark-read invalidation, and load-more behavior.

- [ ] **Step 2: Run and confirm failure**

```bash
cd frontend/web-app && npm run test -- ProfileSettingsSection.test.tsx NotificationsSection.test.tsx
```

- [ ] **Step 3: Implement minimal behavior**

Remove the 1800 ms timer and cleanup dependency. Log out and navigate immediately after the success response. Change the copy from configure to view. Add explicit query error UI with `refetch` and 20-item pagination.

- [ ] **Step 4: Run frontend tests**

```bash
cd frontend/web-app && npm run test
```

- [ ] **Step 5: Commit**

```bash
git add frontend/web-app/src/components/profile frontend/web-app/src/locales/ru.json
git commit -m "fix: make profile security and notifications reliable"
```

### Task 7: Improve modal accessibility and copy consistency

**Files:**
- Modify: `frontend/web-app/src/components/profile/RefundRequestModal.tsx`
- Modify: `frontend/web-app/src/components/profile/ComplaintModal.tsx`
- Modify: `frontend/web-app/src/components/profile/ReviewModal.tsx`
- Create: `frontend/web-app/src/components/profile/useDialogFocus.ts`
- Create: `frontend/web-app/src/components/profile/ProfileModals.test.tsx`
- Modify: `frontend/web-app/src/pages/ProfileDesktop.tsx`
- Modify: `frontend/web-app/src/pages/ProfileMobile.tsx`
- Modify: `frontend/web-app/src/locales/ru.json`

**Interfaces:**
- Produces: accessible dialogs with Escape close, labelled fields, focus containment/restoration, consistent five-working-day refund copy, and coupons without expiry sorted after dated coupons.

- [ ] **Step 1: Add failing tests**

For every modal assert `role=dialog`, `aria-modal=true`, accessible title, labelled fields, close button label, Escape close, initial focus inside the dialog, Tab containment, and focus restoration. Add sorting tests showing a dated active coupon before a coupon with no expiry. Assert both refund texts say five working days.

- [ ] **Step 2: Run and confirm failure**

```bash
cd frontend/web-app && npm run test -- ProfileModals.test.tsx ProfileDesktop.test.tsx ProfileMobile.test.tsx
```

- [ ] **Step 3: Implement shared accessibility behavior**

Reuse one local dialog focus helper rather than duplicating listeners in three components. Use `expiresAt ?? '9999-12-31'` as the sort key. Align Russian FAQ and modal copy to five working days.

- [ ] **Step 4: Run the complete verification**

```bash
./gradlew :services:identity-service:test
./gradlew :services:order-service:test
cd frontend/web-app && npm run test
cd frontend/web-app && npm run lint
cd frontend/web-app && npm run build
```

Expected: all commands exit 0.

- [ ] **Step 5: Commit**

```bash
git add frontend/web-app
git commit -m "fix: improve profile accessibility and copy"
```
