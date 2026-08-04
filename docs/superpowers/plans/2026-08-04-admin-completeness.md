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
