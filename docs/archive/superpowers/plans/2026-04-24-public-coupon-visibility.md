# Public Coupon Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to execute this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** close the last major coupon-flow gap by making public coupon detail pages visible only for `ACTIVE` coupons and showing a controlled unavailable state for non-public or missing coupons.

**Architecture:** keep the existing public endpoint shape `GET /api/v1/coupons/{id}`, but change its semantics to return only public data. Do not introduce a new API or expand merchant scope. The backend should treat non-public coupons as unavailable to the public API, and the frontend should stop falling back to demo data and instead render explicit `loading / unavailable / normal` states.

**Tech Stack:** Spring Boot, JPA, JUnit 5, Mockito, MockMvc standalone setup, React 19, TypeScript, TanStack Query, Vite

---

## Required Skill Map

These skills are mandatory for the worker. Read them before the corresponding task.

### Root workflow skills

- `/.agent/skills/test-driven-development/SKILL.md`
  Use for every backend behavior change. No production code before a failing test.
- `/.agent/skills/systematic-debugging/SKILL.md`
  Use if a test fails for an unexpected reason or if frontend unavailable-state behavior is inconsistent.
- `/.agent/skills/verification-before-completion/SKILL.md`
  Use before any “done/passing/fixed” claim.

### Service-layer local skills

- `/services/.agent/skills/spring-boot-test-patterns/SKILL.md`
  Use for service and controller tests. Prefer focused unit/slice-style tests over full context.
- `/services/.agent/skills/api-design-principles/SKILL.md`
  Use to keep the public API behavior consistent. Recommendation for this task: public `GET /api/v1/coupons/{id}` should return `404` for non-public coupons instead of exposing draft/internal state.

### Frontend local skills

- `/frontend/.agent/skills/react/SKILL.md`
  Use for page state modeling, conditional rendering, and component extraction in `CouponDetailPage`.
- `/frontend/.agent/skills/frontend-design/SKILL.md`
  Use for the unavailable-state UI so it feels intentional and product-grade, not like a raw error dump.

---

## Product Decision Locked For This Plan

This plan fixes PRD rule `BR-C-002` by choosing one explicit implementation:

- Public coupon detail must be available only for `ACTIVE`.
- If coupon exists but is not public, the public API behaves as if it is unavailable and returns `404`.
- Frontend must render a controlled unavailable state, not demo content and not a raw transport error.

This keeps public REST semantics simple and avoids leaking internal coupon lifecycle to guests.

---

### Task 1: Restrict public coupon detail to `ACTIVE` only

**Required skills before starting this task:**
- `/.agent/skills/test-driven-development/SKILL.md`
- `/services/.agent/skills/spring-boot-test-patterns/SKILL.md`
- `/services/.agent/skills/api-design-principles/SKILL.md`

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`

- [ ] **Step 1: Write the failing service test for non-public coupon access**

Add these tests to `CouponOfferServiceTest`:

```java
@Test
@DisplayName("Public getById: ACTIVE coupon returns detail")
void getById_activeCoupon_returnsOffer() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.ACTIVE);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    CouponOfferResponse result = couponOfferService.getById(1L);

    assertThat(result.getTitle()).isEqualTo("SPA массаж 50%");
    verify(couponOfferRepository).incrementViewCount(1L);
}

@Test
@DisplayName("Public getById: WAITING_FOR_MERCHANT coupon returns not found")
void getById_waitingCoupon_throwsNotFound() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.WAITING_FOR_MERCHANT);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.getById(1L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("не найден");

    verify(couponOfferRepository, never()).incrementViewCount(anyLong());
}

@Test
@DisplayName("Public getById: DRAFT coupon returns not found")
void getById_draftCoupon_throwsNotFound() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.DRAFT);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.getById(1L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("не найден");

    verify(couponOfferRepository, never()).incrementViewCount(anyLong());
}
```

- [ ] **Step 2: Run the focused service test and confirm it fails for the right reason**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because current `getById` returns any coupon regardless of status.

- [ ] **Step 3: Implement the minimal public visibility guard**

Update `CouponOfferService.getById` to enforce `ACTIVE`:

```java
@Transactional
public CouponOfferResponse getById(Long id) {
    CouponOffer offer = couponOfferRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

    if (offer.getStatus() != CouponStatus.ACTIVE) {
        throw new ResourceNotFoundException("Купон не найден");
    }

    couponOfferRepository.incrementViewCount(id);
    return mapToResponse(offer);
}
```

Do not change `getByIdAdmin`.

- [ ] **Step 4: Re-run the focused service test**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java
git commit -m "fix: restrict public coupon detail to active offers"
```

---

### Task 2: Lock public endpoint semantics with controller coverage

**Required skills before starting this task:**
- `/.agent/skills/test-driven-development/SKILL.md`
- `/services/.agent/skills/spring-boot-test-patterns/SKILL.md`
- `/services/.agent/skills/api-design-principles/SKILL.md`

**Files:**
- Create: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/CouponPublicVisibilityTest.java`
- Reuse: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/CouponController.java`
- Reuse: `services/coupon-service/src/main/java/uz/topdim/coupon/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Write the failing controller tests for `404` semantics**

Create `CouponPublicVisibilityTest.java`:

```java
@ExtendWith(MockitoExtension.class)
class CouponPublicVisibilityTest {

    @Mock private CouponOfferService couponOfferService;
    @InjectMocks private CouponController couponController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(couponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/coupons/{id}: ACTIVE coupon returns 200")
    void getById_activeCoupon_returns200() throws Exception {
        when(couponOfferService.getById(7L)).thenReturn(
                CouponOfferResponse.builder().id(7L).title("Active coupon").status("ACTIVE").build()
        );

        mockMvc.perform(get("/api/v1/coupons/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    @DisplayName("GET /api/v1/coupons/{id}: non-public coupon returns 404")
    void getById_nonPublicCoupon_returns404() throws Exception {
        when(couponOfferService.getById(8L))
                .thenThrow(new ResourceNotFoundException("Купон не найден"));

        mockMvc.perform(get("/api/v1/coupons/8"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Купон не найден"));
    }
}
```

- [ ] **Step 2: Run the controller test**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.CouponPublicVisibilityTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS if Task 1 is already complete. If it fails unexpectedly, use `/.agent/skills/systematic-debugging/SKILL.md`.

- [ ] **Step 3: Commit**

```bash
git add services/coupon-service/src/test/java/uz/topdim/coupon/controller/CouponPublicVisibilityTest.java
git commit -m "test: lock public coupon visibility semantics"
```

---

### Task 3: Replace demo fallback with explicit route-state handling in the web app

**Required skills before starting this task:**
- `/frontend/.agent/skills/react/SKILL.md`
- `/frontend/.agent/skills/frontend-design/SKILL.md`

**Files:**
- Create: `frontend/web-app/src/components/coupon/CouponUnavailableState.tsx`
- Modify: `frontend/web-app/src/pages/CouponDetailPage.tsx`
- Modify: `frontend/web-app/src/pages/CouponDetailPage.css`

- [ ] **Step 1: Create a dedicated unavailable-state component**

Create `CouponUnavailableState.tsx`:

```tsx
import { Link } from 'react-router-dom';
import { ArrowLeft, SearchX } from 'lucide-react';
import { useLocalePath } from '../../hooks/useLocalePath';

interface CouponUnavailableStateProps {
  title?: string;
  description?: string;
}

export default function CouponUnavailableState({
  title = 'Купон недоступен',
  description = 'Этот купон больше не доступен публично или ссылка устарела.',
}: CouponUnavailableStateProps) {
  const lp = useLocalePath();

  return (
    <div className="coupon-unavailable">
      <div className="coupon-unavailable__card">
        <div className="coupon-unavailable__icon">
          <SearchX size={34} />
        </div>
        <h1>{title}</h1>
        <p>{description}</p>
        <div className="coupon-unavailable__actions">
          <Link to={lp('/coupons')} className="coupon-unavailable__primary">
            Смотреть активные купоны
          </Link>
          <Link to={lp('/')} className="coupon-unavailable__secondary">
            <ArrowLeft size={16} />
            На главную
          </Link>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Add styles for a controlled unavailable state**

Append to `CouponDetailPage.css`:

```css
.coupon-unavailable {
  min-height: 70vh;
  display: grid;
  place-items: center;
  padding: 48px 20px;
}

.coupon-unavailable__card {
  width: min(560px, 100%);
  padding: 32px;
  border-radius: 28px;
  background: linear-gradient(180deg, #fffdf8 0%, #fff8ef 100%);
  border: 1px solid rgba(210, 166, 121, 0.22);
  box-shadow: 0 20px 60px rgba(117, 74, 28, 0.10);
  text-align: center;
}

.coupon-unavailable__icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 18px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: rgba(201, 104, 39, 0.10);
  color: #b65b25;
}

.coupon-unavailable__card h1 {
  margin: 0 0 12px;
}

.coupon-unavailable__card p {
  margin: 0 0 24px;
  color: rgba(52, 37, 26, 0.74);
}

.coupon-unavailable__actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}
```

- [ ] **Step 3: Refactor `CouponDetailPage` to use explicit query states**

Apply these changes:

1. Remove runtime fallback `const c = coupon || DEMO_COUPON;`
2. Extend query destructuring:

```tsx
const {
  data: coupon,
  isLoading,
  isError,
  error,
} = useQuery({
  queryKey: ['coupon', id],
  queryFn: () => couponsApi.getById(Number(id)),
  select: (res) => res.data.data,
  enabled: !!id,
  retry: false,
});
```

3. Use three route states before rendering the detail:

```tsx
if (isLoading) {
  return (
    <div className="detail-page">
      <div className="container" style={{ padding: '64px 0', textAlign: 'center' }}>
        Загрузка купона...
      </div>
    </div>
  );
}

if (isError || !coupon) {
  return (
    <div className="detail-page">
      <CouponUnavailableState />
    </div>
  );
}

const c = coupon;
```

4. Keep `DEMO_COUPON` only if you still need it for local isolated preview development. If it remains in file, it must no longer be part of runtime route fallback.

- [ ] **Step 4: Build the web app**

Run:

```bash
npm run build
```

Workdir: `frontend/web-app`

Expected: PASS.

- [ ] **Step 5: Manual smoke for direct-link behavior**

Check:

1. existing `ACTIVE` coupon still renders full detail page;
2. missing coupon id renders controlled unavailable state;
3. non-public coupon id renders controlled unavailable state;
4. no demo coupon content appears on failed requests.

- [ ] **Step 6: Commit**

```bash
git add frontend/web-app/src/components/coupon/CouponUnavailableState.tsx \
        frontend/web-app/src/pages/CouponDetailPage.tsx \
        frontend/web-app/src/pages/CouponDetailPage.css
git commit -m "feat: add controlled unavailable state for public coupon detail"
```

---

### Task 4: Final regression verification and PRD alignment check

**Required skills before starting this task:**
- `/.agent/skills/verification-before-completion/SKILL.md`

**Files:**
- Review only: `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`

- [ ] **Step 1: Verify PRD rule and implementation stay aligned**

Confirm the implemented behavior still matches:

- `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md:1792`
- direct links on non-public coupon statuses must show a controlled unavailable state.

No document edit is required unless implementation semantics change away from `404 + unavailable state`.

- [ ] **Step 2: Run complete targeted backend verification**

Run from repo root:

```bash
./gradlew :services:coupon-service:test \
  --tests "uz.topdim.coupon.service.CouponOfferServiceTest" \
  --tests "uz.topdim.coupon.controller.CouponPublicVisibilityTest" \
  --tests "uz.topdim.coupon.controller.CouponFlowExceptionMappingTest" \
  --tests "uz.topdim.coupon.controller.CouponOfferControllerValidationTest" \
  -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 3: Run complete targeted frontend verification**

Run:

```bash
npm exec -- tsc -b
npm exec -- vite build
```

Workdir: `frontend/admin-app`

Then:

```bash
npm run build
```

Workdir: `frontend/web-app`

Expected: PASS.

- [ ] **Step 4: Commit final verification-only if docs or ancillary text changed**

```bash
git status --short
```

If only code commits from Tasks 1-3 exist and no new tracked changes are needed, do not add an extra commit.

---

## Definition of Done

- public coupon detail is visible only for `ACTIVE` coupons;
- non-public and missing coupons produce the same public outcome: unavailable;
- public API semantics are locked with service and controller tests;
- frontend no longer falls back to demo content on failed real requests;
- PRD rule `BR-C-002` is satisfied by actual runtime behavior.

## What stays for later

- Redis cache restore for catalog and top-selling;
- broader merchant-module work outside coupon dependency;
- bazaar/directory migration;
- any additional storefront polish beyond the unavailable-state experience.
