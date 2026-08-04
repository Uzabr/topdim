# Admin Completeness Implementation Plan

**Goal:** Finish the remaining operational gaps in `frontend/admin-app` without
reintroducing partner-only functionality, then prepare a separate demo-data E2E
test plan after implementation is complete.

**Delivery:** Continue on `admin/codex`. Every task ends with focused automated
verification, one task-scoped commit, and `git push origin admin/codex`. The full
manual demo-data walkthrough is intentionally deferred to the final task.

## Product boundaries

- `admin-app` remains for `MODERATOR`, `ADMIN`, and `SUPER_ADMIN` only.
- Partner redemption remains in `frontend/partner`; the orphaned admin copy is
  removed.
- Dashboard values must come from canonical services. An API failure is shown as
  unavailable data, never as a fake zero.
- Moderators must not receive order, revenue, user-management, or audit data.
- Category deletion is allowed only when no coupon references the category;
  otherwise the API returns a conflict and the category can be deactivated.
- Existing public category reads remain backward compatible and return only
  active categories.
- Automated regression tests accompany behavior changes. The large manual E2E
  matrix and demo-data run are performed only after all tasks below are complete.

## Task 1: Real operational dashboard

### Backend

- Add `GET /api/v1/admin/dashboard` to `order-service`, restricted to
  `ADMIN`/`SUPER_ADMIN`.
- Return `ordersToday`, `paidRevenueToday`, `pendingComplaints`, a deterministic
  seven-day paid-sales series, and five recent orders.
- Count revenue by `paidAt` and include paid operational statuses only; exclude
  `PENDING`, `CANCELLED`, and `REFUNDED`.
- Use an injected `Clock` so day boundaries are deterministic in tests.
- Add repository/service/controller tests for empty data, day boundaries,
  excluded statuses, seven-day zero filling, authorization, and recent-order
  ordering.

### Frontend

- Replace all hard-coded dashboard values and placeholder panels.
- `ADMIN`/`SUPER_ADMIN`: combine the order dashboard response with canonical
  coupon and identity list totals for active coupons and users.
- `MODERATOR`: show actionable coupon queue totals and pending complaints only;
  do not call privileged order/user endpoints.
- Render a compact seven-day bar chart without adding a chart dependency and a
  recent-orders list with explicit loading, empty, partial-error, and retry
  states.
- Add component/API tests for both role variants and partial backend failure.

## Task 2: Complete category management

### Backend

- Add admin list, detail, update, and delete endpoints under
  `/api/v1/admin/categories`.
- Add an admin response model containing `active`; keep the public response
  contract unchanged.
- Apply `@Valid` to create/update requests, trim values, generate or normalize
  slugs, and enforce case-insensitive unique names and unique slugs.
- Refuse deletion with HTTP 409 when a coupon references the category.
- Evict the public category cache after every mutation.
- Add service/controller/integration tests for create, update, duplicate name,
  duplicate slug, not found, deactivate/reactivate, unused delete, and referenced
  delete conflict.

### Frontend

- Use the admin list instead of the public active-only list.
- Replace the icon-action stub with a complete edit modal.
- Support name, Uzbek name, slug, sort order, active state, icon URL, and icon
  upload through the existing media API.
- Add delete confirmation, conflict messaging, mutation loading states, cache
  invalidation, and component/API tests.

## Task 3: Remove the final admin partner legacy code

- Delete the unreferenced `partner-redemptions` feature from `admin-app`.
- Add/extend a source and route regression proving partner redemption is exposed
  only by `frontend/partner`.
- Update active product documentation if it still describes an admin redemption
  route.

## Task 4: Local startup and Ant Design maintenance

- Make `scripts/dev/start-demo.sh` reuse existing named TopDim infrastructure
  containers so separate worktrees do not fail on Compose name conflicts.
- Disable mail health in the local demo environment so a working
  `identity-service` does not report health `DOWN` solely because SMTP is absent.
- Replace deprecated Ant Design `bordered`, `valueStyle`, and `Space.direction`
  usage in `admin-app`.
- Add shell-level launcher checks where practical and frontend source regression
  coverage for deprecated props.

## Task 5: Critical admin regression coverage

- Add focused tests for role visibility and the main business actions in:
  authentication/navigation, merchants, orders, purchased-coupon lookup, users,
  partner applications, refunds, complaints, reviews, staff, and audit log.
- Prefer API contract tests and small page tests over broad snapshots.
- Cover loading, empty, forbidden, not-found, conflict, retry, and mutation-error
  states according to each page's actual permissions.

## Task 6: Final verification and demo-data test plan

- Run full admin, partner, web, coupon-service, identity-service, order-service,
  lint, and build regressions.
- Review the complete branch diff for scope and stale documentation.
- Write a separate manual test plan for seeded local data covering
  `MODERATOR`, `ADMIN`, and `SUPER_ADMIN`, table/Kanban, status transitions,
  dashboard metrics, category lifecycle, support queues, and negative cases.
- Run that manual plan only after implementation is finished, record defects,
  fix them in small commits, then update PR delivery state.

## Execution notes

### 2026-08-04 — Task 1

- Backend RED: focused Gradle compilation failed because the dashboard DTO,
  service, controller, repository queries, and security contract did not exist.
- Backend GREEN: focused service/security tests passed. Full
  `order-service:test` passed with `DOCKER_API_VERSION=1.40`; without the
  override, the existing Testcontainers client requested obsolete Docker API
  1.32 while the local Docker daemon requires at least 1.40.
- Frontend RED: all three dashboard tests failed against the former hard-coded
  zeroes and placeholder panels.
- Frontend GREEN: 12 test files / 135 tests passed; lint and production build
  passed. The existing large-chunk build warning remains.
- Implemented role-aware data access: ADMIN/SUPER_ADMIN receive operational
  order, revenue, coupon, user, complaint, seven-day, and recent-order data;
  MODERATOR receives only coupon queue and complaint metrics.

### 2026-08-04 — Task 2

- Backend RED: the requested admin category response, CRUD service/controller
  contracts, case-insensitive repository checks, validation, and reference
  conflict behavior were absent.
- Backend GREEN: focused service, controller/security, and H2 repository tests
  cover active/inactive reads, normalization, duplicate races, validation,
  not-found, deactivate/reactivate payloads, deterministic ordering, and safe
  deletion. Flyway V25 adds case-insensitive unique indexes for name and slug.
- Frontend RED: the page still called the public active-only list and exposed an
  icon-edit placeholder without update/delete behavior.
- Frontend GREEN: the page now uses a typed admin CRUD client, exposes complete
  create/edit fields and media upload, shows active state, confirms deletion,
  preserves rows on 409, and invalidates both admin and public category caches.
- Full `coupon-service:test` including JaCoCo passed. Admin-app passed 14 test
  files / 140 tests, ESLint, and the production build. The previously observed
  large-chunk build warning remains; it is handled separately from category
  behavior.

### 2026-08-04 — Task 3

- RED: the ownership regression enumerated three orphan redemption modules in
  `admin-app`, while the canonical partner `RedeemPage` already existed.
- GREEN: removed the unused admin page, API wrapper, and stylesheet. The admin
  `/redeem` URL resolves to its normal dashboard fallback and no redemption
  source remains in the admin feature tree.
- Active product and QA documentation already states that redemption belongs to
  `frontend/partner`, so no product wording change was needed.
- Admin-app passed 15 test files / 142 tests, ESLint, and production build;
  partner-app passed ESLint and production build. Both builds retain the known
  large-chunk warning.

### 2026-08-04 — Task 4

- Launcher RED: the test could not load an infrastructure helper because the
  launcher had only one unconditional `docker compose up` path and no reusable
  environment configuration.
- Launcher GREEN: named TopDim containers are reused or started, only missing
  services are created under stable Compose project `topdim`, and local service
  processes receive `MANAGEMENT_HEALTH_MAIL_ENABLED=false`.
- Ant Design RED listed four remaining deprecated `Space.direction` uses.
  They now use `orientation`; installed-version checks confirmed that remaining
  `Descriptions bordered` props are valid and therefore were preserved.
- The fake-Docker launcher test and Bash syntax checks passed. Admin-app passed
  16 test files / 143 tests, ESLint, and production build. Running Vitest and
  ESLint concurrently once caused a resource-contention timeout in an otherwise
  green Ant modal test; the focused test and full sequential suite both passed,
  so no timeout was hidden or increased. The known large-chunk warning remains.
- Real containers/services were intentionally not started; the seeded local
  walkthrough remains deferred to Task 6 as agreed.

### 2026-08-04 — Task 5a: authentication and navigation guards

- RED: eight direct administrative URLs opened for `MODERATOR` despite the menu
  and backend restricting those operations to `ADMIN`/`SUPER_ADMIN`.
- GREEN: shared staff routes remain available to `MODERATOR`, while refunds,
  catalog, orders, purchased-coupon lookup, users, and partner applications now
  have a nested `ADMIN`/`SUPER_ADMIN` guard. System routes remain exclusive to
  `SUPER_ADMIN` and unauthenticated users still redirect to login.
- The focused role matrix has 16 passing scenarios. The complete admin-app suite
  passes with 17 test files / 159 tests, ESLint, and production build; only the
  already-known large-chunk warning remains.

### 2026-08-04 — Task 5b: merchant location integrity

- Backend RED proved that an ordinary merchant edit recreated normalized
  locations, accepted a location ID from another merchant, and physically
  deleted omitted rows. That invalidated stable location IDs used by cashier
  bindings in identity-service.
- Backend GREEN updates owned locations in place, rejects foreign/duplicate IDs,
  creates only genuinely new locations, and soft-deactivates omitted locations
  while retaining the existing active-coupon safety rule.
- Frontend RED proved that the edit form discarded location ID and coordinates.
  It now sends a typed update payload with stable IDs and exposes latitude and
  longitude controls so coordinates are preserved and editable.
- Full coupon-service tests including JaCoCo passed. Admin-app passes 18 test
  files / 160 tests, ESLint, and production build; the known chunk warning
  remains.

### 2026-08-04 — Task 5c: safe admin order contract

- Backend RED proved that admin order endpoints serialized the `Order` JPA
  entity, including relations capable of exposing purchased-coupon redemption
  secrets. Both list and detail now return a minimal `AdminOrderResponse` with
  no items, purchased coupons, or `qrToken`.
- Frontend RED showed only an internal database ID and an untranslated
  `COMPLETED` status. The table now displays the business order number, covers
  every backend `OrderStatus`, renders zero totals correctly, and includes the
  missing `COMPLETED` and `REFUND_REQUESTED` filters.
- UI regression coverage includes success, empty, error, boundary-zero, and
  status-filter scenarios. Full order-service tests including PostgreSQL
  Testcontainers and JaCoCo passed with `DOCKER_API_VERSION=1.40 --no-daemon`;
  the no-daemon flag is required so an old Gradle daemon does not retain Docker
  API 1.32. Admin-app passes 19 test files / 165 tests, ESLint, and production
  build; the known chunk warning remains.

### 2026-08-04 — Task 5d: purchased-coupon support lookup

- Frontend RED proved that changing a searched code left stale coupon data on
  screen and that service/permission failures were mislabeled as "not found".
  Input changes now clear stale state, 404 has a dedicated warning, other
  failures use an error state, empty input makes no request, and duplicate
  submissions are blocked while loading.
- Backend regression tests cover trim/uppercase normalization, not-found,
  minimal response fields without `qrToken`, and ADMIN/SUPER_ADMIN versus
  MODERATOR/PARTNER/USER method security for both order list and coupon lookup.
- Runtime coverage exposed the installed Ant 6 `Alert.message` deprecation.
  All remaining admin alerts now use `title`, and the source guard prevents the
  prop from returning.
- Gradle now passes Docker API 1.40 explicitly to every test worker (while
  honoring an environment override), removing the intermittent Testcontainers
  fallback to unsupported API 1.32. Full order-service tests with PostgreSQL and
  JaCoCo pass without command-line workarounds. Admin-app passes 20 test files /
  169 tests, ESLint, and production build; the known chunk warning remains.

### 2026-08-04 — Task 5e: user management filters and safe blocking

- Backend RED proved that the advertised phone search did not inspect phone
  numbers and that providing search and role together silently discarded the
  role filter. The repository now searches phone values and applies both filters
  in one query; the service normalizes both inputs before selecting the query.
- Frontend RED proved that a failed list request looked like an empty table,
  administrator rows exposed a block action rejected by the backend, and an
  ordinary user could be blocked without confirmation. The page now separates
  load errors, marks ADMIN/SUPER_ADMIN accounts as protected, confirms block and
  unblock actions, surfaces API errors, and scopes loading to the affected row.
- Full identity-service tests including PostgreSQL and JaCoCo passed. Admin-app
  passes 21 test files / 173 tests, ESLint, and production build. One concurrent
  frontend/Gradle run caused the existing category modal test to reach its
  five-second timeout; both that test and the full frontend suite passed when
  rerun sequentially, so no timeout was increased. The known chunk warning
  remains.

### 2026-08-04 — Task 5f: partner-application operations

- Frontend RED proved that statuses were exposed as backend enum names, the
  backend's status filter was unreachable, empty and failed loads were
  indistinguishable, and mutation conflicts hid their actionable reason.
- The page now localizes statuses, sends a typed status filter, distinguishes
  empty from error, offers an explicit retry, and surfaces backend rejection or
  approval errors. Success wording no longer claims that an existing partner
  account was necessarily created.
- Backend RED proved that missing applications returned generic runtime errors
  and therefore HTTP 500 from detail, approve, and reject operations. All three
  now use the existing resource-not-found contract; controller coverage proves
  ADMIN access and rejects MODERATOR/anonymous access.
- Full identity-service tests including PostgreSQL and JaCoCo passed. Admin-app
  passes 22 test files / 177 tests, ESLint, and production build; the known
  large-chunk warning remains.

### 2026-08-04 — Task 5g: retry-safe partner approval

- RED proved that approval performed a remote merchant call inside the local
  identity transaction, the UI could not represent a recoverable partial
  failure, and coupon-service allowed two merchants with the same owner userId.
- Approval is now split into short `REQUIRES_NEW` preparation and completion
  transactions around the remote call. Preparation locks the application,
  commits one stable linked user and `PROCESSING` state, while retries reuse the
  same user. A completed retry is idempotent and does not call coupon-service.
- `PENDING` and `PROCESSING` applications share a unique phone boundary;
  coupon-service enforces one merchant per non-null userId. A failed remote call
  remains visible and retryable instead of silently rolling back into a state
  that could create duplicate users or merchants.
- The admin page localizes and filters `PROCESSING`, exposes only a retry action,
  and does not demand or transmit a temporary password during retry. Login and
  phone cannot change after preparation; merchant profile fields can be
  corrected before retry.
- Full identity-service and coupon-service suites including JaCoCo passed.
  Admin-app passes 22 test files / 178 tests, ESLint, and production build; the
  known large-chunk warning remains. Active PRD/backend documentation now records
  the intermediate state and migrations V15/V26.

### 2026-08-04 — Task 5h: safe refund operations

- Frontend RED proved that an unavailable refund API looked like an empty list,
  `APPROVED_PROCESSING` was presented as an already completed approval, mutation
  conflicts lost their backend reason, rejection accepted an empty explanation,
  and manual completion gave no warning that money must already be returned.
- The refund queue now has explicit error/retry and empty states, shows the
  expected payout deadline, preserves zero amounts, calls processing refunds
  `Ожидает выплаты`, requires a rejection reason, and warns that completion is a
  manual confirmation after the actual payout. No unsupported payment-provider
  integration was invented.
- Backend RED exposed an unvalidated decision payload, generic missing-request
  errors, unlocked concurrent decisions, and a legacy PATCH endpoint that could
  bypass the canonical state machine and coupon synchronization. Canonical
  approve/reject/complete operations now lock the refund row, return 404 for a
  missing request, enforce comment length and rejection reason, and the unsafe
  legacy endpoint is removed. Unknown resources are mapped to 404 instead of the
  global fallback's former 500.
- Focused UI coverage passes 5/5 and focused backend service/security coverage
  passes 27/27. Full admin-app verification passes 23 files / 183 tests, ESLint,
  and production build; the known large-chunk warning remains. Full order-service
  verification passes 120 tests (1 skipped), PostgreSQL Testcontainers, JaCoCo,
  and coverage verification with an explicit Docker API 1.40 environment.

### 2026-08-04 — Task 5i: complaint and review moderation safety

- Frontend RED proved that complaint load failures looked like empty queues,
  review load failures had no retry, mutation conflicts lost the backend's
  actionable message, and a review could be published with one unconfirmed
  click. Both queues now expose retryable errors and explicit empty states;
  review approval requires confirmation, mutation loading is scoped, and reject
  reasons are bounded to the database's 255-character column.
- Backend RED proved that complaints and reviews could be decided repeatedly or
  concurrently, missing records became 500 responses, blank review rejection
  reasons reached persistence, and the pending-review DTO omitted `userName`.
  Both state transitions now lock the row, accept only pending records, normalize
  inputs, return 404/409/400 by business outcome, and persist before publishing
  rejection/resolution notifications. The review queue now returns the author.
- Role regression uncovered that order-service method-level access denials were
  swallowed by the generic exception handler as HTTP 500. They now map to 403;
  MODERATOR/ADMIN/SUPER_ADMIN can operate support queues while PARTNER/USER cannot.
- `IN_REVIEW` remains an unused complaint enum value because the product has no
  agreed take-to-work/assignment operation. This workflow decision is kept for
  the final product-gap report instead of inventing hidden semantics in this task.
- Full admin-app verification passes 25 files / 189 tests, ESLint, and production
  build; the known large-chunk warning remains. Coupon-service passes 309 tests
  and order-service passes 132 tests, both with JaCoCo and coverage verification.
  Test workers now set docker-java's `api.version` JVM property as well as its
  environment variable, eliminating the intermittent Testcontainers API 1.32
  fallback against Docker Desktop's minimum 1.40.
