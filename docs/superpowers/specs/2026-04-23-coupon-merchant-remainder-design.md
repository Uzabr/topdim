# Coupon-Merchant Remainder Design

**Goal:** finish the remaining `coupon + merchant` work from the full separation initiative without touching `bazaar-service` in this iteration.

**Current state:** the schema split is already far along. `merchant_locations`, `offer_description`, canonical bot lead lookup, admin quick-create with `locations[]`, and catalog/search changes are in place. The remaining risk is no longer schema creation; it is runtime safety, incomplete canonical read-model adoption, and lingering compatibility layers in API/TypeScript contracts.

**Explicit non-goals for this design:**
- no `bazaar-service` parity work;
- no `/api/v1/directory/*` migration;
- no removal of bazaar/domain classes from `coupon-service`;
- no unrelated UI redesign.

## Approaches

### 1. Stability only

Close the publication risk, finish the canonical review screen, and stop.

**Pros**
- lowest implementation risk;
- fastest path to safer production behavior.

**Cons**
- deprecated DTO/API fields remain spread through backend and frontend;
- next cleanup step stays expensive.

### 2. Staged canonicalization (recommended)

First harden business rules and finish canonical read-model adoption, then localize compatibility fields, and only after that remove deprecated coupon/merchant API fields.

**Pros**
- addresses the highest business risk first;
- keeps rollout reversible;
- reduces chance of breaking frontend while still moving the codebase toward the target contract.

**Cons**
- requires two cleanup passes instead of one.

### 3. Aggressive cleanup now

Immediately remove deprecated coupon/merchant request/response fields and fallback helpers together with the remaining UI cleanup.

**Pros**
- fastest route to the clean end state;
- least leftover compatibility code.

**Cons**
- highest breakage risk because some consumers still compile against deprecated fields;
- harder to isolate regressions once behavior and contract changes land together.

## Recommendation

Use **Approach 2: staged canonicalization**.

This repository already took the destructive DB step early, so the safest next move is to reconcile runtime behavior and API contracts around the new canonical model instead of attempting one more broad cleanup. In practice, that means:

1. add a publication safety gate so `ACTIVE` coupons cannot exist without a publication-ready merchant;
2. finish the remaining canonical read-model work in admin/storefront coupon flows;
3. keep compatibility fields temporarily, but make them derived-only and stop relying on them internally;
4. remove deprecated coupon/merchant API fields only after consumer verification.

## Design

### 1. Publication safety

The coupon lifecycle currently allows `WAITING_FOR_MERCHANT -> ACTIVE` without explicitly verifying that the merchant is publication-ready. That is the main business risk left in the coupon/merchant module because storefront coupon detail and contacts already assume `merchant.primaryLocation` is the canonical source.

The design is to introduce one service-level guard in `CouponOfferService` and invoke it in every path that publishes a coupon:

- `approveByMerchant(id)`
- `updateStatus(id, CouponStatus.ACTIVE)`

The guard should fail fast when:

- coupon has no merchant;
- merchant has no primary location;
- primary location exists but is inactive.

For this iteration, the publication-ready minimum is:

- merchant exists in the database;
- merchant has an active `primaryLocation`;
- `primaryLocation.address` is non-empty.

`phone` and `workingHours` remain optional for the publication gate in this iteration because current storefront rendering shows them conditionally, while address is the structural field that drives the contact/map block.

The error message should be explicit enough for admin/partner troubleshooting, not a generic null/state failure.

### 1.1. Lifecycle meaning

The intended lifecycle is:

1. `LEAD` — coupon request exists, merchant may still be incomplete.
2. `DRAFT` — moderator edits coupon and completes merchant data.
3. `WAITING_FOR_MERCHANT` — merchant reviews the prepared coupon.
4. `ACTIVE` — coupon is visible to customers only after merchant publication-readiness is satisfied.

The key rule is that publication does **not** auto-create or auto-complete merchant data. Merchant readiness must be achieved before publication, not during publication.

### 2. Canonical read-model completion

`MerchantReviewPage` still renders legacy fallback fields for offer/contact information. That weakens the migration because moderation can still appear to “work” when canonical data is incomplete or wrong. The admin review screen should move to canonical-only rendering for:

- offer block: `offerDescription`;
- merchant block: `merchant.name`, `merchant.description`, `merchant.logoUrl`;
- contacts block: `merchant.primaryLocation`.

At the same time, storefront preview extraction should stop being copy-pasted across pages. The design is to extract one shared preview helper in `frontend/web-app` so `HomePage`, `FavoritesPage`, and `CouponCatalogPage` derive teaser text consistently from canonical `offerDescription`.

### 3. Compatibility isolation

The deprecated coupon/merchant fields that remain in DTOs and TypeScript types should be treated as a temporary outer compatibility shell, not as an internal model. This iteration keeps them for safety, but narrows their role:

- backend request DTOs may still accept legacy text/contact fields as transition fallback;
- backend response DTOs may still expose deprecated fields only when derived from canonical data;
- frontend/admin code should prefer canonical fields everywhere internally.

This keeps the rollout safe while making the eventual removal mechanical instead of risky.

### 4. Final coupon/merchant API cleanup

Once Task 1-3 are green and no UI code relies on deprecated coupon/merchant fields, the final coupon/merchant cleanup can remove them from:

- `CouponOfferResponse`
- `MerchantResponse`
- `CreateCouponOfferRequest`
- `CreateMerchantRequest`
- `frontend/web-app/src/api/coupons.ts`

This cleanup is gated on consumer verification and should stay separate from the business-safety work.

## Files and ownership

### Backend business logic
- `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceBusinessLogicTest.java`

### Backend contract layer
- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponOfferResponse.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantResponse.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateMerchantRequest.java`
- `services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java`

### Frontend coupon/admin read-model
- `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx`
- `frontend/web-app/src/pages/HomePage.tsx`
- `frontend/web-app/src/pages/FavoritesPage.tsx`
- `frontend/web-app/src/pages/CouponCatalogPage.tsx`
- `frontend/web-app/src/api/coupons.ts`
- `frontend/web-app/src/utils/couponPreview.ts` (new)

## Testing strategy

### Automated
- targeted JUnit tests for publication guard and lifecycle regressions;
- targeted JUnit tests for partner/admin canonical-first precedence;
- existing coupon-service test classes remain the main safety net.

### Build verification
- `npm --prefix frontend/admin-app run build`
- `npm --prefix frontend/web-app run build`

### Manual smoke
- admin: review coupon page renders canonical offer/contact blocks;
- admin: coupon publish path rejects merchant without publication-ready primary location;
- storefront: catalog/home/favorites teaser text is still readable and stable.

## Milestones

### Milestone A: Safety and read-model
- publication guard implemented;
- publication-ready merchant rule documented in code/tests;
- review screen canonicalized;
- storefront preview helper extracted.

### Milestone B: Compatibility isolation
- deprecated fields remain derived-only;
- frontend/admin code no longer depends on them internally;
- regression tests cover canonical-first behavior.

### Milestone C: Final coupon/merchant contract cleanup
- deprecated coupon/merchant API fields removed;
- docs updated to match the surviving contract.
