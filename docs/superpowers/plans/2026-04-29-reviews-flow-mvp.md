# Reviews Flow MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the TopDim reviews flow end-to-end: public approved reviews, buyer-only review submission after coupon usage, moderation, frontend form, and regression tests.

**Architecture:** Reviews stay owned by `coupon-service`, because they belong to coupon content and public coupon rating. Purchase/usage eligibility is owned by `order-service`, because only it knows whether the user has a `USED` purchased coupon. `coupon-service` must call an internal `order-service` eligibility endpoint before accepting a review.

**Tech Stack:** Java 21, Spring Boot, Spring Security, OpenFeign, PostgreSQL/Flyway, JUnit 5/Mockito/MockMvc, React 19, Vite, React Query, Zustand, React Hook Form + Zod if already available.

---

## Required Skills And Project Rules

- Read and follow `AGENTS.md` before changing business logic.
- Use `services/.agent/skills/api-design-principles/SKILL.md` for endpoint shape and error semantics.
- Use `services/.agent/skills/spring-boot-test-patterns/SKILL.md` for backend tests.
- Use `services/.agent/skills/database-schema-designer/SKILL.md` if adding review database constraints or indexes.
- Use `frontend/.agent/skills/react/SKILL.md` for React component structure.
- Use `frontend/.agent/skills/react-hook-form-zod/SKILL.md` for the review form if `react-hook-form` and `zod` are already installed in `frontend/web-app`.
- Use `frontend/.agent/skills/frontend-design/SKILL.md` only to make the form fit the existing TopDim coupon detail style. Do not redesign the page.

## Business Decisions For MVP

- Approved reviews are public and visible to guests and logged-in users.
- Creating a review requires authentication as a regular buyer/user.
- A user may submit a review only after they have at least one `USED` purchased coupon for the same `couponOfferId`.
- `ACTIVE`, `EXPIRED`, `REFUNDED`, missing, or other-user purchased coupons must not allow review creation.
- New reviews are created with status `PENDING`.
- `PENDING` and `REJECTED` reviews are not visible on public coupon pages.
- If a user already has a `PENDING` or `APPROVED` review for the coupon offer, block duplicate submission with HTTP `409`.
- If a user has a `REJECTED` review for the coupon offer, allow one resubmission by updating that same review back to `PENDING`, replacing rating/comment, and clearing `rejectReason`.
- Moderators/admins approve or reject reviews through existing `/api/v1/mod/reviews` endpoints, but admin-app must expose a usable page for this.
- Merchant/contact visibility is not part of this task. Keep current coupon detail contact behavior unchanged.

## Current State To Respect

- `frontend/web-app/src/pages/CouponDetailPage.tsx` already loads and displays reviews, but has no create-review form.
- `frontend/web-app/src/api/reviews.ts` already has `getForCoupon`, `create`, and `getMine`.
- `services/coupon-service/src/main/java/uz/topdim/coupon/controller/ReviewController.java` has public-looking endpoints, but security currently does not permit guest `GET /api/v1/reviews/coupon/**`.
- `services/coupon-service/src/main/java/uz/topdim/coupon/service/ReviewService.java` creates reviews without checking purchase/usage eligibility.
- `services/coupon-service/src/main/java/uz/topdim/coupon/controller/ModCouponController.java` already has review moderation endpoints.
- `frontend/admin-app/src/components/layout/AdminLayout.tsx` has a menu item for `/support/reviews`, but `frontend/admin-app/src/App.tsx` still has a TODO for the review page route.
- `services/order-service/src/main/java/uz/topdim/order/entity/PurchasedCoupon.java` has `userId`, `couponOfferId`, `status`, `usedAt`, and `expiresAt`, which is enough for review eligibility.

---

## Task 1: Make Approved Coupon Reviews Public

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/config/SecurityConfig.java`
- Test: add/update controller security coverage in `services/coupon-service/src/test/java/uz/topdim/coupon/controller/ReviewControllerTest.java` if a controller test pattern exists; otherwise cover via service/security integration test.

- [ ] Add public read access for approved coupon reviews:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/reviews/coupon/**").permitAll()
```

- [ ] Keep all write endpoints authenticated:

```java
// POST /api/v1/reviews remains authenticated through .anyRequest().authenticated()
// GET /api/v1/reviews/my remains authenticated through .anyRequest().authenticated()
```

- [ ] Run coupon-service tests:

```bash
./gradlew :services:coupon-service:test
```

Expected: tests pass. If security tests are added first, verify they fail before changing `SecurityConfig`, then pass after the change.

---

## Task 2: Add Order-Service Review Eligibility Endpoint

**Files:**
- Create: `services/order-service/src/main/java/uz/topdim/order/dto/ReviewEligibilityResponse.java`
- Create: `services/order-service/src/main/java/uz/topdim/order/controller/InternalReviewEligibilityController.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/PurchasedCouponRepository.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/config/SecurityConfig.java`
- Test: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- Test: `services/order-service/src/test/java/uz/topdim/order/controller/InternalReviewEligibilityControllerTest.java`

- [ ] Create DTO:

```java
package uz.topdim.order.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewEligibilityResponse {
    private boolean eligible;
    private String reason;
    private Long purchasedCouponId;
    private LocalDateTime usedAt;
}
```

- [ ] Add repository method:

```java
Optional<PurchasedCoupon> findFirstByUserIdAndCouponOfferIdAndStatusOrderByUsedAtDesc(
        Long userId,
        Long couponOfferId,
        PurchasedCouponStatus status
);
```

- [ ] Add service method:

```java
@Transactional(readOnly = true)
public ReviewEligibilityResponse getReviewEligibility(Long userId, Long couponOfferId) {
    return purchasedCouponRepository
            .findFirstByUserIdAndCouponOfferIdAndStatusOrderByUsedAtDesc(
                    userId, couponOfferId, PurchasedCouponStatus.USED)
            .map(pc -> ReviewEligibilityResponse.builder()
                    .eligible(true)
                    .reason("USED_COUPON_FOUND")
                    .purchasedCouponId(pc.getId())
                    .usedAt(pc.getUsedAt())
                    .build())
            .orElseGet(() -> ReviewEligibilityResponse.builder()
                    .eligible(false)
                    .reason("REVIEW_ALLOWED_AFTER_COUPON_USAGE")
                    .build());
}
```

- [ ] Add internal endpoint:

```java
@RestController
@RequestMapping("/api/v1/internal/reviews")
@RequiredArgsConstructor
public class InternalReviewEligibilityController {
    private final OrderService orderService;

    @GetMapping("/eligibility")
    public ResponseEntity<ApiResponse<ReviewEligibilityResponse>> getEligibility(
            @RequestParam Long userId,
            @RequestParam Long couponOfferId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getReviewEligibility(userId, couponOfferId)
        ));
    }
}
```

- [ ] Permit internal order-service endpoint in `SecurityConfig`:

```java
.requestMatchers("/api/v1/internal/**").permitAll()
```

Place it before `.anyRequest().authenticated()`.

- [ ] Add tests:

```java
@Test
void getReviewEligibility_usedCoupon_returnsEligibleWithPurchasedCouponId() {
    // user owns USED purchased coupon for couponOfferId -> eligible true
}

@Test
void getReviewEligibility_activeCoupon_returnsNotEligible() {
    // ACTIVE purchase is not enough because service was not used yet
}

@Test
void getReviewEligibility_otherUserCoupon_returnsNotEligible() {
    // coupon used by another user must not unlock review for current user
}

@Test
void getReviewEligibility_noCoupon_returnsNotEligible() {
    // missing purchase -> eligible false
}
```

- [ ] Run:

```bash
./gradlew :services:order-service:test
```

---

## Task 3: Enforce Review Eligibility In Coupon-Service

**Files:**
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/client/OrderReviewEligibilityClient.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/client/ReviewEligibilityResponse.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/ReviewRepository.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/ReviewService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/ReviewController.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateReviewRequest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/ReviewServiceTest.java`

- [ ] Add Feign client:

```java
@FeignClient(name = "order-service", path = "/api/v1")
public interface OrderReviewEligibilityClient {
    @GetMapping("/internal/reviews/eligibility")
    ApiResponse<ReviewEligibilityResponse> getReviewEligibility(
            @RequestParam("userId") Long userId,
            @RequestParam("couponOfferId") Long couponOfferId
    );
}
```

- [ ] Add coupon-service DTO matching order-service response:

```java
@Data
public class ReviewEligibilityResponse {
    private boolean eligible;
    private String reason;
    private Long purchasedCouponId;
    private LocalDateTime usedAt;
}
```

- [ ] Add repository methods:

```java
Optional<Review> findFirstByUserIdAndCouponOfferIdOrderByCreatedAtDesc(Long userId, Long couponOfferId);

boolean existsByUserIdAndCouponOfferIdAndStatusIn(
        Long userId,
        Long couponOfferId,
        Collection<ReviewStatus> statuses
);
```

- [ ] Strengthen DTO validation:

```java
@Size(min = 10, max = 2000, message = "Комментарий должен быть от 10 до 2000 символов")
private String comment;
```

- [ ] Update `ReviewService.createReview` business rules:

```java
// 1. Find coupon or throw ResourceNotFoundException("Купон не найден")
// 2. Trim comment and reject blank/too short through validation
// 3. If user has PENDING or APPROVED review for this couponOfferId -> throw IllegalStateException("Вы уже оставили отзыв по этому купону")
// 4. Call order-service eligibility
// 5. If not eligible -> throw IllegalStateException("Оставить отзыв можно после использования купона")
// 6. If latest review is REJECTED -> update it to PENDING with new rating/comment/userName and clear rejectReason
// 7. Else create a new PENDING review
```

- [ ] Add authenticated eligibility endpoint for frontend:

```java
@GetMapping("/coupon/{couponId}/eligibility")
public ResponseEntity<ApiResponse<ReviewEligibilityResponse>> getReviewEligibility(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable Long couponId
) {
    return ResponseEntity.ok(ApiResponse.success(
            reviewService.getReviewEligibility(userId, couponId)
    ));
}
```

- [ ] Tests to add or update:

```java
@Test
void createReview_usedCoupon_createsPendingReview() {}

@Test
void createReview_activeOrMissingUsedCoupon_throwsConflict() {}

@Test
void createReview_pendingExistingReview_throwsConflict() {}

@Test
void createReview_approvedExistingReview_throwsConflict() {}

@Test
void createReview_rejectedExistingReview_resubmitsSameReviewAsPending() {}

@Test
void getApprovedReviewsForCoupon_returnsOnlyApprovedReviews() {}
```

- [ ] Run:

```bash
./gradlew :services:coupon-service:test
```

---

## Task 4: Add Buyer Review Form On Coupon Detail Page

**Files:**
- Modify: `frontend/web-app/src/api/reviews.ts`
- Create: `frontend/web-app/src/components/coupon-detail/CouponReviewsSection.tsx`
- Create: `frontend/web-app/src/components/coupon-detail/ReviewForm.tsx`
- Modify: `frontend/web-app/src/pages/CouponDetailPage.tsx`
- Modify: `frontend/web-app/src/pages/CouponDetailPage.css`

- [ ] Extend API:

```ts
export interface ReviewEligibilityData {
  eligible: boolean;
  reason?: string;
  purchasedCouponId?: number;
  usedAt?: string;
}

getEligibility: (couponId: number) =>
  apiClient.get<ApiResponse<ReviewEligibilityData>>(`/api/v1/reviews/coupon/${couponId}/eligibility`),
```

- [ ] Move current review list UI from `CouponDetailPage.tsx` into `CouponReviewsSection`.

- [ ] `CouponReviewsSection` behavior:

```text
Guest:
  - sees approved reviews
  - sees CTA: "Войдите, чтобы оставить отзыв после использования купона"

Logged-in user without USED coupon:
  - sees approved reviews
  - sees notice: "Оставить отзыв можно после использования купона"

Eligible logged-in user:
  - sees approved reviews
  - sees form with rating 1-5 and comment
  - submit calls reviewsApi.create
  - after success, show "Отзыв отправлен на модерацию"
  - invalidate ['coupon-reviews', id] and review eligibility query

Duplicate/rejected conflict from backend:
  - show backend message in the form, do not crash page
```

- [ ] Use `useAuthStore` to know whether user is authenticated.

- [ ] If `react-hook-form` and `zod` are installed, use them for:

```ts
const schema = z.object({
  rating: z.number().min(1).max(5),
  comment: z.string().trim().min(10).max(2000),
});
```

If they are not installed, use controlled React state and do not install new dependencies just for this task.

- [ ] Keep existing TopDim detail page design. Do not change hero, coupon variants, merchant block, colors, or global layout.

- [ ] Frontend manual states to verify:

```text
Guest opens coupon detail -> approved reviews visible, form hidden.
Logged-in buyer with no used coupon -> notice visible, form hidden.
Logged-in buyer with USED coupon -> form visible.
Submit review -> success state says moderation is pending.
Refresh page -> pending review is not public yet.
```

- [ ] Run:

```bash
cd frontend/web-app && npm run build
```

---

## Task 5: Add Admin Review Moderation Page

**Files:**
- Create: `frontend/admin-app/src/features/support/ReviewsPage.tsx`
- Create or modify: `frontend/admin-app/src/api/reviews.ts` or the existing admin API module pattern.
- Modify: `frontend/admin-app/src/App.tsx`

- [ ] Add admin API functions:

```ts
getPendingReviews(page = 0, size = 20) -> GET /api/v1/mod/reviews
reviewUserReview(id, status, reason?) -> PATCH /api/v1/mod/reviews/{id}/review
```

- [ ] Add route:

```tsx
<Route path="/support/reviews" element={<ReviewsPage />} />
```

- [ ] `ReviewsPage` must show:

```text
couponOfferId
userName / userId
rating
comment
createdAt
Approve button
Reject button with required reason
empty state when no pending reviews
```

- [ ] Business behavior:

```text
APPROVE -> review becomes public and coupon rating/count includes it.
REJECT -> reject reason is saved and user can resubmit later.
Unknown status must not be sent by UI.
```

- [ ] Run:

```bash
cd frontend/admin-app && npm run build
```

---

## Task 6: Update QA And Product Docs

**Files:**
- Modify: `docs/product/flows/coupon-flow.md`
- Modify: `docs/product/roles.md`
- Create: `docs/qa/reviews-flow-checklist.md`

- [ ] Document the review lifecycle:

```text
USED purchased coupon -> user submits review -> PENDING -> moderator approves/rejects -> APPROVED appears publicly, REJECTED can be resubmitted.
```

- [ ] Document public/private behavior:

```text
Guests can read approved reviews.
Guests cannot submit reviews.
Authenticated users without USED coupon cannot submit reviews.
Only moderator/admin/super-admin can approve or reject reviews.
```

- [ ] Add manual QA checklist:

```text
1. Guest can open coupon detail and read approved reviews.
2. Guest does not see review form.
3. Buyer with ACTIVE but unused coupon cannot review.
4. Buyer with USED coupon can submit review.
5. Submitted review is PENDING and hidden publicly.
6. Moderator approves review.
7. Review appears on coupon detail and rating/count updates.
8. Moderator rejects review with reason.
9. Buyer can resubmit rejected review.
10. Duplicate PENDING/APPROVED review is blocked.
```

---

## Final Verification

- [ ] Run backend tests:

```bash
./gradlew :services:order-service:test :services:coupon-service:test
```

- [ ] Run frontend builds:

```bash
cd frontend/web-app && npm run build
cd frontend/admin-app && npm run build
```

- [ ] Manually test the full flow with seed data:

```text
Buyer purchases coupon -> merchant redeems coupon -> buyer opens coupon detail -> buyer leaves review -> admin approves -> guest sees review.
```

- [ ] Confirm these risks are closed:

```text
Unauthenticated review creation is blocked.
Reviews without real USED coupon are blocked.
Duplicate PENDING/APPROVED reviews are blocked.
Rejected reviews can be resubmitted.
Pending/rejected reviews are hidden publicly.
Approved reviews are visible to guests.
Admin moderation page is usable.
```

