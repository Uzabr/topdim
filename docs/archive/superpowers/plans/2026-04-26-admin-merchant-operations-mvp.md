# Admin Merchant Operations MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the admin-side operational layer for partners and merchants so TopDim can manage approved partner merchants, their profile data, publication readiness, locations, active status, and related coupons.

**Architecture:** Keep `identity-service` responsible for partner applications and partner user creation. Keep `coupon-service` responsible for merchant profiles, merchant locations, publication readiness, and merchant coupon relationships. Build admin-app screens on top of existing admin APIs without changing the current coupon form merchant selector contract.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Flyway if needed, OpenFeign, JUnit 5, Mockito, MockMvc, React 19, TypeScript, React Query, React Hook Form or Ant Design Form, Zod if used in the current admin feature style, Ant Design 6, Vite.

---

## Required Skills

- [ ] Read and follow `superpowers:using-superpowers` before starting.
- [ ] Use `superpowers:test-driven-development` for backend behavior changes.
- [ ] Use `superpowers:systematic-debugging` if a test, build, API contract, or frontend behavior is unexpected.
- [ ] Use `superpowers:verification-before-completion` before reporting completion.
- [ ] Use `services/.agent/skills/api-design-principles/SKILL.md` before adding or changing admin APIs.
- [ ] Use `services/.agent/skills/spring-boot-test-patterns/SKILL.md` for service/controller tests.
- [ ] Use `services/.agent/skills/spring-boot-crud-patterns/SKILL.md` for merchant profile CRUD patterns.
- [ ] Use `services/.agent/skills/database-schema-designer/SKILL.md` only if a migration is required.
- [ ] Use `frontend/.agent/skills/react/SKILL.md` before changing `frontend/admin-app`.
- [ ] Use `frontend/.agent/skills/react-hook-form-zod/SKILL.md` if adding complex forms.
- [ ] Use `frontend/.agent/skills/frontend-design/SKILL.md` for admin UI layout and interaction polish.

## Current State From Code

- [ ] Public partner application form exists in `frontend/web-app`.
- [ ] Admin partner application review exists in `frontend/admin-app/src/features/partners/PartnerApplicationsPage.tsx`.
- [ ] `identity-service` approves applications and creates/promotes a `PARTNER` user.
- [ ] `identity-service` calls `coupon-service` internal merchant onboarding endpoint.
- [ ] `coupon-service` already has admin merchant create/update/read endpoints under `/api/v1/admin/merchants`.
- [ ] Existing `GET /api/v1/admin/merchants` returns a list and is used by `CouponFormPage` merchant selector.
- [ ] `MerchantService` already supports normalized `locations[]`, `primaryLocation`, and safe location update rules.
- [ ] There is no dedicated admin merchant management page in `frontend/admin-app`.
- [ ] There is no admin merchant detail page showing coupons, readiness, locations, and operational status together.

## Important Coordination Rule

- [ ] Start this plan only after the current `purchase-lifecycle-reconciliation` work is complete or work in a separate branch/worktree.
- [ ] Do not overwrite active AI changes in `payment-service`, `order-service`, `coupon-service` sale/redemption files, or frontend cart files.
- [ ] Run `git status --short` before editing and treat unrelated changes as user/AI-owned.

## Business Rules

- [ ] A merchant created from partner onboarding must be linked to the partner `userId`.
- [ ] Admin must be able to find approved merchants without opening coupons first.
- [ ] Existing coupon form merchant selector must keep working.
- [ ] Merchant contact data lives in `merchant_locations`, not legacy merchant contact fields.
- [ ] A merchant is publication-ready only when it is active and has an active primary location with a non-empty address and phone.
- [ ] A coupon must not be published if its merchant is not publication-ready.
- [ ] Deactivating a merchant with `ACTIVE` or `WAITING_FOR_MERCHANT` coupons must be blocked in this MVP.
- [ ] Admin can edit merchant profile and locations, but deleting all locations remains blocked when dependent active/waiting coupons exist.
- [ ] Admin can open all coupons belonging to a merchant from the merchant profile.
- [ ] Admin can start creating a coupon for a specific merchant from the merchant profile.

## Scope

- [ ] Add admin merchant management API without breaking current merchant selector API.
- [ ] Add publication readiness to merchant admin read model.
- [ ] Add active/inactive merchant operation with safe business rule.
- [ ] Add merchant coupons endpoint for admin detail page.
- [ ] Add admin-app merchants list page.
- [ ] Add admin-app merchant detail/edit page or drawer.
- [ ] Add link from approved partner application to linked merchant.
- [ ] Add tests for business rules and API behavior.

## Out Of Scope

- [ ] Do not build a full partner cabinet.
- [ ] Do not let partners edit their own merchant profile yet.
- [ ] Do not implement forced deactivation that archives active coupons.
- [ ] Do not build staff invitations.
- [ ] Do not build real merchant analytics beyond basic coupon list/status counts.
- [ ] Do not touch bazaar/shop service.

---

### Task 0: Baseline And Contract Safety

**Files:** No code changes.

- [ ] Run `git status --short` and record which files are already modified.
- [ ] Run `./gradlew :services:coupon-service:test`.
- [ ] Run `./gradlew :services:identity-service:test`.
- [ ] Run `npm run build` inside `frontend/admin-app` if dependencies are installed.
- [ ] Confirm `frontend/admin-app/src/features/coupons/CouponFormPage.tsx` uses `GET /api/v1/admin/merchants` as a plain list.
- [ ] Do not change the response shape of `GET /api/v1/admin/merchants`.

**Done Criteria:**
- [ ] Baseline is known.
- [ ] Current merchant selector contract is protected.
- [ ] Any pre-existing failing tests are documented before implementation.

---

### Task 1: Add Admin Merchant Management Read API

**Files:**
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/dto/AdminMerchantSummaryResponse.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantPublicationReadinessResponse.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/repository/MerchantRepository.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferRepository.java`.
- Add or modify `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java`.
- Add or modify controller tests under `services/coupon-service/src/test/java/uz/topdim/coupon/controller`.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/api-design-principles/SKILL.md`.
- [ ] Keep existing `GET /api/v1/admin/merchants` unchanged for coupon form selector.
- [ ] Add new endpoint `GET /api/v1/admin/merchants/page`.
- [ ] Support query params `page`, `size`, `search`, `active`.
- [ ] Return `Page<AdminMerchantSummaryResponse>`.
- [ ] Summary must include `id`, `name`, `logoUrl`, `contactPerson`, `email`, `userId`, `active`, `primaryLocation`, `publicationReady`, `publicationBlockReason`, `activeCouponsCount`, `waitingCouponsCount`, `totalCouponsCount`.
- [ ] Add service method `getAdminMerchantPage(search, active, pageable)`.
- [ ] Add repository query for merchant search by name, contact person, email, or primary location phone if practical.
- [ ] If joining locations is too invasive, search only merchant fields in this task and leave phone search out with a clear note in final report.
- [ ] Add tests for no filter, active filter, search filter, and publication readiness fields.
- [ ] Run `./gradlew :services:coupon-service:test`.

**Done Criteria:**
- [ ] Admin can page/search merchants without breaking existing list endpoint.
- [ ] Merchant summaries expose readiness and coupon counts.
- [ ] Tests cover readiness and filtering.

---

### Task 2: Add Merchant Publication Readiness Detail

**Files:**
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantResponse.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`.
- Modify tests in `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java`.

**Implementation Steps:**
- [ ] Add `publicationReady` and `publicationBlockReason` to `MerchantResponse`.
- [ ] Compute readiness from current merchant data.
- [ ] Ready when merchant is active and primary location exists, primary location is active, address is not blank, and phone is not blank.
- [ ] Block reason examples must be user-facing: `Мерчант не активен`, `Нет primary location`, `Не указан адрес`, `Не указан телефон`.
- [ ] Keep readiness logic in one private method or small helper to avoid duplicated conditions.
- [ ] Add tests for ready merchant, inactive merchant, missing primary location, missing address, missing phone.
- [ ] Run `./gradlew :services:coupon-service:test`.

**Done Criteria:**
- [ ] Merchant detail tells admin exactly why publication is blocked.
- [ ] Readiness logic matches coupon publication gate.

---

### Task 3: Add Safe Merchant Active Status Operation

**Files:**
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/dto/UpdateMerchantActiveRequest.java`.
- Modify `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java`.
- Add or modify controller tests under `services/coupon-service/src/test/java/uz/topdim/coupon/controller`.

**Implementation Steps:**
- [ ] Add DTO with `private boolean active;`.
- [ ] Add endpoint `PATCH /api/v1/admin/merchants/{id}/active`.
- [ ] If setting `active=true`, activate merchant and return updated `MerchantResponse`.
- [ ] If setting `active=false`, check merchant has no `ACTIVE` or `WAITING_FOR_MERCHANT` coupons.
- [ ] If dependent coupons exist, throw business conflict with message `Нельзя деактивировать мерчанта с активными или ожидающими подтверждения купонами`.
- [ ] If no dependent coupons exist, set merchant inactive and return updated response.
- [ ] Add tests for activate, deactivate without dependent coupons, deactivate blocked with dependent coupons.
- [ ] Run `./gradlew :services:coupon-service:test`.

**Done Criteria:**
- [ ] Admin can safely activate/deactivate merchants.
- [ ] Active coupons cannot become orphaned under inactive merchant by accident.

---

### Task 4: Add Admin Merchant Coupons Endpoint

**Files:**
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java` or create a focused method in `MerchantService` if that fits current boundaries better.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferRepository.java`.
- Add or modify tests in `services/coupon-service/src/test/java/uz/topdim/coupon/service` and controller tests.

**Implementation Steps:**
- [ ] Add endpoint `GET /api/v1/admin/merchants/{id}/coupons`.
- [ ] Support params `page`, `size`, and optional `status`.
- [ ] Verify merchant exists before returning coupon page.
- [ ] Return existing coupon response DTO if safe for admin use, or a smaller summary DTO if existing response is too heavy.
- [ ] Include coupon status, title, option count or from price, sold counters if available, buyUntil, useUntil, createdAt.
- [ ] Add tests for merchant not found, empty coupons, status filter, and paging.
- [ ] Run `./gradlew :services:coupon-service:test`.

**Done Criteria:**
- [ ] Admin can see all coupons for a merchant from merchant detail.
- [ ] Merchant coupon list does not require manual search in moderation board.

---

### Task 5: Add Admin Merchants Frontend Feature

**Files:**
- Create `frontend/admin-app/src/features/merchants/MerchantsPage.tsx`.
- Create `frontend/admin-app/src/features/merchants/MerchantDetailPage.tsx` or `MerchantDrawer.tsx`.
- Create `frontend/admin-app/src/features/merchants/api.ts`.
- Create `frontend/admin-app/src/features/merchants/types.ts`.
- Create optional `frontend/admin-app/src/features/merchants/MerchantForm.tsx`.
- Modify `frontend/admin-app/src/App.tsx`.
- Modify `frontend/admin-app/src/components/layout/AdminLayout.tsx`.
- Modify `frontend/admin-app/src/types/index.ts` only if shared types are preferred by existing project style.

**Implementation Steps:**
- [ ] Use `frontend/.agent/skills/react/SKILL.md`.
- [ ] Use `frontend/.agent/skills/frontend-design/SKILL.md` for admin layout polish.
- [ ] Add menu item `Мерчанты` for `ADMIN` and `SUPER_ADMIN`.
- [ ] Add route `/catalog/merchants` or `/users/merchants`; prefer `/catalog/merchants` because merchants are catalog/publishing infrastructure.
- [ ] Merchants page must show table with name, active status, publication readiness, primary address, phone, coupons count, and actions.
- [ ] Add search input and active status filter.
- [ ] Add detail action to open merchant detail route or drawer.
- [ ] Detail must show profile fields, locations, readiness reason, linked `userId`, coupon counts, and related coupons.
- [ ] Add edit form for name, description, logoUrl, coverUrl, email, website, contactPerson, and locations.
- [ ] Form must support one primary location and prevent submitting two primary locations.
- [ ] Add active/inactive toggle with confirmation modal.
- [ ] If deactivation is blocked by backend, show backend message to admin.
- [ ] Add button `Создать купон для мерчанта`, navigating to `/moderation/coupons/create?merchantId=<id>`.
- [ ] Run `npm run build` inside `frontend/admin-app`.

**Done Criteria:**
- [ ] Admin can find, inspect, edit, and safely activate/deactivate merchants from admin-app.
- [ ] Admin can navigate from merchant to coupon creation.
- [ ] UI shows readiness blockers instead of hiding why coupon publication would fail.

---

### Task 6: Preselect Merchant In Coupon Create Form

**Files:**
- Modify `frontend/admin-app/src/features/coupons/CouponFormPage.tsx`.

**Implementation Steps:**
- [ ] Read `merchantId` from query string on create page.
- [ ] When merchant list loads, preselect that merchant if it exists.
- [ ] Do not remove the existing merchant selector.
- [ ] If `merchantId` is invalid or not found, show a non-blocking warning and let admin choose manually.
- [ ] Ensure quick merchant creation inside coupon form still works.
- [ ] Run `npm run build` inside `frontend/admin-app`.

**Done Criteria:**
- [ ] From merchant detail, admin can start a coupon with merchant already selected.
- [ ] Existing coupon creation flow remains intact.

---

### Task 7: Link Partner Applications To Merchant Operations

**Files:**
- Modify `frontend/admin-app/src/features/partners/PartnerApplicationsPage.tsx`.
- Optionally modify `services/identity-service/src/main/java/uz/topdim/identity/dto/PartnerApplicationResponse.java` only if current response lacks needed fields.

**Implementation Steps:**
- [ ] For approved applications with `linkedMerchantId`, show action `Открыть мерчанта`.
- [ ] Navigate to merchant detail using linked merchant id.
- [ ] For approved applications with no linked merchant id, show clear warning tag `Мерчант не привязан`.
- [ ] Keep approve/reject flow unchanged.
- [ ] Add frontend build verification.
- [ ] Run `./gradlew :services:identity-service:test` only if backend identity DTO code changed.

**Done Criteria:**
- [ ] Admin can move from application review to the created merchant without manual ID copying.
- [ ] Broken onboarding links are visible.

---

### Task 8: Final Verification

**Files:** No code changes unless verification exposes bugs.

- [ ] Run `./gradlew :services:coupon-service:test`.
- [ ] Run `./gradlew :services:identity-service:test`.
- [ ] Run `npm run build` from `frontend/admin-app`.
- [ ] Manually verify admin flow: open partner applications, open approved merchant, edit merchant, edit locations, see readiness, block unsafe deactivation, create coupon with merchant preselected.
- [ ] Verify existing coupon create page still loads merchant selector from `GET /api/v1/admin/merchants`.
- [ ] Verify public storefront coupon detail still reads merchant contact data from `primaryLocation`.

## Final Report Required From AI

- [ ] Explain what changed in backend business logic.
- [ ] Explain what changed in admin UI.
- [ ] List new/changed endpoints.
- [ ] List tests added or updated.
- [ ] Provide exact verification commands and results.
- [ ] Mention any deferred items, especially partner self-service cabinet, forced merchant deactivation, staff invitations, or analytics.

