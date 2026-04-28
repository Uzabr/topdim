# Coupon Archive And Active Immutability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make published coupon offers immutable in MVP and add a safe archive action that stops future sales without cancelling already purchased coupons.

**Architecture:** Extend the coupon-service offer state machine with terminal `ARCHIVED`. Keep public catalog/detail and order purchase validation dependent on `ACTIVE` only. Add a dedicated admin archive endpoint with a required reason, then update admin-app actions so staff cannot edit active offers and can archive only `ACTIVE` or `SOLD_OUT` offers.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, Flyway, JUnit 5, Mockito, React 19, TanStack Query, Ant Design, Vite.

---

## Required Local Skills

The implementing AI must read and follow the skills that match the touched area:

- Root process:
  - `.agent/skills/systematic-debugging/SKILL.md`
  - `.agent/skills/test-driven-development/SKILL.md`
  - `.agent/skills/verification-before-completion/SKILL.md`
- Services/backend:
  - `services/.agent/skills/api-design-principles/SKILL.md`
  - `services/.agent/skills/spring-boot-crud-patterns/SKILL.md`
  - `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
  - `services/.agent/skills/database-schema-designer/SKILL.md`
- Frontend/admin UI:
  - `frontend/.agent/skills/react/SKILL.md`
  - `frontend/.agent/skills/frontend-design/SKILL.md`

## Business Rules

- `ACTIVE` coupon offers are public and purchasable; they must be read-only in MVP.
- `SOLD_OUT` coupon offers are also read-only; they may be archived for operational cleanup.
- `ARCHIVED` means future sales are stopped and the offer is hidden from public catalog/detail.
- `ARCHIVED` is terminal. Do not support `ARCHIVED -> ACTIVE`, `ARCHIVED -> DRAFT`, or unarchive in MVP.
- Archiving an offer must not cancel, expire, refund, or mutate existing `PurchasedCoupon` rows.
- Staff must provide an archive reason.
- Generic status endpoint must not allow setting `ARCHIVED`; use the dedicated archive endpoint.
- Public catalog/detail stays `ACTIVE` only. Because `ARCHIVED` is not `ACTIVE`, it must not appear publicly.
- Checkout/order-service purchase validation already rejects non-`ACTIVE`; keep that behavior.

## Files

- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponStatus.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponOffer.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/ArchiveCouponRequest.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponOfferResponse.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java`
- Create: `services/coupon-service/src/main/resources/db/migration/V15__coupon_archive_fields.sql`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceBusinessLogicTest.java`
- Modify: `frontend/admin-app/src/features/coupons/CouponsListPage.tsx`
- Modify: `frontend/admin-app/src/features/coupons/CouponKanbanPage.tsx`
- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/COUPON_FLOW.md`
- Modify: `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`

Do not modify order-service refund/purchased-coupon behavior for this task.

---

### Task 1: Write Backend State-Machine Tests First

**Files:**
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceBusinessLogicTest.java`

- [ ] **Step 1: Replace the active-update test with a failing immutable-active test**

In `CouponOfferServiceTest`, replace the current test named `update_fromActive_allowed` with:

```java
@Test
@DisplayName("Update: из ACTIVE — запрещено, опубликованный купон immutable")
void update_fromActive_throws() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.ACTIVE);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    CreateCouponOfferRequest req = new CreateCouponOfferRequest();
    req.setTitle("Updated");
    req.setOfferDescription("Updated description");
    req.setMerchantId(1L);
    req.setCategoryId(1L);
    req.setFromPrice(offer.getFromPrice());

    assertThatThrownBy(() -> couponOfferService.update(1L, req, 100L, "ADMIN"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Редактирование запрещено");

    verify(couponOfferRepository, never()).save(any());
}
```

- [ ] **Step 2: Add SOLD_OUT and ARCHIVED update protection tests**

Add these tests under the same update restrictions section:

```java
@Test
@DisplayName("Update: из SOLD_OUT — запрещено")
void update_fromSoldOut_throws() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.SOLD_OUT);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.update(1L, new CreateCouponOfferRequest(), 100L, "ADMIN"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Редактирование запрещено");

    verify(couponOfferRepository, never()).save(any());
}

@Test
@DisplayName("Update: из ARCHIVED — запрещено")
void update_fromArchived_throws() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.ARCHIVED);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.update(1L, new CreateCouponOfferRequest(), 100L, "ADMIN"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Редактирование запрещено");

    verify(couponOfferRepository, never()).save(any());
}
```

- [ ] **Step 3: Add archive transition tests**

Add these tests under a new section `// ==================== Archive ====================`:

```java
@Test
@DisplayName("Archive: ACTIVE → ARCHIVED сохраняет причину и дату")
void archive_fromActive_setsArchivedReasonAndArchivedAt() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.ACTIVE);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
    when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CouponOfferResponse result = couponOfferService.archive(1L, "Ошибка в условиях акции");

    assertThat(result.getStatus()).isEqualTo("ARCHIVED");
    assertThat(result.getArchiveReason()).isEqualTo("Ошибка в условиях акции");
    assertThat(result.getArchivedAt()).isNotNull();
    assertThat(offer.getStatus()).isEqualTo(CouponStatus.ARCHIVED);
    assertThat(offer.getArchiveReason()).isEqualTo("Ошибка в условиях акции");
    assertThat(offer.getArchivedAt()).isNotNull();
}

@Test
@DisplayName("Archive: SOLD_OUT → ARCHIVED разрешён")
void archive_fromSoldOut_allowed() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.SOLD_OUT);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
    when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CouponOfferResponse result = couponOfferService.archive(1L, "Оффер больше не актуален");

    assertThat(result.getStatus()).isEqualTo("ARCHIVED");
    assertThat(result.getArchiveReason()).isEqualTo("Оффер больше не актуален");
}

@Test
@DisplayName("Archive: DRAFT запрещён")
void archive_fromDraft_throws() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.DRAFT);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.archive(1L, "Не нужен"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Архивирование запрещено");

    verify(couponOfferRepository, never()).save(any());
}

@Test
@DisplayName("Archive: ARCHIVED повторно запрещён")
void archive_fromArchived_throws() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.ARCHIVED);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.archive(1L, "Повторно"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Архивирование запрещено");

    verify(couponOfferRepository, never()).save(any());
}

@Test
@DisplayName("Archive: пустая причина запрещена")
void archive_blankReason_throws() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.ACTIVE);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.archive(1L, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Причина архивирования обязательна");

    verify(couponOfferRepository, never()).save(any());
}
```

- [ ] **Step 4: Add delete protection for ARCHIVED and SOLD_OUT**

Add:

```java
@Test
@DisplayName("Delete: ARCHIVED — запрещено")
void delete_archived_throws() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.ARCHIVED);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.delete(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Удаление запрещено");
}

@Test
@DisplayName("Delete: SOLD_OUT — запрещено")
void delete_soldOut_throws() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.SOLD_OUT);
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.delete(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Удаление запрещено");
}
```

- [ ] **Step 5: Add generic-status guard in business logic tests**

In `CouponOfferServiceBusinessLogicTest`, add:

```java
@Test
@DisplayName("updateStatus: ACTIVE -> ARCHIVED через generic status endpoint запрещён")
void updateStatus_activeToArchived_throws() {
    CouponOffer offer = createOffer(CouponStatus.ACTIVE);
    when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.updateStatus(10L, CouponStatus.ARCHIVED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("запрещён");
}
```

- [ ] **Step 6: Run tests and confirm RED**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.CouponOfferServiceBusinessLogicTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because `CouponStatus.ARCHIVED`, `CouponOfferService.archive`, and response archive fields do not exist yet. If it passes, the test is not proving the new behavior.

---

### Task 2: Add Backend Domain Model And Migration

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponStatus.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/entity/CouponOffer.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/ArchiveCouponRequest.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponOfferResponse.java`
- Create: `services/coupon-service/src/main/resources/db/migration/V15__coupon_archive_fields.sql`

- [ ] **Step 1: Add `ARCHIVED` to the offer enum**

Update `CouponStatus.java`:

```java
/** Архивирован staff-ом: скрыт из продаж, купленные купоны не меняются. */
ARCHIVED
```

Keep `SOLD_OUT` before `ARCHIVED`.

- [ ] **Step 2: Add archive fields to `CouponOffer`**

In `CouponOffer.java`, add:

```java
@Column(name = "archive_reason", columnDefinition = "TEXT")
private String archiveReason;

@Column(name = "archived_at")
private LocalDateTime archivedAt;
```

- [ ] **Step 3: Create archive request DTO**

Create `ArchiveCouponRequest.java`:

```java
package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ArchiveCouponRequest {

    @NotBlank(message = "Причина архивирования обязательна")
    @Size(max = 1000, message = "Причина архивирования не должна превышать 1000 символов")
    private String reason;
}
```

- [ ] **Step 4: Add archive fields to response DTO**

In `CouponOfferResponse.java`, add near `revisionComment`:

```java
private String archiveReason;
private LocalDateTime archivedAt;
```

- [ ] **Step 5: Add Flyway migration**

Create `V15__coupon_archive_fields.sql`:

```sql
-- V15: terminal archive metadata for coupon offers.
-- ARCHIVED stops future sales but does not mutate purchased coupons.

ALTER TABLE coupon_offers
    ADD COLUMN IF NOT EXISTS archive_reason TEXT,
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;
```

- [ ] **Step 6: Run backend compile**

Run:

```bash
./gradlew :services:coupon-service:compileJava
```

Expected: FAIL with a Java compile error because `CouponStatus.ARCHIVED` is not handled in the existing switch expression. Continue to Task 3 to add the missing state-machine branch.

---

### Task 3: Implement Archive Service And Admin Endpoint

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/AdminCouponController.java`

- [ ] **Step 1: Import `LocalDateTime`**

In `CouponOfferService.java`, ensure imports include:

```java
import java.time.LocalDateTime;
```

- [ ] **Step 2: Make update allowed only for draft-like statuses**

Replace the update status guard with:

```java
if (offer.getStatus() != CouponStatus.DRAFT
        && offer.getStatus() != CouponStatus.REVISION_REQUESTED) {
    throw new IllegalStateException(
            "Редактирование запрещено из статуса " + offer.getStatus()
            + ". Допустимые: DRAFT, REVISION_REQUESTED");
}
```

Also update the method comment so it says `Разрешено из статусов DRAFT, REVISION_REQUESTED`.

- [ ] **Step 3: Keep generic status endpoint from setting ARCHIVED**

In `updateStatus`, add `ARCHIVED` to the switch with `false`:

```java
case ARCHIVED -> false; // Используйте dedicated archive endpoint with reason.
```

Update the error message:

```java
+ "Допустимые: LEAD→DRAFT, DRAFT/REVISION→WAITING, WAITING→ACTIVE/REVISION. "
+ "Для ACTIVE/SOLD_OUT используйте archive endpoint");
```

- [ ] **Step 4: Add dedicated archive method**

Add this method in `CouponOfferService.java` after `updateStatus` or before `delete`:

```java
/**
 * Архивирует опубликованный или распроданный купон.
 * Останавливает будущие продажи, но не меняет уже купленные купоны.
 */
@Caching(evict = {
        @CacheEvict(value = "catalog", allEntries = true),
        @CacheEvict(value = "topSelling", allEntries = true),
        @CacheEvict(value = "couponDetail", key = "#id")
})
@Transactional
public CouponOfferResponse archive(Long id, String reason) {
    CouponOffer offer = couponOfferRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

    String trimmedReason = reason == null ? "" : reason.trim();
    if (trimmedReason.isBlank()) {
        throw new IllegalArgumentException("Причина архивирования обязательна");
    }

    if (offer.getStatus() != CouponStatus.ACTIVE && offer.getStatus() != CouponStatus.SOLD_OUT) {
        throw new IllegalStateException(
                "Архивирование запрещено из статуса " + offer.getStatus()
                + ". Допустимые: ACTIVE, SOLD_OUT");
    }

    offer.setStatus(CouponStatus.ARCHIVED);
    offer.setArchiveReason(trimmedReason);
    offer.setArchivedAt(LocalDateTime.now());

    log.info("Купон #{} архивирован. Причина: {}", id, trimmedReason);
    return mapToResponse(couponOfferRepository.save(offer));
}
```

- [ ] **Step 5: Make delete allowed only for pre-public statuses**

Replace the delete guard with:

```java
if (offer.getStatus() != CouponStatus.LEAD
        && offer.getStatus() != CouponStatus.DRAFT
        && offer.getStatus() != CouponStatus.REVISION_REQUESTED) {
    throw new IllegalStateException(
            "Удаление запрещено из статуса " + offer.getStatus()
            + ". Допустимые для удаления: LEAD, DRAFT, REVISION_REQUESTED");
}
```

- [ ] **Step 6: Map archive fields into response**

In `mapToResponse`, add:

```java
.archiveReason(offer.getArchiveReason())
.archivedAt(offer.getArchivedAt())
```

Place them immediately after `.revisionComment(offer.getRevisionComment())`.

- [ ] **Step 7: Add controller endpoint**

In `AdminCouponController.java`, add after `updateCouponStatus` or before `deleteCoupon`:

```java
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
@PostMapping("/coupons/{id}/archive")
public ResponseEntity<ApiResponse<CouponOfferResponse>> archiveCoupon(
        @PathVariable Long id,
        @Valid @RequestBody ArchiveCouponRequest request
) {
    return ResponseEntity.ok(ApiResponse.success(
            "Купон снят с публикации", couponOfferService.archive(id, request.getReason())));
}
```

- [ ] **Step 8: Run service tests and confirm GREEN**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.CouponOfferServiceBusinessLogicTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

---

### Task 4: Update Admin UI Actions

**Files:**
- Modify: `frontend/admin-app/src/features/coupons/CouponsListPage.tsx`
- Modify: `frontend/admin-app/src/features/coupons/CouponKanbanPage.tsx`

- [ ] **Step 1: Update `CouponsListPage` imports**

Change imports:

```ts
import { Table, Tag, Button, Space, Typography, Popconfirm, message, Modal, Input } from 'antd';
import { EditOutlined, DeleteOutlined, PlusOutlined, StopOutlined } from '@ant-design/icons';
```

- [ ] **Step 2: Add a typed coupon row**

Add near the top of `CouponsListPage.tsx`:

```ts
type CouponStatus =
  | 'LEAD'
  | 'DRAFT'
  | 'WAITING_FOR_MERCHANT'
  | 'REVISION_REQUESTED'
  | 'ACTIVE'
  | 'SOLD_OUT'
  | 'ARCHIVED';

interface CouponRow {
  id: number;
  title: string;
  oldPrice?: number | null;
  fromPrice: number;
  discountPercent?: number | null;
  status: CouponStatus;
  createdAt: string;
  archiveReason?: string | null;
  archivedAt?: string | null;
}
```

Change the query type:

```ts
const res = await api.get<ApiResponse<PageResponse<CouponRow>>>(
```

Change columns type:

```ts
const columns: ColumnsType<CouponRow> = [
```

- [ ] **Step 3: Add archive mutation**

Add after delete mutation:

```ts
const archiveMutation = useMutation({
  mutationFn: ({ id, reason }: { id: number; reason: string }) =>
    api.post(`/api/v1/admin/coupons/${id}/archive`, { reason }),
  onSuccess: () => {
    message.success('Купон снят с публикации');
    queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
  },
  onError: () => {
    message.error('Не удалось снять купон с публикации');
  },
});
```

- [ ] **Step 4: Add archive modal helper**

Add before `columns`:

```tsx
const openArchiveModal = (record: CouponRow) => {
  let reason = '';

  Modal.confirm({
    title: 'Снять купон с публикации?',
    content: (
      <Input.TextArea
        rows={4}
        maxLength={1000}
        showCount
        placeholder="Укажите причину: ошибка в условиях, просьба мерчанта, оффер больше не актуален"
        onChange={(event) => {
          reason = event.target.value;
        }}
      />
    ),
    okText: 'Снять',
    cancelText: 'Отмена',
    okButtonProps: { danger: true },
    onOk: () => {
      const trimmedReason = reason.trim();
      if (!trimmedReason) {
        message.error('Укажите причину снятия с публикации');
        return Promise.reject(new Error('Archive reason is required'));
      }

      return archiveMutation.mutateAsync({ id: record.id, reason: trimmedReason });
    },
  });
};
```

- [ ] **Step 5: Update status colors and actions**

Update `statusColors`:

```ts
const statusColors: Record<CouponStatus, string> = {
  LEAD: 'blue',
  DRAFT: 'default',
  WAITING_FOR_MERCHANT: 'purple',
  REVISION_REQUESTED: 'orange',
  ACTIVE: 'green',
  SOLD_OUT: 'volcano',
  ARCHIVED: 'default',
};
```

Inside the actions render:

```ts
const isEditable = record.status === 'LEAD' || record.status === 'DRAFT' || record.status === 'REVISION_REQUESTED';
const isDeletable = record.status === 'LEAD' || record.status === 'DRAFT' || record.status === 'REVISION_REQUESTED';
const isArchivable = record.status === 'ACTIVE' || record.status === 'SOLD_OUT';
```

Add the archive button inside `<Space>` after delete button:

```tsx
{isArchivable ? (
  <Button
    type="text"
    danger
    icon={<StopOutlined />}
    loading={archiveMutation.isPending}
    title="Снять с публикации"
    onClick={() => openArchiveModal(record)}
  />
) : (
  <Button type="text" danger icon={<StopOutlined />} disabled title="Архивирование недоступно" />
)}
```

- [ ] **Step 6: Keep Kanban focused on active work statuses only**

In `CouponKanbanPage.tsx`, extend `CouponStatus`:

```ts
type CouponStatus =
  | 'LEAD'
  | 'DRAFT'
  | 'WAITING_FOR_MERCHANT'
  | 'REVISION_REQUESTED'
  | 'ACTIVE'
  | 'SOLD_OUT'
  | 'ARCHIVED';
```

Change `KanbanColumnConfig`:

```ts
interface KanbanColumnConfig {
  key: Extract<CouponStatus, 'LEAD' | 'DRAFT' | 'WAITING_FOR_MERCHANT' | 'REVISION_REQUESTED'>;
  title: string;
  color: string;
  emptyText: string;
}
```

Update status colors and labels:

```ts
const statusTagColors: Record<CouponStatus, string> = {
  LEAD: 'blue',
  DRAFT: 'gold',
  WAITING_FOR_MERCHANT: 'purple',
  REVISION_REQUESTED: 'red',
  ACTIVE: 'green',
  SOLD_OUT: 'volcano',
  ARCHIVED: 'default',
};

const statusLabels: Record<CouponStatus, string> = {
  LEAD: 'LEAD',
  DRAFT: 'DRAFT',
  WAITING_FOR_MERCHANT: 'WAITING_FOR_MERCHANT',
  REVISION_REQUESTED: 'REVISION_REQUESTED',
  ACTIVE: 'ACTIVE',
  SOLD_OUT: 'SOLD_OUT',
  ARCHIVED: 'ARCHIVED',
};
```

Replace the query filter:

```ts
const boardStatuses = new Set(KANBAN_COLUMNS.map((column) => column.key));
return normalizeCoupons(res.data.data).filter((coupon) => boardStatuses.has(coupon.status));
```

This prevents `ARCHIVED` and `SOLD_OUT` from appearing in the work kanban.

- [ ] **Step 7: Run admin build**

Run:

```bash
cd frontend/admin-app
npm run build
```

Expected: PASS.

---

### Task 5: Update Docs And Contracts

**Files:**
- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/COUPON_FLOW.md`
- Modify: `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`

- [ ] **Step 1: Update API contract**

Add or update admin coupon archive endpoint in `docs/API_CONTRACT.md`:

````md
### POST `/api/v1/admin/coupons/{id}/archive` — Снять купон с публикации

**Auth:** ✅ `MODERATOR`, `ADMIN`, `SUPER_ADMIN`

**Allowed current statuses:** `ACTIVE`, `SOLD_OUT`

**Request:**
```json
{
  "reason": "Мерчант попросил остановить продажи"
}
```

**Result:**

- Coupon offer status becomes `ARCHIVED`.
- Public catalog/detail no longer shows the offer.
- Existing purchased coupons are not cancelled or refunded automatically.
- `ARCHIVED` is terminal in MVP.
````

- [ ] **Step 2: Update coupon flow**

In `docs/COUPON_FLOW.md`, update lifecycle:

```md
LEAD → DRAFT → WAITING_FOR_MERCHANT → ACTIVE → SOLD_OUT
                                            ↘ ARCHIVED
SOLD_OUT → ARCHIVED
```

Add:

```md
For MVP, `ACTIVE` and `SOLD_OUT` offers are immutable. If staff needs to change public terms, they archive the current offer with a reason and create a new draft.
```

- [ ] **Step 3: Update PRD**

In `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`, update the status and edge-case sections:

```md
- `ARCHIVED` — staff intentionally stopped future sales; existing purchased coupons are not changed automatically.
```

Replace any rule saying active coupons are editable with:

```md
For MVP, `ACTIVE`, `SOLD_OUT`, and `ARCHIVED` coupon offers are read-only. Public terms are not edited in place after publication.
```

Add:

```md
If an active offer needs changed terms, staff archives it with a reason and creates a new draft. Refund/cancellation of already purchased coupons is a separate order/payment workflow and is not triggered by archiving the offer.
```

- [ ] **Step 4: Run docs grep**

Run:

```bash
rg -n "редактировать.*ACTIVE|DRAFT, REVISION_REQUESTED, ACTIVE|ACTIVE.*редакт" docs services/coupon-service/src/main/java services/coupon-service/src/test/java
```

Expected: no stale docs or comments claiming `ACTIVE` is editable. Test names should also reflect the new rule.

---

### Task 6: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused coupon-service tests**

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.CouponOfferServiceBusinessLogicTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 2: Run broader coupon-service tests**

```bash
./gradlew :services:coupon-service:test -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS. If unrelated existing failures appear, capture exact failing test names and output.

- [ ] **Step 3: Run admin-app build**

```bash
cd frontend/admin-app
npm run build
```

Expected: PASS.

- [ ] **Step 4: Run admin-app lint**

```bash
cd frontend/admin-app
npm run lint
```

Expected: PASS. If admin-app has pre-existing lint failures, capture exact output and do not hide it.

- [ ] **Step 5: Run contract grep checks**

```bash
rg -n "ARCHIVED|archiveReason|archivedAt|/archive" services/coupon-service/src/main/java services/coupon-service/src/test/java frontend/admin-app/src docs/API_CONTRACT.md docs/COUPON_FLOW.md docs/PRODUCT_REQUIREMENTS_DOCUMENT.md
```

Expected: matches in enum/entity/DTO/service/controller/tests/admin UI/docs.

```bash
rg -n "DRAFT, REVISION_REQUESTED, ACTIVE|редактирование из DRAFT, REVISION_REQUESTED, ACTIVE|isEditable.*ACTIVE" services/coupon-service/src/main/java services/coupon-service/src/test/java frontend/admin-app/src docs
```

Expected: no matches.

```bash
rg -n "ARCHIVED.*PurchasedCoupon|PurchasedCoupon.*ARCHIVED|archive.*refund|archive.*cancel" services docs
```

Expected: no code path that archives purchased coupons or triggers refund from offer archive. Docs may mention that archiving does not refund/cancel purchased coupons.

---

## Self-Review

- Spec coverage: covers immutable `ACTIVE`, immutable `SOLD_OUT`, terminal `ARCHIVED`, archive reason, public hiding, admin UI actions, and docs.
- Scope check: does not implement refunds, purchased coupon cancellation, merchant web cabinet, or offer versioning.
- Test coverage: includes update restrictions, archive allowed paths, archive forbidden paths, delete protection, generic status endpoint protection, and build/lint verification.
- Type consistency: uses `archiveReason` and `archivedAt` consistently in entity, response DTO, frontend row type, and docs.
