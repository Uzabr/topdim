# Partner Coupon Request MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let partner owners request new coupon promotions from `frontend/partner`, while admins/moderators review, enrich, and convert those requests through the existing coupon moderation flow.

**Architecture:** Use `coupon-service` as the source of truth for coupon requests. Prefer reusing existing `CouponOffer` with status `LEAD` as the partner request object instead of creating a parallel request table. Partner input is lightweight; TopDim/admin finalizes copy, pricing, images, and publication readiness before sending the coupon to merchant approval or publishing.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Flyway, JUnit 5, Mockito, MockMvc, React 19, Vite, TypeScript, Ant Design, React Query, React Hook Form/Zod if used in current app, Axios.

---

## Required Skills

- [ ] Read `.agent/skills/using-superpowers` before starting.
- [ ] Use `superpowers:test-driven-development` for backend behavior changes.
- [ ] Use `superpowers:systematic-debugging` for failing tests, broken builds, API mismatches, or seed/demo issues.
- [ ] Use `superpowers:verification-before-completion` before reporting completion.
- [ ] Use `services/.agent/skills/api-design-principles` for all endpoint contracts.
- [ ] Use `services/.agent/skills/spring-boot-crud-patterns` for controller/service/repository structure.
- [ ] Use `services/.agent/skills/spring-boot-test-patterns` for service and controller tests.
- [ ] Use `services/.agent/skills/database-schema-designer` before adding/changing columns or migrations.
- [ ] Use `frontend/.agent/skills/react`, `frontend/.agent/skills/react-hook-form-zod`, and `frontend/.agent/skills/frontend-design` for partner/admin UI work.

## Current Context To Respect

- [ ] `frontend/partner` already exists and contains pages: `DashboardPage`, `CouponsPage`, `RedeemPage`, `StaffPage`, `LoginPage`.
- [ ] `coupon-service` already has `PartnerCouponController` at `/api/v1/partner/coupons`.
- [ ] `PartnerCouponService.createCouponOffer(...)` currently creates `CouponOffer` with status `LEAD`.
- [ ] `AdminCouponController` already supports admin coupon list, create, update, take-to-work, send-to-approval, archive.
- [ ] `media-service` already has `POST /api/v1/media/upload` returning a URL.
- [ ] Do not overwrite unrelated user/AI changes. Run `git status --short` before editing.

## Product Rules

- [ ] Partner does not publish coupons directly.
- [ ] Partner creates a promotion request; admin/moderator owns final packaging and quality control.
- [ ] Partner photo upload is optional. Lack of partner photos must not block request submission.
- [ ] Published/public-ready coupon must have a cover image.
- [ ] If partner does not provide good photos, admin can use TopDim category fallback cover.
- [ ] Fallback cover must be stored on `CouponOffer.coverImageUrl` before approval/publication, not only rendered as a frontend-only fallback.
- [ ] Partner cannot edit `ACTIVE`, `WAITING_FOR_MERCHANT`, `ARCHIVED`, or `SOLD_OUT` coupons.
- [ ] Partner can edit only requests/coupons that are still partner-editable: `LEAD`, `DRAFT`, or `REVISION_REQUESTED` according to existing business rules.
- [ ] Cashier users must not access coupon request creation or admin-style coupon management.

## Status Mapping

Use existing `CouponStatus` values for MVP:

- [ ] UI label `Новая заявка` maps to `LEAD`.
- [ ] UI label `В работе у TopDim` maps to `DRAFT`.
- [ ] UI label `Нужны уточнения` maps to `REVISION_REQUESTED`.
- [ ] UI label `На согласовании` maps to `WAITING_FOR_MERCHANT`.
- [ ] UI label `Опубликована` maps to `ACTIVE`.
- [ ] UI label `Отклонена/архив` maps to `ARCHIVED`.

Do not add a new request status enum unless existing `CouponStatus` cannot satisfy the flow.

## Image Strategy

- [ ] Partner form supports optional photo upload using existing `POST /api/v1/media/upload`.
- [ ] Partner can submit without photos.
- [ ] Partner can attach up to 5 images.
- [ ] First uploaded image can become suggested cover.
- [ ] Admin can replace cover and gallery images before approval.
- [ ] Add a category fallback cover map in backend or frontend-admin, but final selected fallback must be written into `coverImageUrl`.
- [ ] Do not generate AI images in this task.
- [ ] Do not require partner to upload professional photos.
- [ ] Clearly tell partner: `Если есть фото услуги, блюда, помещения или результата работы — добавьте. Если нет, TopDim поможет оформить акцию.`

---

### Task 0: Baseline And Scope Check

**Files:** No code changes.

- [ ] Run `git status --short`.
- [ ] Inspect existing partner coupon code:
  - `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java`
  - `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java`
  - `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java`
  - `frontend/partner/src/pages/CouponsPage.tsx`
  - `frontend/partner/src/api.ts`
- [ ] Run backend baseline:
  - `./gradlew :services:coupon-service:test`
- [ ] Run frontend baseline:
  - `npm run build` from `frontend/partner`
  - `npm run build` from `frontend/admin-app`

**Done Criteria:**
- [ ] Pre-existing failures are recorded before editing.
- [ ] No unrelated dirty files are overwritten.

---

### Task 1: Backend DTO And Validation For Partner Requests

**Files:**
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreatePartnerCouponRequest.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/dto/PartnerCouponRequestResponse.java` only if `CouponOfferResponse` is missing request-specific fields needed by partner UI.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOptionRepository.java` only if needed for options persistence.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponImageRepository.java` only if needed for images persistence.
- Add tests in `services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java`.
- Add controller tests in `services/coupon-service/src/test/java/uz/topdim/coupon/controller/PartnerCouponControllerTest.java`.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/api-design-principles/SKILL.md`.
- [ ] Keep endpoint `POST /api/v1/partner/coupons`, but treat it as `create promotion request` in names/messages/docs.
- [ ] Add request fields:
  - `title`
  - `categoryId`
  - `offerDescription`
  - `oldPrice`
  - `fromPrice`
  - `discountPercent`
  - `buyUntil`
  - `useUntil`
  - `giftAvailable`
  - `coverImageUrl` optional
  - `imageUrls` optional list, max 5
  - `options` list with `title`, `regularPrice`, `couponPrice`, `quantityLimit`
  - `partnerComment` optional if a suitable field exists; otherwise append safely into request notes/description only if product accepts it.
- [ ] Validate title, category, prices, discount, dates, and options.
- [ ] Reject `fromPrice >= oldPrice` unless existing business rules intentionally allow it.
- [ ] Reject `buyUntil` or `useUntil` in the past for partner-submitted active-intended requests.
- [ ] Validate `useUntil >= buyUntil`.
- [ ] Create `CouponOffer` with status `LEAD`, never `ACTIVE`.
- [ ] Persist `CouponOption` rows from partner request.
- [ ] Persist `CouponImage` rows from optional image URLs.
- [ ] If `coverImageUrl` is empty and `imageUrls` has values, use the first image as suggested `coverImageUrl`.
- [ ] If no images are supplied, leave `coverImageUrl` empty at request stage; admin must select final cover before approval.
- [ ] Ensure partner owner can create only for their own merchant resolved by `X-User-Id`.
- [ ] Ensure cashier context cannot create coupon requests. If backend has partner access context available, use it; otherwise rely on owner merchant resolution and document the limitation.
- [ ] Add tests:
  - owner creates `LEAD` request successfully;
  - request persists options;
  - request persists images;
  - first uploaded image becomes cover;
  - no image is allowed at request stage;
  - invalid price rejected;
  - invalid date rejected;
  - unknown category rejected;
  - user without merchant rejected;
  - partner cannot create request for another merchant.
- [ ] Run `./gradlew :services:coupon-service:test`.

**Done Criteria:**
- [ ] Partner request is a valid `CouponOffer` lead with enough data for admin review.
- [ ] Partner is not forced to upload photos.
- [ ] Request creation does not bypass merchant ownership.

---

### Task 2: Backend Admin Review Actions For Partner Requests

**Files:**
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/dto/RejectCouponRequest.java` if a dedicated rejection request DTO is cleaner than reusing archive.
- Create `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponCoverFallbackService.java`.
- Add tests in `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`.
- Add controller tests in `services/coupon-service/src/test/java/uz/topdim/coupon/controller/AdminCouponControllerTest.java`.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/spring-boot-crud-patterns/SKILL.md`.
- [ ] Reuse existing `GET /api/v1/admin/coupons?status=LEAD` for request queue if it already returns enough data.
- [ ] Add or improve admin response so request list shows merchant, category, status, createdAt, cover readiness, and assigned moderator.
- [ ] Keep `PATCH /api/v1/admin/coupons/{id}/take-to-work` as `LEAD -> DRAFT`.
- [ ] Add a clear reject action if not present: `POST /api/v1/admin/coupons/{id}/reject-request`, mapping request to `ARCHIVED` with reason.
- [ ] Before `send-to-approval`, ensure coupon has final `coverImageUrl`.
- [ ] If no cover exists, set category fallback cover using `CouponCoverFallbackService`.
- [ ] Fallback cover mapping must cover all demo categories from `docs/dev-demo-seed.md`.
- [ ] Do not allow `send-to-approval` when merchant lacks publication-ready primary location.
- [ ] Do not allow direct `ACTIVE` from partner request.
- [ ] Add tests:
  - admin lists `LEAD` requests;
  - moderator takes request to work;
  - admin rejects request with reason;
  - send-to-approval fills fallback cover when no partner photo exists;
  - send-to-approval keeps partner photo if present;
  - send-to-approval fails for merchant without publication readiness;
  - cashier/partner cannot call admin request actions.
- [ ] Run `./gradlew :services:coupon-service:test`.

**Done Criteria:**
- [ ] Admin/moderator can process partner requests without creating a parallel workflow.
- [ ] Every coupon sent onward has a cover image.
- [ ] Partner request rejection is explicit and auditable through status/reason.

---

### Task 3: Partner Frontend Request Form And List

**Files:**
- Create `frontend/partner/src/pages/CouponRequestFormPage.tsx`.
- Create `frontend/partner/src/pages/CouponRequestsPage.tsx` or extend `frontend/partner/src/pages/CouponsPage.tsx` if current structure is simpler.
- Modify `frontend/partner/src/App.tsx`.
- Modify `frontend/partner/src/layouts/PartnerLayout.tsx`.
- Modify `frontend/partner/src/api.ts`.

**Implementation Steps:**
- [ ] Use `frontend/.agent/skills/react/SKILL.md`.
- [ ] Use `frontend/.agent/skills/react-hook-form-zod/SKILL.md`.
- [ ] Use `frontend/.agent/skills/frontend-design/SKILL.md`.
- [ ] Add partner navigation item: `Заявки на акции` or `Создать акцию`.
- [ ] Route owner users to request list/form; cashier users must not see these routes.
- [ ] Build form fields:
  - title;
  - category;
  - offerDescription;
  - oldPrice;
  - fromPrice;
  - discountPercent;
  - buyUntil;
  - useUntil;
  - options with at least one option;
  - optional photos, max 5;
  - partnerComment/help text.
- [ ] Add photo helper copy:
  - `Фото необязательно. Если есть фото услуги, блюда, помещения или результата работы — добавьте. Если нет, TopDim поможет оформить акцию.`
- [ ] Implement optional upload via `POST /api/v1/media/upload`.
- [ ] If upload fails, show a clear error and allow submit without photos.
- [ ] Show request list with statuses using Russian labels from this plan.
- [ ] Show admin/revision comment when status is `REVISION_REQUESTED` or `ARCHIVED`.
- [ ] Allow editing only for `LEAD`, `DRAFT`, and `REVISION_REQUESTED`; hide edit for `WAITING_FOR_MERCHANT`, `ACTIVE`, `ARCHIVED`, `SOLD_OUT`.
- [ ] Run `npm run build` from `frontend/partner`.

**Done Criteria:**
- [ ] Owner can create a coupon request without photos.
- [ ] Owner can optionally upload photos.
- [ ] Owner can see request status and comments.
- [ ] Cashier cannot access request creation.

---

### Task 4: Admin Frontend Request Queue

**Files:**
- Create `frontend/admin-app/src/features/coupon-requests/CouponRequestsPage.tsx`.
- Create `frontend/admin-app/src/features/coupon-requests/api.ts`.
- Create `frontend/admin-app/src/features/coupon-requests/types.ts`.
- Modify `frontend/admin-app/src/App.tsx`.
- Modify `frontend/admin-app/src/components/layout/AdminLayout.tsx`.
- Optionally reuse `frontend/admin-app/src/features/coupons/CouponFormPage.tsx` for final editing.

**Implementation Steps:**
- [ ] Use `frontend/.agent/skills/react/SKILL.md`.
- [ ] Use `frontend/.agent/skills/frontend-design/SKILL.md`.
- [ ] Add admin menu item under coupon moderation: `Заявки на акции`.
- [ ] Build request table with columns:
  - id;
  - title;
  - merchant;
  - category;
  - status;
  - cover/image readiness;
  - assigned moderator;
  - createdAt;
  - actions.
- [ ] Add filters by status and merchant/search if existing API supports it.
- [ ] Actions:
  - view details;
  - take to work;
  - edit/finalize coupon;
  - reject with reason;
  - send to merchant approval when ready.
- [ ] In details drawer/page, show partner-submitted images and fallback cover suggestion.
- [ ] Admin must be able to select fallback category cover when partner provided no photo.
- [ ] Do not publish directly from this page unless existing coupon flow already supports it safely.
- [ ] Run `npm run build` from `frontend/admin-app`.

**Done Criteria:**
- [ ] Admin/moderator has a clear queue for partner coupon requests.
- [ ] Admin can finalize content and image before moving request forward.
- [ ] Request processing does not require jumping blindly through the generic coupon list.

---

### Task 5: Public/Storefront Image Safety

**Files:**
- Modify `frontend/web-app/src/components/coupon/CouponCard.tsx` only if it currently breaks on missing cover.
- Modify `frontend/web-app/src/pages/CouponDetailPage.tsx` only if it currently breaks on missing images.
- Modify related CSS only if needed.

**Implementation Steps:**
- [ ] Use `frontend/.agent/skills/react/SKILL.md`.
- [ ] Verify public catalog handles coupon with missing `coverImageUrl` gracefully before admin finalization.
- [ ] Public pages should not show `LEAD` or `DRAFT` coupons.
- [ ] `ACTIVE` coupons should have cover images because admin flow enforces this.
- [ ] If defensive UI fallback is needed, use a neutral TopDim placeholder, not fake merchant-specific photos.
- [ ] Run `npm run build` from `frontend/web-app`.

**Done Criteria:**
- [ ] Missing partner photos do not break public UI.
- [ ] Public users do not see unfinished partner requests.

---

### Task 6: Documentation And Manual QA Checklist

**Files:**
- Create or update `docs/partner-coupon-request-mvp.md`.
- Update `docs/API_CONTRACT.md` only if new endpoints or DTO contracts are added.
- Update `docs/COUPON_FLOW.md` if status flow wording changes.

**Implementation Steps:**
- [ ] Document partner request flow:
  - partner creates request;
  - photos optional;
  - TopDim/admin finalizes cover and copy;
  - admin sends to merchant approval/publication flow;
  - partner tracks status.
- [ ] Document status labels and backend status mapping.
- [ ] Document image rules:
  - optional partner upload;
  - max 5 images;
  - category fallback cover;
  - final cover required before approval/publication.
- [ ] Add manual QA scenario using demo accounts:
  - owner creates request without photos;
  - owner creates request with photos;
  - admin processes no-photo request and fallback cover is applied;
  - admin processes photo request and keeps partner cover;
  - cashier cannot create request;
  - public catalog does not show unfinished request.

**Done Criteria:**
- [ ] Future AI/developer can understand the flow without reading this implementation plan.
- [ ] Manual tester has a concrete checklist.

---

### Task 7: Final Verification

**Files:** No code changes unless verification exposes bugs.

- [ ] Run `./gradlew :services:coupon-service:test`.
- [ ] Run `npm run build` from `frontend/partner`.
- [ ] Run `npm run build` from `frontend/admin-app`.
- [ ] Run `npm run build` from `frontend/web-app`.
- [ ] Manual flow: owner creates request without photos.
- [ ] Manual flow: owner creates request with uploaded photos.
- [ ] Manual flow: admin sees request queue.
- [ ] Manual flow: admin takes request to work.
- [ ] Manual flow: admin rejects request with reason and owner sees reason.
- [ ] Manual flow: admin applies category fallback cover and sends request onward.
- [ ] Manual flow: cashier cannot access request form.
- [ ] Manual flow: unfinished request never appears in public catalog.

## Final Report Required From AI

- [ ] List backend files changed.
- [ ] List frontend partner pages/routes changed.
- [ ] List frontend admin pages/routes changed.
- [ ] List endpoint contracts added/changed.
- [ ] List tests added/updated.
- [ ] List image/fallback behavior implemented.
- [ ] Provide exact verification commands and results.
- [ ] Mention deferred items: AI image generation, full merchant self-publication, advanced media moderation, branch-specific coupon availability, and direct partner editing of active coupons.
