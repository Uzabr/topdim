# Coupon Workspace and Terminology Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragmented coupon administration screens with one URL-driven, server-paginated workspace and standardize active product copy around coupons and coupon offers rather than a separate “promotion” entity.

**Architecture:** Keep existing domain names and mutation endpoints, extend the admin list endpoint with compatible optional filters, and build a focused React workspace from pure URL-state and permission modules plus table/Kanban views. Legacy routes redirect to canonical routes; partner and bot approval paths remain unchanged.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA Specifications, JUnit 5, MockMvc, React 19, TypeScript 5.9, React Router 7, TanStack Query 5, Ant Design 6, Vitest 4, React Testing Library 16.

## Global Constraints

- Public buyer terminology is “Купон”; partner terminology is “Предложение” or “Купонное предложение”; admin terminology is “Купон”.
- Do not rename `CouponOffer`, `CouponOption`, database tables, JSON fields, or existing technical identifiers.
- Canonical routes are `/coupons`, `/coupons/new`, and `/coupons/:id/edit`; every documented legacy coupon route must redirect.
- Table page sizes are exactly 20, 50, or 100; backend accepts `size` from 1 through 100 and rejects other values with HTTP 400.
- Search debounce is exactly 300 ms; Kanban fetches 20 records per column page.
- Backend authorization, not button visibility, is the source of truth.
- Partner and bot approval endpoints must retain their existing behavior.
- Use strict TDD for every behavior change: observe RED, implement the minimum, observe GREEN.
- Every task ends with focused verification, one task-scoped commit, and `git push origin admin/codex`.
- Do not start a following task when the current task verification, commit, or push failed.

---

## File Map

### New frontend files

- `frontend/admin-app/vitest.config.ts` — admin-app test runner configuration.
- `frontend/admin-app/src/test/setup.ts` — browser storage and DOM test setup.
- `frontend/admin-app/src/features/coupons/workspace/types.ts` — coupon, filter, tab, and action types.
- `frontend/admin-app/src/features/coupons/workspace/state.ts` — URL parsing and tab/status mapping.
- `frontend/admin-app/src/features/coupons/workspace/permissions.ts` — pure role/status action matrix.
- `frontend/admin-app/src/features/coupons/workspace/api.ts` — typed admin list requests.
- `frontend/admin-app/src/features/coupons/workspace/CouponWorkspacePage.tsx` — workspace composition.
- `frontend/admin-app/src/features/coupons/workspace/CouponWorkspaceToolbar.tsx` — search, filters, view, create CTA.
- `frontend/admin-app/src/features/coupons/workspace/CouponStatusTabs.tsx` — product tabs.
- `frontend/admin-app/src/features/coupons/workspace/CouponTableView.tsx` — server-paginated table.
- `frontend/admin-app/src/features/coupons/workspace/CouponKanbanView.tsx` — independently paginated columns.
- `frontend/admin-app/src/features/coupons/workspace/CouponActionMenu.tsx` — shared mutations and actions.
- `frontend/admin-app/src/features/coupons/workspace/LegacyCouponRedirect.tsx` — compatibility redirects.
- Co-located `*.test.ts` and `*.test.tsx` files — TDD coverage for each module.

### New backend files

- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/AdminCouponFilter.java` — immutable filter value.
- `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponAssigneeResponse.java` — assigned moderator filter option.
- `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferSpecifications.java` — dynamic admin predicates.
- `services/coupon-service/src/test/java/uz/topdim/coupon/repository/CouponOfferAdminFilterTest.java` — real JPA filtering.
- `services/coupon-service/src/test/java/uz/topdim/coupon/controller/AdminCouponFilterControllerTest.java` — query validation and compatibility.
- `services/coupon-service/src/test/java/uz/topdim/coupon/controller/ModCouponReviewSecurityTest.java` — support-decision role boundary.

### Existing files changed or retired

- `frontend/admin-app/package.json`, `package-lock.json`, `tsconfig.node.json` — test tooling.
- `frontend/admin-app/src/App.tsx`, `components/layout/AdminLayout.tsx` — canonical routes and one menu entry.
- `frontend/admin-app/src/features/coupons/CouponFormPage.tsx` — canonical navigation and copy.
- `frontend/admin-app/src/features/coupons/CouponsListPage.tsx` — retired after table migration.
- `frontend/admin-app/src/features/coupons/CouponKanbanPage.tsx` — retired after Kanban migration.
- `frontend/admin-app/src/features/coupon-requests/CouponRequestsPage.tsx` — retired after workspace migration.
- `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx` — retired after support action migration.
- `frontend/partner/src/pages/CouponRequestFormPage.tsx`, `CouponsPage.tsx`, `CouponApprovalPage.tsx` — partner copy.
- `frontend/partner/src/pages/LoginPage.tsx`, `RedeemPage.tsx`, `layouts/PartnerLayout.tsx` — public brand copy.
- `frontend/web-app/src/locales/ru.json`, `uz.json`, `pages/legal/partners/PartnerLandingPage.tsx` — buyer and public brand copy.
- `services/coupon-service/.../AdminCouponController.java`, `CouponOfferService.java`, `CouponOfferRepository.java` — filters.
- `services/coupon-service/.../ModCouponController.java`, `ModCouponService.java` — support decision restriction and reason.
- `services/coupon-service/.../PartnerCouponController.java`, `PartnerCouponService.java`, `CreateCouponOfferRequest.java`, `CreatePartnerCouponRequest.java` — user-visible partner validation, response, and notification copy.
- `docs/product/roles.md`, `docs/product/flows/coupon-flow.md` — active product documentation.

---

### Task 1: Add the admin-app test foundation

**Files:**
- Modify: `frontend/admin-app/package.json`
- Modify: `frontend/admin-app/package-lock.json`
- Modify: `frontend/admin-app/tsconfig.node.json`
- Create: `frontend/admin-app/vitest.config.ts`
- Create: `frontend/admin-app/src/test/setup.ts`
- Create: `frontend/admin-app/src/test/smoke.test.tsx`

**Interfaces:**
- Produces: `npm test -- --run` and a jsdom environment available to every later frontend task.
- Consumes: existing Vite/React configuration; no application behavior.

- [x] **Step 1: Add a smoke test that cannot run yet**

```tsx
// frontend/admin-app/src/test/smoke.test.tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

describe('admin test environment', () => {
  it('renders React in jsdom', () => {
    render(<button type="button">Купоны</button>);
    expect(screen.getByRole('button', { name: 'Купоны' })).toBeTruthy();
  });
});
```

- [x] **Step 2: Run RED and record the missing test command/dependencies**

Run: `cd frontend/admin-app && npm test -- --run`

Expected: npm exits non-zero because `test` is not defined.

- [x] **Step 3: Install the exact test dependencies and add the script**

Run:

```bash
cd frontend/admin-app
npm install --save-dev @testing-library/react@^16.3.2 @testing-library/user-event@^14.6.1 jsdom@^29.1.1 vitest@^4.1.10
```

Add to `package.json`:

```json
"test": "vitest run"
```

- [x] **Step 4: Configure Vitest and storage**

```ts
// frontend/admin-app/vitest.config.ts
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
```

Copy the `MemoryStorage` implementation from `frontend/web-app/src/test/setup.ts`
into the new admin setup and stub `localStorage`. Add `vitest.config.ts` to the
`include` array of `tsconfig.node.json`.

- [x] **Step 5: Run GREEN and frontend quality gates**

Run:

```bash
cd frontend/admin-app
npm test -- --run
npm run lint
npm run build
```

Expected: one smoke test passes; lint and build exit 0.

- [x] **Step 6: Commit and push the test foundation**

```bash
git add frontend/admin-app/package.json frontend/admin-app/package-lock.json frontend/admin-app/tsconfig.node.json frontend/admin-app/vitest.config.ts frontend/admin-app/src/test
git diff --cached --check
git commit -m "test(admin): add frontend test foundation"
git push origin admin/codex
```

---

### Task 2: Add compatible backend filtering and bounded pagination

**Files:**
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/AdminCouponFilter.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponAssigneeResponse.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferSpecifications.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferRepository.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java`
- Create: `services/coupon-service/src/test/java/uz/topdim/coupon/repository/CouponOfferAdminFilterTest.java`
- Create: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/AdminCouponFilterControllerTest.java`

**Interfaces:**
- Produces: `AdminCouponFilter(Set<CouponStatus> statuses, String search, Long merchantId, Long assignedModeratorId)`.
- Produces: `CouponOfferSpecifications.forAdmin(AdminCouponFilter): Specification<CouponOffer>`.
- Produces: compatible `GET /api/v1/admin/coupons` optional parameters `status`, `statuses`, `search`, `merchantId`, `assignedModeratorId`, `page`, `size`.
- Produces: staff-readable `GET /api/v1/admin/coupons/assignees` with distinct assigned moderator IDs and display names already stored on coupon records.
- Consumes: existing `CouponOfferResponse` and descending `createdAt` sort.

- [x] **Step 1: Write repository RED tests for combined filters**

Create an H2 `@DataJpaTest` extending `AbstractIntegrationTest`. Persist merchants
`PizzaLab` and `Spa House`, then persist offers with statuses `LEAD`, `ACTIVE`, and
`PAUSED` and different `assignedModeratorId` values. Assert:

```java
Page<CouponOffer> result = repository.findAll(
        CouponOfferSpecifications.forAdmin(new AdminCouponFilter(
                Set.of(CouponStatus.ACTIVE, CouponStatus.PAUSED),
                "pizza", pizzaMerchant.getId(), 77L)),
        PageRequest.of(0, 20));

assertThat(result.getContent()).extracting(CouponOffer::getTitle)
        .containsExactly("Pizza family coupon");
```

Add a second assertion proving case-insensitive merchant-name search.
Persist 521 matching offers and request
`PageRequest.of(25, 20, Sort.by("id").ascending())`; assert that the page contains
the deterministic records 501-520 and reports the full `totalElements`. This is
the regression guard against the existing fixed 50/100/500 frontend limits.

- [x] **Step 2: Run RED for repository support**

Run: `./gradlew :services:coupon-service:test --tests '*CouponOfferAdminFilterTest' -x jacocoTestCoverageVerification`

Expected: compilation fails because `AdminCouponFilter`, specifications, and
`JpaSpecificationExecutor` support do not exist.

- [x] **Step 3: Implement the filter value and specification**

```java
public record AdminCouponFilter(
        Set<CouponStatus> statuses,
        String search,
        Long merchantId,
        Long assignedModeratorId
) {}
```

`forAdmin` must compose predicates only for present fields. Search predicate:

```java
String pattern = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
predicate = cb.and(predicate, cb.or(
        cb.like(cb.lower(root.get("title")), pattern),
        cb.like(cb.lower(root.get("merchant").get("name")), pattern)));
```

Extend `CouponOfferRepository` with `JpaSpecificationExecutor<CouponOffer>`.

- [x] **Step 4: Run repository GREEN**

Run: `./gradlew :services:coupon-service:test --tests '*CouponOfferAdminFilterTest' -x jacocoTestCoverageVerification`

Expected: all filter cases pass.

- [x] **Step 5: Write controller RED tests for compatibility and validation**

Using `@WebMvcTest(AdminCouponController.class)` with `SecurityConfig`,
`RoleHeaderAuthenticationFilter`, and `GlobalExceptionHandler`, assert with
staff-role headers:

```java
mockMvc.perform(get("/api/v1/admin/coupons")
        .param("status", "LEAD")
        .param("page", "0")
        .param("size", "20"))
    .andExpect(status().isOk());

mockMvc.perform(get("/api/v1/admin/coupons")
        .param("status", "LEAD")
        .param("statuses", "ACTIVE,PAUSED"))
    .andExpect(status().isBadRequest());

mockMvc.perform(get("/api/v1/admin/coupons").param("size", "101"))
    .andExpect(status().isBadRequest());
```

Also assert `page=-1` and `size=0` return 400. Verify the service receives the
expected status set for `statuses` and is never invoked for invalid bounds.
Assert MODERATOR, ADMIN, and SUPER_ADMIN can read `/coupons/assignees`, and the
response contains no account fields beyond `id` and `name`.

- [x] **Step 6: Run controller RED**

Run: `./gradlew :services:coupon-service:test --tests '*AdminCouponFilterControllerTest' -x jacocoTestCoverageVerification`

Expected: new query parameters and bounded validation are absent.

- [x] **Step 7: Implement the compatible controller/service contract**

Before constructing `PageRequest`, explicitly reject `page < 0`, `size < 1`, and
`size > 100` with `IllegalArgumentException` so the existing
`GlobalExceptionHandler` reliably returns HTTP 400. Reject simultaneous `status`
and non-empty `statuses` the same way. Normalize blank search to `null` and call:

```java
couponOfferService.getAllForAdmin(
        new AdminCouponFilter(resolvedStatuses, search, merchantId, assignedModeratorId),
        page,
        size);
```

The service uses a pageable sorted by `createdAt DESC` and
`repository.findAll(specification, pageable).map(this::mapToResponse)`.
Add a distinct repository projection for non-null `assignedModeratorId` values,
sorted by display name, and expose it through the controller. Do not call the
SUPER_ADMIN-only identity staff endpoint from this workflow.

- [x] **Step 8: Run backend GREEN and full coupon-service regression**

Run:

```bash
./gradlew :services:coupon-service:test --tests '*AdminCouponFilterControllerTest' --tests '*CouponOfferAdminFilterTest' -x jacocoTestCoverageVerification
./gradlew :services:coupon-service:test
```

Expected: focused tests and the full coupon-service suite pass.

- [x] **Step 9: Commit and push backend filtering**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java services/coupon-service/src/main/java/uz/topdim/coupon/dto/AdminCouponFilter.java services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponAssigneeResponse.java services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferRepository.java services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferSpecifications.java services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java services/coupon-service/src/test/java/uz/topdim/coupon/controller/AdminCouponFilterControllerTest.java services/coupon-service/src/test/java/uz/topdim/coupon/repository/CouponOfferAdminFilterTest.java
git diff --cached --check
git commit -m "feat(coupons): add paginated admin filters"
git push origin admin/codex
```

---

### Task 3: Define workspace URL state and permission rules

**Files:**
- Create: `frontend/admin-app/src/features/coupons/workspace/types.ts`
- Create: `frontend/admin-app/src/features/coupons/workspace/state.ts`
- Create: `frontend/admin-app/src/features/coupons/workspace/state.test.ts`
- Create: `frontend/admin-app/src/features/coupons/workspace/permissions.ts`
- Create: `frontend/admin-app/src/features/coupons/workspace/permissions.test.ts`

**Interfaces:**
- Produces: `CouponTab`, `CouponView`, `CouponStatus`, `CouponAction`, `CouponWorkspaceState`, `AdminCouponRow`.
- Produces: `parseWorkspaceState(URLSearchParams): CouponWorkspaceState`.
- Produces: `workspaceStatuses(CouponTab): CouponStatus[]`.
- Produces: `nextWorkspaceSearch(current, patch): URLSearchParams`.
- Produces: `allowedCouponActions(coupon, user): CouponAction[]`.

- [x] **Step 1: Write URL-state RED tests**

Cover defaults, invalid values, page reset on tab change, filter preservation, and
the exact published mapping:

```ts
expect(workspaceStatuses('published')).toEqual(['ACTIVE', 'PAUSED', 'SOLD_OUT']);
expect(parseWorkspaceState(new URLSearchParams('tab=bad&view=bad&page=-2'))).toMatchObject({
  tab: 'new', view: 'table', page: 0, pageSize: 20,
});
```

Also prove `size=50` is preserved and `size=0|21|101|invalid` normalizes to 20.
Assert the complete tab mapping:

```ts
new: ['LEAD']
in-progress: ['DRAFT']
revision: ['REVISION_REQUESTED']
waiting-partner: ['WAITING_FOR_MERCHANT']
published: ['ACTIVE', 'PAUSED', 'SOLD_OUT']
archived: ['ARCHIVED']
```

- [x] **Step 2: Run state RED**

Run: `cd frontend/admin-app && npm test -- src/features/coupons/workspace/state.test.ts`

Expected: imports fail because the state module does not exist.

- [x] **Step 3: Implement minimal state types and functions**

Use exact unions:

```ts
export type CouponTab = 'new' | 'in-progress' | 'revision' | 'waiting-partner' | 'published' | 'archived';
export type CouponView = 'table' | 'kanban';
export type CouponStatus = 'LEAD' | 'DRAFT' | 'REVISION_REQUESTED' | 'WAITING_FOR_MERCHANT' | 'ACTIVE' | 'PAUSED' | 'SOLD_OUT' | 'ARCHIVED';
export type CouponAction = 'view' | 'take-to-work' | 'edit' | 'send-to-approval' | 'support-review' | 'pause' | 'restore' | 'archive';
```

Only accept page sizes `20 | 50 | 100`; debounce remains a UI concern.

- [x] **Step 4: Run state GREEN**

Run: `cd frontend/admin-app && npm test -- src/features/coupons/workspace/state.test.ts`

- [x] **Step 5: Write permission RED tests**

Create one test per business rule, including ownership:

```ts
expect(allowedCouponActions(lead, moderator)).toContain('take-to-work');
expect(allowedCouponActions(otherModeratorsDraft, moderator)).not.toContain('edit');
expect(allowedCouponActions(waiting, moderator)).toEqual(['view']);
expect(allowedCouponActions(waiting, admin)).toContain('support-review');
```

Use this exact matrix; `view` is always first and SUPER_ADMIN equals ADMIN:

| Status | MODERATOR | ADMIN / SUPER_ADMIN |
|---|---|---|
| `LEAD` | view, take-to-work | view, take-to-work |
| owned `DRAFT` / `REVISION_REQUESTED` | view, edit, send-to-approval | view, edit, send-to-approval |
| unowned `DRAFT` / `REVISION_REQUESTED` | view | view, edit, send-to-approval |
| `WAITING_FOR_MERCHANT` | view | view, support-review |
| `ACTIVE` | view | view, pause |
| `PAUSED` | view | view, restore, archive |
| `SOLD_OUT` | view | view, archive |
| `ARCHIVED` | view | view |

No role receives `delete` or `reject-request` because they are not workspace
actions in the approved design.

- [x] **Step 6: Implement and verify the permission matrix**

Run RED, implement one exhaustive `switch (coupon.status)`, then run:

```bash
cd frontend/admin-app
npm test -- src/features/coupons/workspace/permissions.test.ts
npm run lint
```

- [x] **Step 7: Commit and push the pure workspace model**

```bash
git add frontend/admin-app/src/features/coupons/workspace
git diff --cached --check
git commit -m "feat(admin): define coupon workspace rules"
git push origin admin/codex
```

---

### Task 4: Add canonical routes, redirects, and workspace shell

**Files:**
- Create: `frontend/admin-app/src/features/coupons/workspace/LegacyCouponRedirect.tsx`
- Create: `frontend/admin-app/src/features/coupons/workspace/LegacyCouponRedirect.test.tsx`
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponWorkspacePage.tsx`
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponWorkspacePage.test.tsx`
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponWorkspaceToolbar.tsx`
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponStatusTabs.tsx`
- Modify: `frontend/admin-app/src/App.tsx`
- Modify: `frontend/admin-app/src/components/layout/AdminLayout.tsx`
- Modify: `frontend/admin-app/src/features/coupons/CouponFormPage.tsx`

**Interfaces:**
- Consumes: state functions and types from Task 3.
- Produces: canonical route components and compatibility redirects.
- Produces: one menu entry with key `/coupons`.

- [x] **Step 1: Write redirect RED tests**

Render `LegacyCouponRedirect` inside `MemoryRouter` and assert final locations for
all six mappings from the design spec, including edit ID preservation.

```text
/moderation/coupons          -> /coupons
/moderation/coupons/kanban   -> /coupons?view=kanban
/moderation/requests         -> /coupons?tab=new
/moderation/coupons/create   -> /coupons/new
/moderation/coupons/edit/:id -> /coupons/:id/edit
/moderation/coupons/review   -> /coupons?tab=waiting-partner
```

```tsx
expect(screen.getByTestId('location').textContent)
  .toBe('/coupons/42/edit');
```

- [x] **Step 2: Run RED, implement redirects, run GREEN**

Run: `cd frontend/admin-app && npm test -- LegacyCouponRedirect.test.tsx`

Use `useParams`, `useLocation`, and `<Navigate replace>`; never concatenate an
unvalidated query supplied by a legacy route.

- [x] **Step 3: Write workspace-shell RED test**

Assert the page renders heading `Купоны`, six tabs, `Таблица`, `Kanban`, and
`Создать купон`, and that changing a tab updates the URL while preserving
`search` and resetting `page`. Assert merchant options come from the existing
`/api/v1/admin/merchants` endpoint and assignee options come from
`/api/v1/admin/coupons/assignees`; neither filter is built from the incomplete
current coupon page.

- [x] **Step 4: Implement shell, toolbar, and tabs**

Use `useSearchParams`; debounce the visible search input by 300 ms before writing
`search` to the URL. Persist the selected table size as `size=20|50|100` and
normalize any other value to 20. Navigate create CTA to `/coupons/new`.

- [x] **Step 5: Replace routes and menu**

Add canonical routes under the existing staff `ProtectedRoute`; add legacy
redirect routes; remove four separate coupon menu children and expose one
`Купоны` child. Update form back/success navigation to canonical paths.

- [x] **Step 6: Verify route behavior and frontend build**

```bash
cd frontend/admin-app
npm test -- LegacyCouponRedirect.test.tsx CouponWorkspacePage.test.tsx
npm run lint
npm run build
```

- [x] **Step 7: Commit and push canonical navigation**

```bash
git add frontend/admin-app/src/App.tsx frontend/admin-app/src/components/layout/AdminLayout.tsx frontend/admin-app/src/features/coupons
git diff --cached --check
git commit -m "feat(admin): add canonical coupon workspace"
git push origin admin/codex
```

---

### Task 5: Build the server-paginated table view and explicit error states

**Files:**
- Create: `frontend/admin-app/src/features/coupons/workspace/api.ts`
- Create: `frontend/admin-app/src/features/coupons/workspace/api.test.ts`
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponTableView.tsx`
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponTableView.test.tsx`
- Modify: `frontend/admin-app/src/features/coupons/workspace/CouponWorkspacePage.tsx`

**Interfaces:**
- Produces: `fetchAdminCoupons(state, pageParam?): Promise<PageResponse<AdminCouponRow>>`.
- Consumes: backend query contract from Task 2 and state mapping from Task 3.
- Produces: table callbacks `onPageChange(page, pageSize)` and `onAction(coupon, action)`.

- [x] **Step 1: Write API parameter RED tests**

Spy on `api.get` and prove `published` sends `statuses=ACTIVE,PAUSED,SOLD_OUT`,
while `new` sends `status=LEAD`, plus trimmed search and numeric filters.

- [x] **Step 2: Implement the typed API helper and observe GREEN**

The helper must omit empty parameters and use the query key prefix
`['admin-coupons', 'workspace']` in callers.

- [x] **Step 3: Write table RED tests**

Cover:

- total and current page come from backend metadata;
- changing to page 3 calls the supplied callback with zero-based page 2;
- 20/50/100 selector is available;
- failed query renders `Не удалось загрузить купоны` and `Повторить`, not the
  active tab empty text;
- a 403 renders an explicit no-permission explanation rather than an empty state;
- a 404 renders that the coupon was removed or the link is stale;
- a network timeout with retained data marks the rows as stale and shows the
  last successful load time;
- successful empty page renders the tab-specific empty text.

- [x] **Step 4: Implement the minimal table and query state**

Use `placeholderData` to retain the last valid page. Show an `Alert` when
`isError`, a stale-data warning when retained data is visible, and the backend
error message when present. Keep 401 handling delegated to the existing shared
Axios refresh interceptor; do not add a second refresh loop inside the workspace.

- [x] **Step 5: Verify table behavior and quality gates**

```bash
cd frontend/admin-app
npm test -- src/features/coupons/workspace/api.test.ts src/features/coupons/workspace/CouponTableView.test.tsx
npm run lint
npm run build
```

- [x] **Step 6: Commit and push the table view**

```bash
git add frontend/admin-app/src/features/coupons/workspace
git diff --cached --check
git commit -m "feat(admin): add paginated coupon table"
git push origin admin/codex
```

---

### Task 6: Replace the fixed-limit Kanban with paginated columns

**Files:**
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponKanbanView.tsx`
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponKanbanView.test.tsx`
- Modify: `frontend/admin-app/src/features/coupons/workspace/CouponWorkspacePage.tsx`

**Interfaces:**
- Consumes: `fetchAdminCoupons`, `CouponStatus`, and shared action callback.
- Produces: four operational columns for `LEAD`, `DRAFT`, `REVISION_REQUESTED`, and `WAITING_FOR_MERCHANT`.
- Produces: independent `fetchNextPage` per column with page size 20.

- [x] **Step 1: Write Kanban RED tests**

Mock two pages for `LEAD` and one page for other columns. Assert:

```tsx
expect(api.get).not.toHaveBeenCalledWith(expect.anything(),
  expect.objectContaining({ params: expect.objectContaining({ size: 500 }) }));
await user.click(screen.getByRole('button', { name: 'Показать ещё: Новые' }));
expect(api.get).toHaveBeenCalledWith('/api/v1/admin/coupons',
  expect.objectContaining({ params: expect.objectContaining({ status: 'LEAD', page: 1, size: 20 }) }));
```

Also prove one failed column shows its own retry without hiding successful columns.

- [x] **Step 2: Run RED**

Run: `cd frontend/admin-app && npm test -- CouponKanbanView.test.tsx`

- [x] **Step 3: Implement one infinite query per column**

Use `useInfiniteQuery` with:

```ts
initialPageParam: 0,
getNextPageParam: (lastPage) => lastPage.last
  ? undefined
  : lastPage.pageable.pageNumber + 1,
```

Flatten only the pages of the current column. Do not fetch published/archive
statuses in Kanban. When the selected tab is not operational, the workspace
automatically displays the table and updates `view=table`.

- [x] **Step 4: Run GREEN, lint, and build**

```bash
cd frontend/admin-app
npm test -- CouponKanbanView.test.tsx
npm run lint
npm run build
```

- [x] **Step 5: Commit and push paginated Kanban**

```bash
git add frontend/admin-app/src/features/coupons/workspace
git diff --cached --check
git commit -m "feat(admin): paginate coupon kanban columns"
git push origin admin/codex
```

---

### Task 7: Enforce workspace actions in the backend

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/ModCouponController.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/ModCouponService.java`
- Create: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/AdminCouponActionSecurityTest.java`
- Create: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/ModCouponReviewSecurityTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceBusinessLogicTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/ModCouponServiceTest.java`

**Interfaces:**
- Keeps: all existing endpoint paths and partner/bot flows.
- Changes: generic `PATCH /api/v1/admin/coupons/{id}/status`, archive, delete, and partner-request rejection are `ADMIN`/`SUPER_ADMIN` only.
- Changes: generic status updates allow only `ACTIVE -> PAUSED` and `PAUSED -> ACTIVE`; dedicated take, send, partner/bot, and support endpoints own the other transitions.
- Changes: moderator send-to-approval and edit operations require exact ownership, including rejecting unassigned or another moderator's coupon.
- Produces: support review restricted to `ADMIN`/`SUPER_ADMIN` with a non-blank reason and actor-aware logging.

- [x] **Step 1: Write controller security RED tests**

Using `@WebMvcTest`, `SecurityConfig`, and `RoleHeaderAuthenticationFilter`,
assert:

- MODERATOR gets 403 from generic status, archive, delete, reject-request, and
  support-review endpoints;
- ADMIN and SUPER_ADMIN can reach those controller methods;
- MODERATOR can still take a LEAD and call send-to-approval;
- review moderation remains available to MODERATOR;
- calls that pass security forward `X-User-Id` and `X-User-Role` to the service.

- [x] **Step 2: Run controller security RED**

Run:

```bash
./gradlew :services:coupon-service:test --tests '*AdminCouponActionSecurityTest' --tests '*ModCouponReviewSecurityTest' -x jacocoTestCoverageVerification
```

Expected: MODERATOR can currently reach privileged coupon mutations and support
review.

- [x] **Step 3: Write service RED tests for transitions, ownership, and reason**

Assert:

- `updateStatus` allows only `ACTIVE -> PAUSED` and `PAUSED -> ACTIVE`;
- `WAITING_FOR_MERCHANT -> ACTIVE/REVISION_REQUESTED`, `LEAD -> DRAFT`, and
  `DRAFT/REVISION_REQUESTED -> WAITING_FOR_MERCHANT` are rejected by the generic
  status method;
- archive accepts `ACTIVE`, `PAUSED`, and `SOLD_OUT` with a trimmed reason;
- MODERATOR can edit/send only when `assignedModeratorId` equals the actor ID;
- an unassigned or another moderator's coupon is rejected before mutation;
- ADMIN/SUPER_ADMIN can edit/send any eligible coupon;
- blank support reason is rejected before state transition and notification.

- [x] **Step 4: Implement the minimum backend enforcement**

Apply method-level role restrictions to the privileged controller methods. Pass
actor ID and role from `send-to-approval` into the service and centralize the
moderator ownership assertion so edit and send use the same rule. Keep take-to-
work as the only `LEAD -> DRAFT` path. In `reviewCoupon`, trim and require the
reason, then log actor ID, coupon ID, decision, and reason only after the state
transition succeeds.

- [x] **Step 5: Run focused and full backend GREEN**

```bash
./gradlew :services:coupon-service:test --tests '*AdminCouponActionSecurityTest' --tests '*ModCouponReviewSecurityTest' -x jacocoTestCoverageVerification
./gradlew :services:coupon-service:test --tests '*CouponOfferServiceBusinessLogicTest' --tests '*CouponOfferServiceTest' --tests '*ModCouponServiceTest' -x jacocoTestCoverageVerification
./gradlew :services:coupon-service:test --tests '*PartnerCouponServiceTest' --tests '*BotWebhookApiKeyTest' -x jacocoTestCoverageVerification
./gradlew :services:coupon-service:test
```

- [x] **Step 6: Commit and push backend action enforcement**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java services/coupon-service/src/main/java/uz/topdim/coupon/controller/ModCouponController.java services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java services/coupon-service/src/main/java/uz/topdim/coupon/service/ModCouponService.java services/coupon-service/src/test/java/uz/topdim/coupon/controller/AdminCouponActionSecurityTest.java services/coupon-service/src/test/java/uz/topdim/coupon/controller/ModCouponReviewSecurityTest.java services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceBusinessLogicTest.java services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java services/coupon-service/src/test/java/uz/topdim/coupon/service/ModCouponServiceTest.java
git diff --cached --check
git commit -m "fix(coupons): enforce workspace action rules"
git push origin admin/codex
```

---

### Task 8: Centralize table and Kanban actions

**Files:**
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponActionMenu.tsx`
- Create: `frontend/admin-app/src/features/coupons/workspace/CouponActionMenu.test.tsx`
- Modify: `frontend/admin-app/src/features/coupons/workspace/CouponTableView.tsx`
- Modify: `frontend/admin-app/src/features/coupons/workspace/CouponKanbanView.tsx`

**Interfaces:**
- Consumes: `allowedCouponActions` from Task 3 and secured endpoints from Task 7.
- Produces: one action component used by both views.
- Produces: no standalone support queue or menu item.

- [x] **Step 1: Write frontend RED tests for the complete matrix**

Assert table and Kanban render identical actions for every status/role/ownership
case from the design. Specifically, a moderator waiting coupon has only
`Просмотреть`; an admin has `Служебное решение`; empty reason keeps submit
disabled; a valid reason sends:

```ts
{ status: 'APPROVE', reason: 'Партнёр подтвердил по телефону, обращение SUP-42' }
```

Assert moderator never sees pause, restore, archive, delete, reject-request, or
support actions.

- [x] **Step 2: Run frontend RED**

Run: `cd frontend/admin-app && npm test -- CouponActionMenu.test.tsx`

- [x] **Step 3: Implement the shared action component**

Use the dedicated take/send endpoints, generic status only for admin pause and
restore, archive only for eligible admin statuses, and support review only from
`WAITING_FOR_MERCHANT`. Show the required warning before support submission. Do
not expose reject or delete in this workspace. Do not optimistically change
status.

- [x] **Step 4: Cover mutation failures**

Add tests proving 403 explains missing permission without retry, 404 reports a
stale/deleted coupon, and 409 shows the backend conflict then invalidates
`['admin-coupons', 'workspace']` so ownership/status refreshes.

- [x] **Step 5: Run frontend GREEN and quality gates**

```bash
cd frontend/admin-app
npm test -- CouponActionMenu.test.tsx CouponTableView.test.tsx CouponKanbanView.test.tsx
npm run lint
npm run build
```

- [x] **Step 6: Commit and push shared actions**

```bash
git add frontend/admin-app/src/features/coupons/workspace/CouponActionMenu.tsx frontend/admin-app/src/features/coupons/workspace/CouponActionMenu.test.tsx frontend/admin-app/src/features/coupons/workspace/CouponTableView.tsx frontend/admin-app/src/features/coupons/workspace/CouponKanbanView.tsx
git diff --cached --check
git commit -m "feat(admin): centralize coupon workspace actions"
git push origin admin/codex
```

---

### Task 9: Standardize active coupon terminology across applications

**Files:**
- Create: `frontend/admin-app/src/features/coupons/workspace/terminology.test.ts`
- Modify: `frontend/admin-app/src/features/coupons/CouponFormPage.tsx`
- Modify: `frontend/admin-app/src/features/coupons/workspace/*.tsx`
- Modify: `frontend/admin-app/src/features/auth/LoginPage.tsx`
- Modify: `frontend/admin-app/src/components/layout/AdminLayout.tsx`
- Modify: `frontend/partner/src/pages/CouponRequestFormPage.tsx`
- Modify: `frontend/partner/src/pages/CouponsPage.tsx`
- Modify: `frontend/partner/src/pages/CouponApprovalPage.tsx`
- Modify: `frontend/partner/src/pages/LoginPage.tsx`
- Modify: `frontend/partner/src/pages/RedeemPage.tsx`
- Modify: `frontend/partner/src/layouts/PartnerLayout.tsx`
- Modify: `frontend/web-app/src/locales/ru.json`
- Modify: `frontend/web-app/src/locales/uz.json`
- Modify: `frontend/web-app/src/pages/legal/partners/PartnerLandingPage.tsx`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreatePartnerCouponRequest.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java`

**Interfaces:**
- Produces: buyer/admin “Купон” copy and partner “Предложение” copy.
- Preserves: technical identifiers, API paths, entity names, and merchant-authored descriptions.

- [x] **Step 1: Write a source-copy contract and observe RED**

The Vitest test reads the explicitly listed active source files with `node:fs`.
Assert admin/partner interface literals do not contain
`/акци(?:я|и|ю|ей|ям|ями|ях|е)/iu`
or the user terms `оффер`/`товар`, and public brand literals do not contain
`TopDim`. Include the login/layout, partner QR error, partner landing, and
backend validation/notification sources listed above. Exclude comments,
technical identifiers, archived docs, email addresses, and merchant-authored
example text from this contract.

Run: `cd frontend/admin-app && npm test -- terminology.test.ts`

Expected: failures point to current `Мои акции`, `Заявка на акцию`, and related
active UI copy.

- [x] **Step 2: Replace copy according to the approved dictionary**

Use exact replacements:

```text
Мои акции → Мои предложения
Создать заявку на акцию → Подать купонное предложение
Название акции → Название предложения
Описание акции → Описание предложения
Цена по акции → Цена по предложению
Варианты акции → Варианты предложения
Акции и купоны → Купоны
Описание оффера → Описание купона
товар (в корзине купонов) → купон
TopDim → sizbiz (public UI copy only)
```

Do not change `CouponOffer`, `offerDescription`, `/coupons`, `TOPDIM-QR:`, Java
packages, storage keys, email domains, database identifiers, or historical
documents. Backend validation responses and notifications are user-facing copy,
so update them. Do not change log-only technical wording in this task.

- [x] **Step 3: Run terminology GREEN and all affected frontend checks**

```bash
cd frontend/admin-app
npm test -- terminology.test.ts
npm run lint
npm run build
cd ../partner
npm run lint
npm run build
cd ../web-app
npm test -- --run
npm run lint
npm run build
cd ../..
./gradlew :services:coupon-service:test
```

- [x] **Step 4: Commit and push terminology**

```bash
git add frontend/admin-app/src/components/layout/AdminLayout.tsx frontend/admin-app/src/features/auth/LoginPage.tsx frontend/admin-app/src/features/coupons/CouponFormPage.tsx frontend/admin-app/src/features/coupons/workspace/terminology.test.ts frontend/admin-app/src/features/coupons/workspace/CouponWorkspacePage.tsx frontend/admin-app/src/features/coupons/workspace/CouponWorkspaceToolbar.tsx frontend/admin-app/src/features/coupons/workspace/CouponStatusTabs.tsx frontend/admin-app/src/features/coupons/workspace/CouponTableView.tsx frontend/admin-app/src/features/coupons/workspace/CouponKanbanView.tsx frontend/admin-app/src/features/coupons/workspace/CouponActionMenu.tsx frontend/partner/src/layouts/PartnerLayout.tsx frontend/partner/src/pages/CouponRequestFormPage.tsx frontend/partner/src/pages/CouponsPage.tsx frontend/partner/src/pages/CouponApprovalPage.tsx frontend/partner/src/pages/LoginPage.tsx frontend/partner/src/pages/RedeemPage.tsx frontend/web-app/src/locales/ru.json frontend/web-app/src/locales/uz.json frontend/web-app/src/pages/legal/partners/PartnerLandingPage.tsx services/coupon-service/src/main/java/uz/topdim/coupon/controller/PartnerCouponController.java services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreatePartnerCouponRequest.java services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java
git diff --cached --check
git commit -m "refactor(ui): standardize coupon terminology"
git push origin admin/codex
```

---

### Task 10: Retire legacy screens, update active docs, and run full regression

**Files:**
- Delete: `frontend/admin-app/src/features/coupons/CouponsListPage.tsx`
- Delete: `frontend/admin-app/src/features/coupons/CouponKanbanPage.tsx`
- Delete: `frontend/admin-app/src/features/coupon-requests/CouponRequestsPage.tsx`
- Delete: `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx`
- Modify: `docs/product/roles.md`
- Modify: `docs/product/flows/coupon-flow.md`
- Modify: `docs/superpowers/plans/2026-08-03-coupon-workspace-and-terminology.md` — check completed boxes and append exact verification evidence.

**Interfaces:**
- Consumes: all completed workspace components.
- Produces: no imports or menu/routes pointing to retired pages.
- Produces: active documentation matching canonical routes, terminology, roles, and server pagination.

- [x] **Step 1: Write/extend a route regression test before deletion**

Assert every legacy URL reaches the workspace or canonical form and no route
renders old headings `Все купоны`, `Канбан купонов`, `Заявки на акции от
партнёров`, or `Ожидают подтверждения мерчанта` as standalone pages.

- [x] **Step 2: Record the user-approved GREEN characterization baseline**

Run: `cd frontend/admin-app && npm test -- LegacyCouponRedirect.test.tsx`

The originally specified RED is not applicable: before Task 10, the four pages
were already orphaned and all legacy routes already rendered `LegacyCouponRedirect`.
The user approved a GREEN behavioral characterization baseline instead; see the
dated execution evidence below.

- [x] **Step 3: Remove legacy imports/files and update active docs**

Delete only the four replaced pages. Keep `CouponFormPage` and publication
readiness helpers. Update roles and coupon flow with:

- `/coupons` as the admin entry point;
- six product tabs and table/Kanban behavior;
- MODERATOR inability to decide for the partner;
- ADMIN/SUPER_ADMIN support decision requiring a reason;
- partner language “предложение” and buyer/admin language “купон”.

- [x] **Step 4: Run fresh full verification**

```bash
cd frontend/admin-app
npm test -- --run
npm run lint
npm run build
cd ../partner
npm run lint
npm run build
cd ../web-app
npm test -- --run
npm run lint
npm run build
cd ../..
./gradlew :services:coupon-service:test
git diff --check
git status --short
```

Expected: all available test suites, lints, and builds exit 0; deleted files have
no remaining imports; only Task 10 files are uncommitted.

- [x] **Step 5: Review requirements line by line**

Compare the diff with
`docs/superpowers/specs/2026-08-03-coupon-workspace-and-terminology-design.md`.
Record each verification command and result at the end of this plan. Do not mark
the project complete while any criterion lacks evidence.

- [x] **Step 6: Commit final cleanup and record controller-owned push**

```bash
git add frontend/admin-app/src/features/coupons/CouponsListPage.tsx frontend/admin-app/src/features/coupons/CouponKanbanPage.tsx frontend/admin-app/src/features/coupon-requests/CouponRequestsPage.tsx frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx frontend/admin-app/src/features/coupons/workspace/LegacyCouponRedirect.test.tsx docs/product/roles.md docs/product/flows/coupon-flow.md docs/superpowers/plans/2026-08-03-coupon-workspace-and-terminology.md
git diff --cached --check
git commit -m "chore(admin): retire legacy coupon screens"
git push origin admin/codex
```

- [ ] **Step 7: Finish the branch with Superpowers**

Invoke `superpowers:verification-before-completion`, then
`superpowers:finishing-a-development-branch`. Present delivery state separately:
local commits, remote synchronization, CI status, and PR/merge state.

---

## Execution Notes

Append dated evidence here during execution. Each entry must include task number,
RED command/result, GREEN command/result, commit SHA, and push confirmation.

### 2026-08-04 — Task 10

- **Approved RED deviation / baseline:** The required pre-deletion RED could not be observed without adding a fake source/file-existence assertion. Before this task, `rg` found no imports of the four legacy page components and `App.tsx` already routed every legacy URL through `LegacyCouponRedirect`. The user approved a behavioral characterization instead. After extending `LegacyCouponRedirect.test.tsx`, `cd frontend/admin-app && npm test -- LegacyCouponRedirect.test.tsx` exited 0: **1 file, 17 tests passed**. It verifies all six legacy URLs reach their canonical URLs and none renders the four former standalone headings.
- **GREEN cleanup:** Deleted only `CouponsListPage.tsx`, `CouponKanbanPage.tsx`, `CouponRequestsPage.tsx`, and `MerchantReviewPage.tsx`; retained `CouponFormPage` and publication-readiness helpers. Updated active role and coupon-flow documentation for `/coupons`, six tabs, server pagination, Kanban's 20-record column pages, terminology, and the MODERATOR/ADMIN support-decision boundary.
- **Full regression:** `cd frontend/admin-app && npm test -- --run` — 11 files, 123 tests passed; `npm run lint` — exit 0; `npm run build` — exit 0 (existing chunk-size warning only). `cd frontend/partner && npm run lint` — exit 0; `npm run build` — exit 0 (existing chunk-size warning only). `cd frontend/web-app && npm test -- --run` — 22 files, 149 tests passed; `npm run lint` — exit 0 (one pre-existing `CouponCatalogPage.tsx` exhaustive-deps warning); `npm run build` — exit 0 (existing chunk-size warning only). `./gradlew :services:coupon-service:test` — BUILD SUCCESSFUL, exit 0.
- **Diff and scope review:** `rg` import check for all four retired page components returned no matches; `git diff --check` exited 0. The diff is restricted to the four deletions, `LegacyCouponRedirect.test.tsx`, the two active product documents, and this plan. Requirements reviewed against `docs/superpowers/specs/2026-08-03-coupon-workspace-and-terminology-design.md`: canonical redirects retained; six-tab/table/Kanban behavior documented; partner uses “предложение”; buyer/admin use “купон”; MODERATOR only views `WAITING_FOR_MERCHANT`; ADMIN/SUPER_ADMIN support decision requires a reason.
- **Delivery:** commit SHA `8976d51`; controller subsequently pushed it to `origin/admin/codex` after independent review and external-export approval.

### 2026-08-04 — Task 10 Fix Round 1

- **Review finding fixed:** `LegacyCouponRedirect.test.tsx` now mounts the real `App` route configuration with only minimal mocks for the protected/layout wrappers and stable canonical screen markers. For every legacy URL it asserts the actual canonical page and complete path/query state; it also proves no former standalone heading is rendered. This fails if `App.tsx` drops or miswires a legacy `<Route>`.
- **Sensitivity check (approved GREEN characterization, not fabricated RED):** temporarily changed the expected canonical target for `/moderation/requests` from `/coupons?tab=new` to `/coupons?tab=waiting-partner`, then ran `cd frontend/admin-app && npm test -- LegacyCouponRedirect.test.tsx`. The test exited 1 with the application-level assertion: expected `workspace:/coupons?tab=waiting-partner`, received `workspace:/coupons?tab=new`. Restored the expected target before final verification.
- **Final verification:** `cd frontend/admin-app && npm test -- LegacyCouponRedirect.test.tsx` — 1 file, 22 tests passed; `npm test -- --run` — 11 files, 128 tests passed; `npm run lint` — exit 0; `npm run build` — exit 0 (existing chunk-size warning only). `git diff --check` — exit 0. Follow-up commit SHA `0f69e18`; controller subsequently pushed it to `origin/admin/codex` after scoped review.
