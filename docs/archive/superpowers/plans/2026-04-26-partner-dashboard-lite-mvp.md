# Partner Dashboard Lite MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first safe partner cabinet MVP with three conceptual zones `Owner`, `Manager`, and `Cashier`, while implementing only the Owner dashboard and Cashier redemption page in this iteration.

**Architecture:** Keep existing platform roles and domain names stable: global auth role remains `PARTNER`, merchant branch remains `MerchantLocation`, and purchased coupon status remains `USED`. Add a partner-access layer that maps a logged-in partner user to an internal partner cabinet role (`OWNER`, `MANAGER`, `CASHIER`) and, for cashiers, to a fixed merchant location. Build the partner cabinet as a dedicated frontend app in `frontend/partner`; `frontend/admin-app` remains only for TopDim internal admins, moderators, and super admins.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Flyway, OpenFeign, JUnit 5, Mockito, MockMvc, React 19, TypeScript, React Query, React Hook Form or Ant Design Form, Zod, Ant Design 6, Vite.

---

## Required Skills

- [ ] Before starting, read all project root workflow skills in `.agent/skills/`, especially `using-superpowers`, `executing-plans`, `test-driven-development`, `systematic-debugging`, `verification-before-completion`, and `requesting-code-review`.
- [ ] Use `superpowers:test-driven-development` for every backend behavior change.
- [ ] Use `superpowers:systematic-debugging` for every unexpected failing test, API mismatch, or build issue.
- [ ] Use `superpowers:verification-before-completion` before reporting completion.
- [ ] Use all relevant service skills in `services/.agent/skills/`: `api-design-principles`, `spring-boot-crud-patterns`, `spring-boot-test-patterns`, `database-schema-designer`, `sql-optimization-patterns`, and `e2e-testing-patterns`.
- [ ] Use all frontend skills in `frontend/.agent/skills/`: `react`, `react-hook-form-zod`, and `frontend-design`.
- [ ] Check infrastructure skills only if deployment, security gateway, or runtime configuration is changed; otherwise do not touch infrastructure.

## Coordination Rules

- [ ] Start this plan only after `purchase-lifecycle-reconciliation` and `admin-merchant-operations-mvp` are complete, or use a separate branch/worktree.
- [ ] Run `git status --short` before editing and do not overwrite unrelated user/AI changes.
- [ ] Do not rename existing backend domain concepts just to match the document wording.
- [ ] Do not implement full enterprise merchant cabinet from `docs/topdim_merchant_dashboard_structure.md`.
- [ ] Create partner-facing screens only in `frontend/partner`; do not add new partner dashboard or cashier redemption routes to `frontend/admin-app`.
- [ ] If `frontend/admin-app` already contains old partner routes, remove or block them after `frontend/partner` is working so partners do not use the admin interface.

## Naming Rules

- [ ] Keep backend global role as `PARTNER`; do not rename it to `OWNER`.
- [ ] Add internal cabinet role values `OWNER`, `MANAGER`, `CASHIER` where needed.
- [ ] Keep entity `MerchantLocation`; show it as `Филиал` in UI.
- [ ] Keep purchased coupon status `USED`; show it as `Погашен` in UI.
- [ ] In `frontend/partner`, use partner-friendly routes such as `/dashboard` and `/redeem`.
- [ ] Do not keep partner cabinet functionality inside `frontend/admin-app`; admin app must not be the long-term partner entry point.

## MVP Scope

- [ ] Owner dashboard: KPI cards, coupon overview, redemption overview, branch breakdown, limited staff/cashier block.
- [ ] Cashier page: PIN and QR redemption, no branch selector, limited own history.
- [ ] Dedicated partner frontend app: create `frontend/partner` for partner login, owner dashboard, and cashier redemption.
- [ ] Partner access context: resolve whether current `PARTNER` user is Owner, Manager, or Cashier.
- [ ] Cashier branch binding: cashier must be tied to one `MerchantLocation`.
- [ ] Cashier login: every cashier must have a separate login user account with their own credentials.
- [ ] Redemption branch recording: redemption stores `merchantLocationId` and staff identity.
- [ ] Manager is only modeled for future access separation; no separate manager dashboard is required in this MVP.

## Out Of Scope

- [ ] Do not implement Accountant.
- [ ] Do not implement Branch Manager.
- [ ] Do not implement payouts, commissions, acts, invoices, or export.
- [ ] Do not implement full review/reputation management.
- [ ] Do not implement advanced analytics such as conversion funnel, cohorts, repeat customers, or cashier bonuses.
- [ ] Do not allow partners to directly edit `ACTIVE` coupons.
- [ ] Do not allow cashiers to select a branch manually during redemption.

## Access Model

- [ ] Owner: existing merchant owner user where `Merchant.userId == currentUserId`.
- [ ] Manager: future staff-backed partner user with internal role `MANAGER`; can view operational dashboard later.
- [ ] Cashier: staff-backed partner login user with global auth role `PARTNER` and internal role `CASHIER`; must have `merchantId`, `merchantLocationId`, and separate login credentials.
- [ ] Cashier cannot access owner dashboard, coupons overview, merchant settings, revenue cards, staff management, or all-branch analytics.
- [ ] Cashier can redeem by PIN/QR and see only their own limited redemption history.

---

### Task 0: Baseline Verification

**Files:** No code changes.

- [ ] Run `git status --short`.
- [ ] Run `./gradlew :services:identity-service:test`.
- [ ] Run `./gradlew :services:coupon-service:test`.
- [ ] Run `./gradlew :services:order-service:test`.
- [ ] Run `npm run build` from `frontend/admin-app` to record the current internal admin app baseline.
- [ ] Do not expect `frontend/partner` to build before Task 7 if the app does not exist yet.
- [ ] Record any pre-existing failures before editing.

**Done Criteria:**
- [ ] Baseline is known.
- [ ] Current admin app and admin merchant operations are not broken before starting.

---

### Task 1: Add Partner Cabinet Access Context In Identity Service

**Files:**
- Modify `services/identity-service/src/main/java/uz/topdim/identity/entity/Staff.java`.
- Modify `services/identity-service/src/main/java/uz/topdim/identity/dto/CreateStaffRequest.java`.
- Modify `services/identity-service/src/main/java/uz/topdim/identity/dto/PartnerStaffResponse.java`.
- Create `services/identity-service/src/main/java/uz/topdim/identity/dto/PartnerAccessContextResponse.java`.
- Create `services/identity-service/src/main/java/uz/topdim/identity/controller/InternalPartnerAccessController.java`.
- Modify `services/identity-service/src/main/java/uz/topdim/identity/service/PartnerStaffService.java`.
- Modify `services/identity-service/src/main/java/uz/topdim/identity/repository/StaffRepository.java`.
- Modify `services/identity-service/src/main/java/uz/topdim/identity/client/CouponMerchantClient.java`.
- Create Flyway migration in `services/identity-service/src/main/resources/db/migration/`.
- Add or modify tests in `services/identity-service/src/test/java/uz/topdim/identity/service/PartnerStaffServiceTest.java`.
- Add controller tests for internal partner access endpoint.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/database-schema-designer/SKILL.md` before changing the `staff` schema.
- [ ] Keep existing `staff.user_id` as owner partner user id for backward compatibility.
- [ ] Add fields to `Staff`: `loginUserId`, `merchantId`, `merchantLocationId`, `role`, `active`.
- [ ] Keep `role` string-compatible for now with allowed values `MANAGER` and `CASHIER`.
- [ ] Extend `CreateStaffRequest` with `loginEmail`, `temporaryPassword`, `merchantLocationId`, and `role`.
- [ ] When owner creates a cashier, create a separate identity user for the cashier using `loginEmail` and `temporaryPassword`.
- [ ] The cashier identity user must have global auth role `PARTNER`; partner cabinet permissions come from staff access context, not from a new global role.
- [ ] Store the created cashier identity user id in `Staff.loginUserId`.
- [ ] Do not allow two active staff records to share the same `loginUserId`.
- [ ] Do not store or return plaintext password after creation; only return/show the temporary password once in the create response if the current identity service pattern allows it.
- [ ] If the project already has password-reset or invitation mechanics, prefer generating a one-time temporary password or invitation token instead of accepting a reusable plaintext password from frontend.
- [ ] For MVP, require `merchantLocationId` when `role == CASHIER`.
- [ ] Add repository methods `findByLoginUserId(Long loginUserId)`, `findByUserIdAndActiveTrue(Long ownerUserId)`, and `existsByLoginUserId(Long loginUserId)`.
- [ ] Add internal endpoint `GET /api/v1/internal/partner-access/{userId}`.
- [ ] For owner user, return `role=OWNER`, `merchantId` from coupon-service merchant context, `merchantLocationId=null`, `canViewDashboard=true`, `canRedeem=true`.
- [ ] For staff login user, return staff role, `merchantId`, `merchantLocationId`, `staffId`, `staffName`, `canViewDashboard=false` for `CASHIER`, `canRedeem=true`.
- [ ] If staff is inactive, return 409/403 according to existing exception style.
- [ ] Add tests: owner context, cashier context with location, inactive cashier rejected, cashier without location rejected, cashier login user created with role `PARTNER`, duplicate `loginUserId` rejected.
- [ ] Run `./gradlew :services:identity-service:test`.

**Done Criteria:**
- [ ] Backend can tell whether current partner user is Owner, Manager, or Cashier.
- [ ] Cashier context always contains fixed `merchantLocationId`.
- [ ] Cashier has a separate login/password and does not use owner credentials.
- [ ] No request body can spoof cashier location.

---

### Task 2: Validate Merchant Locations For Staff Creation

**Files:**
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/controller/InternalMerchantController.java`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`.
- Create or update DTOs in `services/coupon-service/src/main/java/uz/topdim/coupon/dto/`.
- Modify `services/identity-service/src/main/java/uz/topdim/identity/client/CouponMerchantClient.java`.
- Modify `services/identity-service/src/main/java/uz/topdim/identity/service/PartnerStaffService.java`.
- Add tests in coupon-service and identity-service.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/api-design-principles/SKILL.md`.
- [ ] Add internal coupon-service endpoint to return active locations for merchant owner: `GET /api/v1/internal/merchants/by-user/{userId}/locations`.
- [ ] Location response must include `id`, `title`, `address`, `phone`, `workingHours`, `primary`, and `active`.
- [ ] In identity-service staff creation, verify selected `merchantLocationId` belongs to the owner user's merchant and is active.
- [ ] If merchant has exactly one active location and request role is `CASHIER` with missing `merchantLocationId`, default to the only active location.
- [ ] If merchant has multiple active locations and missing `merchantLocationId`, reject with message `Выберите филиал для кассира`.
- [ ] Add tests for default single location, reject multiple locations without selection, reject foreign/inactive location.
- [ ] Run `./gradlew :services:coupon-service:test`.
- [ ] Run `./gradlew :services:identity-service:test`.

**Done Criteria:**
- [ ] Owner cannot create a cashier attached to another merchant's branch.
- [ ] Cashier branch is chosen once during staff creation, not during redemption.

---

### Task 3: Add Partner Access Resolver To Order Service

**Files:**
- Create `services/order-service/src/main/java/uz/topdim/order/client/IdentityPartnerAccessClient.java`.
- Create `services/order-service/src/main/java/uz/topdim/order/client/PartnerAccessContext.java`.
- Create `services/order-service/src/main/java/uz/topdim/order/service/PartnerAccessResolver.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/service/PartnerService.java`.
- Modify tests in `services/order-service/src/test/java/uz/topdim/order/service/`.
- Modify tests in `services/order-service/src/test/java/uz/topdim/order/controller/`.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/api-design-principles/SKILL.md`.
- [ ] Add Feign client to identity-service internal access endpoint.
- [ ] `PartnerAccessResolver.resolve(userId)` must return `role`, `merchantId`, optional `merchantLocationId`, optional `staffId`, optional `staffName`.
- [ ] Replace partner dashboard/redemption access paths that only resolve merchant with the richer access context.
- [ ] Owner and Manager may call dashboard endpoints.
- [ ] Cashier may call redemption endpoints only.
- [ ] If `role=CASHIER` and `merchantLocationId` is null, redemption must fail before reading coupon.
- [ ] Add tests for owner allowed dashboard, cashier forbidden dashboard, cashier allowed redemption, missing location rejected.
- [ ] Run `./gradlew :services:order-service:test`.

**Done Criteria:**
- [ ] Order-service no longer relies on user-provided staff name/location for cashier operations.
- [ ] Cashier cannot access owner dashboard data.

---

### Task 4: Store Branch And Staff Identity On Redemption

**Files:**
- Modify `services/order-service/src/main/java/uz/topdim/order/entity/Redemption.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/dto/RedemptionResponse.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/service/PartnerService.java`.
- Create Flyway migration in `services/order-service/src/main/resources/db/migration/`.
- Modify `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`.
- Modify `services/order-service/src/test/java/uz/topdim/order/service/PartnerServiceTest.java`.

**Implementation Steps:**
- [ ] Use `services/.agent/skills/database-schema-designer/SKILL.md`.
- [ ] Add redemption columns `merchant_location_id`, `staff_id`, `redeem_method`.
- [ ] `redeem_method` values should be `PIN` or `QR`.
- [ ] Change redemption processing to accept partner access context and method.
- [ ] Store `merchantLocationId` from access context, not request body.
- [ ] Store `staffId` and `staffName` from access context for cashier users.
- [ ] Preserve legacy `staffName` only for owner/manager manual redemption if needed.
- [ ] Add repository queries for merchant all-branch history and cashier own history.
- [ ] Add tests: PIN redemption stores location/staff, QR redemption stores method QR, cashier history only own records, owner history all merchant records.
- [ ] Run `./gradlew :services:order-service:test`.

**Done Criteria:**
- [ ] Every cashier redemption is tied to the cashier's fixed branch.
- [ ] Cashier cannot fake branch or staff identity through request payload.

---

### Task 5: Add Partner Dashboard API

**Files:**
- Create `services/order-service/src/main/java/uz/topdim/order/dto/PartnerDashboardResponse.java`.
- Create optional DTOs `PartnerBranchStatsResponse`, `PartnerCouponStatsResponse`, `PartnerRecentRedemptionResponse`.
- Modify `services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/service/PartnerService.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/repository/PurchasedCouponRepository.java`.
- Modify `services/order-service/src/main/java/uz/topdim/order/repository/RedemptionRepository.java`.
- Add tests in `services/order-service/src/test/java/uz/topdim/order/service/PartnerServiceTest.java`.
- Add controller tests in `services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java`.

**Implementation Steps:**
- [ ] Add `GET /api/v1/partner/dashboard`.
- [ ] Owner/Manager only; Cashier receives forbidden/business error.
- [ ] Response must include `totalCoupons`, `activeCoupons`, `totalSold`, `totalRedeemed`, `pendingRedemption`, `expired`, `totalRevenue`.
- [ ] Add branch breakdown by `merchantLocationId`: sold, redeemed, pending, revenue where data is available.
- [ ] Add recent redemptions with coupon title, code, branch/location id, staff name, redeemedAt.
- [ ] Do not include commission, payout, bank details, or customer personal data in MVP.
- [ ] Add optional query params `from`, `to`, `merchantLocationId`.
- [ ] If no dates are provided, default to last 30 days for time-based charts and all-time for headline counters if current project style supports it; otherwise document exact behavior in response tests.
- [ ] Add tests for owner dashboard success, cashier forbidden, branch filter, empty state.
- [ ] Run `./gradlew :services:order-service:test`.

**Done Criteria:**
- [ ] Owner has useful business overview without finance/payout complexity.
- [ ] Cashier cannot see owner dashboard.

---

### Task 6: Add Partner Merchant Profile Read API

**Files:**
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java` or create `PartnerMerchantController`.
- Modify `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`.
- Add tests in coupon-service controller/service tests.

**Implementation Steps:**
- [ ] Add `GET /api/v1/partner/merchant`.
- [ ] Resolve merchant by `X-User-Id` owner relation for MVP.
- [ ] Return merchant name, description, logo, cover, primaryLocation, all active locations, active status, and publication readiness.
- [ ] Do not allow partner profile edit in this MVP.
- [ ] Add tests for success, no merchant, inactive merchant.
- [ ] Run `./gradlew :services:coupon-service:test`.

**Done Criteria:**
- [ ] Owner can see how TopDim displays their business and branches.
- [ ] Merchant profile remains controlled by admin until later stage.

---

### Task 7: Create Dedicated Partner Frontend App And Owner Dashboard

**Files:**
- Create `frontend/partner/package.json`.
- Create `frontend/partner/index.html`.
- Create `frontend/partner/tsconfig.json`.
- Create `frontend/partner/tsconfig.app.json`.
- Create `frontend/partner/tsconfig.node.json`.
- Create `frontend/partner/vite.config.ts`.
- Create `frontend/partner/eslint.config.js`.
- Create `frontend/partner/src/main.tsx`.
- Create `frontend/partner/src/App.tsx`.
- Create `frontend/partner/src/api/client.ts`.
- Create `frontend/partner/src/store/authStore.ts`.
- Create `frontend/partner/src/routes/ProtectedRoute.tsx`.
- Create `frontend/partner/src/types/index.ts`.
- Create `frontend/partner/src/components/layout/PartnerLayout.tsx`.
- Create `frontend/partner/src/features/auth/LoginPage.tsx` or reuse the admin-app login implementation by copying it into this app.
- Create `frontend/partner/src/features/auth/ForbiddenPage.tsx`.
- Create `frontend/partner/src/features/dashboard/PartnerDashboardPage.tsx`.
- Create `frontend/partner/src/features/dashboard/api.ts`.
- Create `frontend/partner/src/features/dashboard/types.ts`.
- Create `frontend/partner/src/features/dashboard/PartnerDashboardPage.css`.
- Do not modify `frontend/admin-app` in this task except for reading existing patterns.

**Implementation Steps:**
- [ ] Use `frontend/.agent/skills/react/SKILL.md`.
- [ ] Use `frontend/.agent/skills/frontend-design/SKILL.md`.
- [ ] Create `frontend/partner` as a standalone Vite React app using the same dependency style as `frontend/admin-app`.
- [ ] Set `package.json` name to `partner-app`, scripts to `dev`, `build`, `lint`, and `preview`.
- [ ] Copy the auth token pattern from `frontend/admin-app/src/api/client.ts`, but use a separate persisted storage key such as `topdim-partner-auth`.
- [ ] Add route `/login` for partner login.
- [ ] Add route `/dashboard` for `PARTNER` Owner/Manager users.
- [ ] Add route `/redeem` for Cashier and Owner/Manager redemption access.
- [ ] Add root redirect: Owner/Manager goes to `/dashboard`, Cashier goes to `/redeem`.
- [ ] Add partner access query on app load or dashboard load.
- [ ] If access role is `CASHIER`, redirect from `/dashboard` to `/redeem`.
- [ ] Dashboard must show KPI cards: active coupons, sold, redeemed, pending redemption, total revenue.
- [ ] Add compact table for recent redemptions.
- [ ] Add compact table or cards for coupon overview using existing `GET /api/v1/partner/coupons`.
- [ ] Add branch breakdown using dashboard response; show `MerchantLocation` as `Филиал`.
- [ ] Add read-only merchant profile block with locations from `GET /api/v1/partner/merchant`.
- [ ] Do not show payout, commission, bank details, or full customer data.
- [ ] Run `npm install` from `frontend/partner` if dependencies are not installed for the new app.
- [ ] Run `npm run build` from `frontend/partner`.

**Done Criteria:**
- [ ] Owner lands on useful partner dashboard in `frontend/partner`, not in `frontend/admin-app`.
- [ ] Cashier cannot open owner dashboard UI.
- [ ] Partner auth storage is isolated from admin auth storage.

---

### Task 8: Build Cashier Redeem Frontend In Partner App

**Files:**
- Create `frontend/partner/src/features/redeem/PartnerRedeemPage.tsx`.
- Create `frontend/partner/src/features/redeem/api.ts`.
- Create `frontend/partner/src/features/redeem/types.ts`.
- Create `frontend/partner/src/features/redeem/PartnerRedeemPage.css`.
- Modify `frontend/partner/src/App.tsx`.
- Modify `frontend/partner/src/components/layout/PartnerLayout.tsx`.
- Read existing `frontend/admin-app/src/features/partner-redemptions/PartnerRedeemPage.tsx` only as migration reference.

**Implementation Steps:**
- [ ] Use `frontend/.agent/skills/react-hook-form-zod/SKILL.md`.
- [ ] Use `frontend/.agent/skills/frontend-design/SKILL.md`.
- [ ] Implement the final redemption UI in `frontend/partner`; do not keep it as an admin-app page.
- [ ] Remove manual staff name input for cashier context, or make it visible only for owner/manager legacy redeem if product still wants it.
- [ ] Do not add a branch selector.
- [ ] Show current branch name/location from partner access context.
- [ ] Keep PIN redemption.
- [ ] Keep QR redemption endpoint support; if camera scanning is not implemented yet, add a safe text/scan-result field and label it as QR token input.
- [ ] Cashier history table must show only limited fields: coupon, status/result, time, method.
- [ ] Owner/Manager history can show all merchant redemptions if the page is opened by them.
- [ ] If backend returns missing branch binding, show clear error: `Кассир не привязан к филиалу. Обратитесь к владельцу бизнеса.`
- [ ] Run `npm run build` from `frontend/partner`.

**Done Criteria:**
- [ ] Cashier can redeem quickly by PIN/QR.
- [ ] Cashier cannot choose or spoof branch.
- [ ] Cashier does not see revenue or owner analytics.

---

### Task 9: Minimal Owner Cashier Management UI

**Files:**
- Create or extend `frontend/partner/src/features/dashboard/PartnerStaffBlock.tsx`.
- Modify `frontend/partner/src/features/dashboard/api.ts`.
- Modify `services/identity-service/src/main/java/uz/topdim/identity/controller/PartnerStaffController.java` only if API contract needs response changes.
- Modify identity-service tests if backend changed.

**Implementation Steps:**
- [ ] In owner dashboard, add small `Кассиры` block, not a full Employees module.
- [ ] Owner can see cashier list: name, phone, role, branch/location, active.
- [ ] Owner can add cashier with name, phone, login email, generated or entered temporary password, and branch.
- [ ] Prefer generated temporary password in UI: owner clicks `Создать кассира`, system shows `Логин` and `Временный пароль` once after successful creation.
- [ ] UI must warn owner to share the temporary password safely and that it will not be shown again.
- [ ] Owner can deactivate/remove cashier using existing or updated endpoint.
- [ ] Manager creation can be supported in backend but does not need full UI in this MVP.
- [ ] If there are multiple branches, branch selection is required when creating cashier.
- [ ] Run `./gradlew :services:identity-service:test` if backend changed.
- [ ] Run `npm run build` from `frontend/partner`.

**Done Criteria:**
- [ ] Owner can create a cashier attached to a branch.
- [ ] Cashier receives their own login and temporary password, then can log in and redeem without selecting branch.

---

### Task 10: Remove Partner Cabinet From Admin App

**Files:**
- Modify `frontend/admin-app/src/App.tsx`.
- Modify `frontend/admin-app/src/components/layout/AdminLayout.tsx`.
- Modify `frontend/admin-app/src/types/index.ts` only if the admin app no longer needs to compile `PARTNER` as an allowed route role.
- Leave `frontend/admin-app/src/features/partner-redemptions/*` in place only if deletion is intentionally deferred; otherwise remove the unused feature after verifying no imports remain.

**Implementation Steps:**
- [ ] Use `frontend/.agent/skills/react/SKILL.md`.
- [ ] In `frontend/admin-app/src/App.tsx`, remove `PartnerRedeemPage` import.
- [ ] In `frontend/admin-app/src/App.tsx`, remove the `ProtectedRoute allowedRoles={['PARTNER']}` block.
- [ ] In `HomeRedirect`, stop redirecting `PARTNER` to `/partner/redeem`; if a partner accidentally opens admin app, redirect to `/403` or `/login` according to existing auth behavior.
- [ ] In `frontend/admin-app/src/components/layout/AdminLayout.tsx`, remove the `/partner` menu group and all `PARTNER`-only menu items.
- [ ] Keep admin routes for partner applications and merchant operations because those are internal admin workflows.
- [ ] Run `npm run build` from `frontend/admin-app`.
- [ ] Run `npm run build` from `frontend/partner`.

**Done Criteria:**
- [ ] `frontend/admin-app` no longer exposes partner dashboard or redemption UI.
- [ ] Partner users have a clear dedicated app path through `frontend/partner`.
- [ ] Admin app still supports internal partner applications and merchant moderation flows.

---

### Task 11: Final Verification

**Files:** No code changes unless verification exposes bugs.

- [ ] Run `./gradlew :services:identity-service:test`.
- [ ] Run `./gradlew :services:coupon-service:test`.
- [ ] Run `./gradlew :services:order-service:test`.
- [ ] Run `npm run build` from `frontend/admin-app`.
- [ ] Run `npm run build` from `frontend/partner`.
- [ ] Manual flow: Owner logs into `frontend/partner`, sees `/dashboard`.
- [ ] Manual flow: Owner creates cashier and assigns a branch.
- [ ] Manual flow: Cashier logs into `frontend/partner`, lands on `/redeem`.
- [ ] Manual flow: Cashier redeems by PIN; redemption stores staff and branch.
- [ ] Manual flow: Cashier redeems by QR token; redemption stores method `QR`.
- [ ] Manual flow: Cashier cannot open `/dashboard`.
- [ ] Manual flow: Owner sees redemption in dashboard under correct branch.
- [ ] Manual flow: No screen allows cashier to choose branch manually.
- [ ] Manual flow: Partner user cannot use `frontend/admin-app` as a cabinet.

## Final Report Required From AI

- [ ] List backend access model changes.
- [ ] List frontend pages/routes added or changed in `frontend/partner`.
- [ ] List admin-app partner route cleanup done in `frontend/admin-app`.
- [ ] List new/changed endpoints.
- [ ] List migrations added.
- [ ] List tests added/updated.
- [ ] Provide exact verification commands and results.
- [ ] Mention deferred items: full Manager dashboard, Branch Manager, Accountant, payouts, reviews, advanced analytics, and audit-log UI.
