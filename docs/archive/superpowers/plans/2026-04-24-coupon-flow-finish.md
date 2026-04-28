# Coupon Flow Finish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** finish the coupon creation-to-publication flow, make lifecycle failures visible and actionable, and harden the merchant dependency only where it directly protects coupon behavior.

**Architecture:** keep `coupon` as the primary domain for this iteration. `merchant` is not a standalone workstream here; it is only a dependency that must remain publication-ready for coupon moderation and publication. Reuse the existing canonical model (`offerDescription`, `merchant.primaryLocation`) and avoid reopening DTO/schema cleanup or bazaar migration.

**Tech Stack:** Spring Boot, JPA, JUnit 5, Mockito, MockMvc standalone setup, React 19, TypeScript, Ant Design, Vite

---

## Scope and Non-Goals

### In scope

- coupon create/edit/take-to-work/send-to-approval/approve/revision flows;
- admin UX around coupon publication readiness;
- backend error semantics for coupon business-rule failures;
- merchant changes only when they prevent coupon flow regressions.

### Explicit non-goals

- no standalone merchant-module expansion;
- no bazaar/directory work;
- no new frontend test infrastructure;
- no schema migrations or new contract cleanup pass;
- no redesign of admin/storefront beyond readiness UX.

### Required superpowers workflow

1. Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to run tasks in order.
2. For every backend change, use `superpowers:test-driven-development`.
3. If a test fails for an unexpected reason, use `superpowers:systematic-debugging` before changing code.
4. Before claiming success, use `superpowers:verification-before-completion`.

---

### Task 1: Return business-rule failures as actionable API responses

**Why first:** coupon publication already has backend guards, but today `IllegalStateException`/`IllegalArgumentException` fall through the generic handler and surface to UI as `500 Внутренняя ошибка сервера`. That hides the real reason from moderator and bot flows.

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/exception/GlobalExceptionHandler.java`
- Create: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/CouponFlowExceptionMappingTest.java`

- [ ] **Step 1: Write the failing controller tests for `IllegalStateException -> 409` and `IllegalArgumentException -> 400`**

```java
@Test
@DisplayName("Mod approve: publication guard failure returns 409 with business message")
void modApprove_publicationGuardFailure_returnsConflict() throws Exception {
    doThrow(new IllegalStateException("Нельзя публиковать купон без active primary location у мерчанта"))
            .when(modCouponService).reviewCoupon(7L, 12L, "APPROVE", null);

    mockMvc.perform(patch("/api/v1/mod/coupons/12/review")
                    .header("X-User-Id", 7L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"status":"APPROVE"}
                            """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message")
                    .value("Нельзя публиковать купон без active primary location у мерчанта"));
}

@Test
@DisplayName("Admin merchant update: invalid location payload returns 400 with business message")
void adminMerchantUpdate_invalidLocationPayload_returnsBadRequest() throws Exception {
    when(merchantService.updateMerchant(eq(5L), any()))
            .thenThrow(new IllegalArgumentException("У мерчанта может быть только одна primary location"));

    mockMvc.perform(put("/api/v1/admin/merchants/5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"SPA Oasis","locations":[{"address":"ул. 1","primary":true},{"address":"ул. 2","primary":true}]}
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message")
                    .value("У мерчанта может быть только одна primary location"));
}
```

- [ ] **Step 2: Run the new controller tests and confirm they fail because the handler still returns generic 500**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.CouponFlowExceptionMappingTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL. Current `GlobalExceptionHandler` maps these exceptions to the generic `Exception` branch.

- [ ] **Step 3: Implement explicit business-exception mappings**

Add this to `GlobalExceptionHandler` before the generic `Exception` handler:

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
}

@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage()));
}
```

- [ ] **Step 4: Re-run the controller tests**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.CouponFlowExceptionMappingTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/exception/GlobalExceptionHandler.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/controller/CouponFlowExceptionMappingTest.java
git commit -m "fix: expose coupon flow business errors to clients"
```

---

### Task 2: Surface publication readiness in admin coupon flow

**Why now:** after Task 1, failures become visible. This task makes them predictable before the moderator clicks approve. This is still coupon work, not standalone merchant development.

**Files:**
- Create: `frontend/admin-app/src/features/coupons/publicationReadiness.ts`
- Modify: `frontend/admin-app/src/features/coupons/CouponFormPage.tsx`
- Modify: `frontend/admin-app/src/features/coupons/CouponKanbanPage.tsx`
- Modify: `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx`

- [ ] **Step 1: Create one shared admin helper for readiness computation**

Create `publicationReadiness.ts`:

```ts
export interface MerchantPublicationLocation {
  address?: string;
  active?: boolean;
}

export interface MerchantPublicationSummary {
  id?: number;
  name?: string;
  primaryLocation?: MerchantPublicationLocation | null;
}

export interface PublicationReadinessResult {
  ready: boolean;
  reasons: string[];
}

export function getPublicationReadiness(
  merchant?: MerchantPublicationSummary | null,
): PublicationReadinessResult {
  const reasons: string[] = [];

  if (!merchant) {
    reasons.push('У купона не выбран мерчант');
  } else if (!merchant.primaryLocation) {
    reasons.push('У мерчанта нет primary location');
  } else {
    if (merchant.primaryLocation.active === false) {
      reasons.push('Primary location мерчанта неактивен');
    }
    if (!merchant.primaryLocation.address?.trim()) {
      reasons.push('В primary location не заполнен адрес');
    }
  }

  return { ready: reasons.length === 0, reasons };
}
```

- [ ] **Step 2: Use the helper in `CouponFormPage` and show readiness for the selected merchant**

Add typed merchant shape for the `/api/v1/admin/merchants` query and compute the selected merchant:

```ts
interface MerchantOption {
  id: number;
  name: string;
  primaryLocation?: {
    address?: string;
    active?: boolean;
  } | null;
}

const selectedMerchantId = Form.useWatch('merchantId', form);
const selectedMerchant = merchants?.find((m: MerchantOption) => m.id === selectedMerchantId);
const readiness = getPublicationReadiness(selectedMerchant);
```

Render an informational alert under the merchant selector:

```tsx
{selectedMerchant && (
  <Alert
    type={readiness.ready ? 'success' : 'warning'}
    showIcon
    message={readiness.ready ? 'Мерчант готов к публикации купона' : 'Мерчант ещё не готов к публикации'}
    description={
      readiness.ready ? 'У primary location заполнен адрес, approve-path не будет заблокирован.' : (
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          {readiness.reasons.map((reason) => <li key={reason}>{reason}</li>)}
        </ul>
      )
    }
    style={{ marginTop: 8 }}
  />
)}
```

Do **not** block coupon creation or update on this screen. This task is UX guidance only.

- [ ] **Step 3: Use the helper in `CouponKanbanPage` to warn before sending to merchant**

Extend the local `Coupon` type:

```ts
interface MerchantSummary {
  id: number;
  name: string;
  primaryLocation?: {
    address?: string;
    active?: boolean;
  } | null;
}
```

For cards in `DRAFT` and `REVISION_REQUESTED`, show a readiness tag:

```tsx
const readiness = getPublicationReadiness(coupon.merchant);

{!readiness.ready && (
  <Alert
    type="warning"
    showIcon
    message="Не готов к публикации"
    description={readiness.reasons.join('. ')}
    style={{ marginBottom: 12 }}
  />
)}
```

Keep `sendToApproval` enabled. Showing the warning is enough because business blocking happens only on `ACTIVE`.

- [ ] **Step 4: Use the helper in `MerchantReviewPage` and disable approve when not ready**

Compute readiness from `previewCoupon.merchant` and render a blocking warning near the footer:

```tsx
const readiness = getPublicationReadiness(previewCoupon?.merchant);
```

Render:

```tsx
{previewCoupon && !readiness.ready && (
  <Alert
    type="error"
    showIcon
    message="Купон нельзя публиковать"
    description={
      <ul style={{ margin: 0, paddingLeft: 18 }}>
        {readiness.reasons.map((reason) => <li key={reason}>{reason}</li>)}
      </ul>
    }
    style={{ marginBottom: 16 }}
  />
)}
```

Disable approve buttons:

```tsx
<Button
  type="primary"
  disabled={!readiness.ready}
  onClick={() => handleApprove(previewCoupon.id, previewCoupon.title)}
>
  Одобрить → ACTIVE
</Button>
```

- [ ] **Step 5: Build the admin app**

Run:

```bash
npm exec -- tsc -b
npm exec -- vite build
```

Workdir: `frontend/admin-app`

Expected: PASS.

- [ ] **Step 6: Manual smoke for admin UX**

Check:

1. coupon form shows success alert for a merchant with publication-ready primary location;
2. coupon form shows warning reasons for a merchant without address;
3. kanban card keeps `send-to-approval` enabled but warns when merchant is not publication-ready;
4. merchant review modal disables approve until readiness is satisfied.

- [ ] **Step 7: Commit**

```bash
git add frontend/admin-app/src/features/coupons/publicationReadiness.ts \
        frontend/admin-app/src/features/coupons/CouponFormPage.tsx \
        frontend/admin-app/src/features/coupons/CouponKanbanPage.tsx \
        frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx
git commit -m "feat: surface coupon publication readiness in admin flow"
```

---

### Task 3: Prevent merchant edits from silently breaking coupon publication

**Why this is still in scope:** this is not “merchant module work”. It protects coupon flow from a destructive dependency update. Today `updateMerchant()` can clear all locations if `locations` is omitted or intentionally sent empty.

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferRepository.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java`

- [ ] **Step 1: Write the failing test for `null locations` preserving existing locations**

```java
@Test
@DisplayName("updateMerchant: null locations preserves existing locations")
void updateMerchant_nullLocations_preservesExistingLocations() {
    Merchant existing = createTestMerchant();
    when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of(
            MerchantLocation.builder()
                    .id(10L)
                    .merchant(existing)
                    .address("Ташкент, ул. Нукус, 10")
                    .primary(true)
                    .active(true)
                    .build()
    ));

    CreateMerchantRequest request = new CreateMerchantRequest();
    request.setName("Updated SPA");
    request.setDescription("Новое описание");
    request.setLocations(null);

    MerchantResponse result = merchantService.updateMerchant(1L, request);

    assertThat(result.getPrimaryLocation()).isNotNull();
    verify(merchantLocationRepository, never()).deleteAllByMerchantId(1L);
    verify(merchantLocationRepository, never()).save(any());
}
```

- [ ] **Step 2: Write the failing test for `empty locations` with publication-dependent coupons**

```java
@Test
@DisplayName("updateMerchant: empty locations with ACTIVE/WAITING coupons is rejected")
void updateMerchant_emptyLocationsWithPublicationDependentCoupons_throws() {
    Merchant existing = createTestMerchant();
    when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(couponOfferRepository.existsByMerchantIdAndStatusIn(
            eq(1L),
            eq(List.of(CouponStatus.WAITING_FOR_MERCHANT, CouponStatus.ACTIVE))
    )).thenReturn(true);

    CreateMerchantRequest request = new CreateMerchantRequest();
    request.setName("Updated SPA");
    request.setLocations(List.of());

    assertThatThrownBy(() -> merchantService.updateMerchant(1L, request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Нельзя удалить все locations");

    verify(merchantLocationRepository, never()).deleteAllByMerchantId(anyLong());
}
```

- [ ] **Step 3: Run the merchant tests and confirm they fail**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.MerchantServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because `updateMerchant` still always replaces all locations.

- [ ] **Step 4: Add repository support for publication-dependent coupon lookup**

Add to `CouponOfferRepository`:

```java
boolean existsByMerchantIdAndStatusIn(Long merchantId, List<CouponStatus> statuses);
```

- [ ] **Step 5: Implement safe update semantics in `MerchantService`**

Implementation rules:

1. `request.locations == null` on update means “leave existing locations unchanged”;
2. `request.locations = []` means explicit clear attempt;
3. explicit clear is rejected if merchant has `WAITING_FOR_MERCHANT` or `ACTIVE` coupons;
4. create flow keeps current behavior: empty or null locations are allowed.

Suggested shape:

```java
private void saveLocationsForUpdate(Merchant merchant, CreateMerchantRequest request) {
    if (request.getLocations() == null) {
        return;
    }

    List<MerchantLocation> normalizedLocations = buildLocations(merchant, request);
    if (normalizedLocations.isEmpty() && hasPublicationDependentCoupons(merchant.getId())) {
        throw new IllegalStateException(
                "Нельзя удалить все locations у мерчанта с WAITING_FOR_MERCHANT или ACTIVE купонами");
    }

    merchantLocationRepository.deleteAllByMerchantId(merchant.getId());
    merchant.getLocations().clear();
    normalizedLocations.forEach(merchantLocationRepository::save);
}

private boolean hasPublicationDependentCoupons(Long merchantId) {
    return couponOfferRepository.existsByMerchantIdAndStatusIn(
            merchantId,
            List.of(CouponStatus.WAITING_FOR_MERCHANT, CouponStatus.ACTIVE)
    );
}
```

Call `saveLocationsForUpdate(...)` from `updateMerchant(...)`. Keep `createMerchant(...)` on the existing create-only path.

- [ ] **Step 6: Add the positive regression for explicit clear when no dependent coupons exist**

```java
@Test
@DisplayName("updateMerchant: empty locations without dependent coupons clears locations")
void updateMerchant_emptyLocationsWithoutDependentCoupons_clearsLocations() {
    Merchant existing = createTestMerchant();
    when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(couponOfferRepository.existsByMerchantIdAndStatusIn(
            eq(1L),
            eq(List.of(CouponStatus.WAITING_FOR_MERCHANT, CouponStatus.ACTIVE))
    )).thenReturn(false);
    when(merchantLocationRepository.findByMerchantIdAndActiveTrue(1L)).thenReturn(List.of());

    CreateMerchantRequest request = new CreateMerchantRequest();
    request.setName("Updated SPA");
    request.setLocations(List.of());

    merchantService.updateMerchant(1L, request);

    verify(merchantLocationRepository).deleteAllByMerchantId(1L);
}
```

- [ ] **Step 7: Re-run merchant tests**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.MerchantServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java \
        services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferRepository.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java
git commit -m "fix: protect coupon flow from destructive merchant location updates"
```

---

### Task 4: Add final coupon-flow regressions and sync docs

**Why final:** once API error semantics, admin readiness UX, and dependency hardening are in place, close the loop with regression coverage and docs so the next worker does not fall back to outdated lifecycle assumptions.

**Files:**
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/ModCouponServiceTest.java`
- Create: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/BotWebhookControllerApprovalTest.java`
- Modify: `docs/COUPON_CREATION_FLOW.md`
- Modify: `docs/COUPON_FLOW.md`
- Modify: `docs/BACKEND.md`

- [ ] **Step 1: Add the failing regression for moderator approve failure not sending success notification**

```java
@Test
@DisplayName("reviewCoupon: approve failure does not send success notification")
void reviewCoupon_approveFailure_doesNotSendNotification() {
    doThrow(new IllegalStateException("Нельзя публиковать купон без active primary location у мерчанта"))
            .when(couponOfferService).approveByMerchant(10L);

    assertThatThrownBy(() -> modCouponService.reviewCoupon(3L, 10L, "APPROVE", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("primary location");

    verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(NotificationEvent.class));
}
```

- [ ] **Step 2: Add the failing controller regression for bot approve path surfacing 409**

```java
@Test
@DisplayName("Bot approve: publication guard failure returns 409 with business message")
void botApprove_publicationGuardFailure_returnsConflict() throws Exception {
    when(couponOfferService.approveByMerchant(12L))
            .thenThrow(new IllegalStateException("Нельзя публиковать купон без адреса в primary location мерчанта"));

    mockMvc.perform(post("/api/v1/bot/coupons/12/approve"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message")
                    .value("Нельзя публиковать купон без адреса в primary location мерчанта"));
}
```

- [ ] **Step 3: Run the focused regression tests**

Run:

```bash
./gradlew :services:coupon-service:test \
  --tests "uz.topdim.coupon.service.ModCouponServiceTest" \
  --tests "uz.topdim.coupon.controller.BotWebhookControllerApprovalTest" \
  -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL before test additions are satisfied; PASS after implementation.

- [ ] **Step 4: Update lifecycle docs to match the real coupon flow**

Update these truths in:

- `docs/COUPON_CREATION_FLOW.md`
- `docs/COUPON_FLOW.md`
- `docs/BACKEND.md`

Required wording:

1. coupon admin/partner creation starts as `LEAD`, not directly `ACTIVE`;
2. actual lifecycle is `LEAD -> DRAFT -> WAITING_FOR_MERCHANT -> ACTIVE` with `REVISION_REQUESTED` side path;
3. `ACTIVE` requires a publication-ready merchant;
4. publication does not auto-fill merchant data.

- [ ] **Step 5: Run the complete targeted verification set**

Run from repo root:

```bash
./gradlew :services:coupon-service:test \
  --tests "uz.topdim.coupon.controller.CouponFlowExceptionMappingTest" \
  --tests "uz.topdim.coupon.controller.CouponOfferControllerValidationTest" \
  --tests "uz.topdim.coupon.controller.BotWebhookControllerApprovalTest" \
  --tests "uz.topdim.coupon.service.CouponOfferServiceTest" \
  --tests "uz.topdim.coupon.service.CouponOfferServiceBusinessLogicTest" \
  --tests "uz.topdim.coupon.service.MerchantServiceTest" \
  --tests "uz.topdim.coupon.service.ModCouponServiceTest" \
  -x jacocoTestReport -x jacocoTestCoverageVerification
```

Then run frontend build verification:

```bash
npm exec -- tsc -b
npm exec -- vite build
```

Workdir: `frontend/admin-app`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/coupon-service/src/test/java/uz/topdim/coupon/service/ModCouponServiceTest.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/controller/BotWebhookControllerApprovalTest.java \
        docs/COUPON_CREATION_FLOW.md \
        docs/COUPON_FLOW.md \
        docs/BACKEND.md
git commit -m "test: lock coupon flow approval regressions"
```

---

## Definition of Done for this plan

- moderator and bot approve paths return business-meaningful errors instead of generic `500`;
- admin coupon UI shows publication readiness before approve;
- merchant profile edits can no longer silently invalidate coupon publication for live/waiting coupons;
- regression tests cover service and entry-point coupon lifecycle behavior;
- lifecycle docs match the real system behavior.

## What stays for later

- Redis cache restore for catalog and top-selling;
- full standalone merchant-module work;
- bazaar/directory migration;
- any new contract cleanup pass beyond the already completed canonical cleanup.
