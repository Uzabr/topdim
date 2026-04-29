# User Flow Completion Stage 2: Per-Coupon Refund + Complaint + Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users request refunds per purchased coupon, report problems when refund is not allowed, see request statuses, receive critical notifications, and let admins process support cases safely.

**Architecture:** `order-service` owns purchased coupons, refunds, complaints, and support decisions. `notification-service` already stores notification events, so `order-service` should publish minimal `NotificationEvent`s for refund/complaint lifecycle changes. `frontend/web-app` surfaces refund/complaint actions from the user profile and purchased coupon cards, while `frontend/admin-app` provides support queues for staff.

**Tech Stack:** Java 21, Spring Boot, Spring Security, JPA, Flyway/PostgreSQL, RabbitMQ events, JUnit 5/Mockito, React 19, Vite, React Query, React Hook Form, Zod, Axios, Ant Design in admin-app.

---

## Required Skills And Project Rules

- Read and follow `AGENTS.md` before touching business logic.
- Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task.
- Use `services/.agent/skills/api-design-principles/SKILL.md` for endpoint/resource design.
- Use `services/.agent/skills/database-schema-designer/SKILL.md` for migration design.
- Use `services/.agent/skills/spring-boot-test-patterns/SKILL.md` for backend tests.
- Use `frontend/.agent/skills/react/SKILL.md` for React component structure.
- Use `frontend/.agent/skills/react-hook-form-zod/SKILL.md` for user-facing forms.
- Use `frontend/.agent/skills/frontend-design/SKILL.md` only to fit existing TopDim UI. Do not redesign the site.

## Business Rules

- Refunds are per `PurchasedCoupon`, not per whole `Order`.
- User can request a refund only for their own purchased coupon.
- Refund can be requested only when purchased coupon status is `ACTIVE` and it has not expired.
- `USED`, `EXPIRED`, `REFUNDED`, `CANCELLED`, and `REFUND_PENDING` coupons cannot be refunded by the user.
- For `USED` or `EXPIRED` coupons, user should be directed to “Сообщить о проблеме” instead of refund.
- Creating a refund request changes purchased coupon status from `ACTIVE` to `REFUND_PENDING`.
- `REFUND_PENDING` coupons cannot be redeemed by merchant/cashier because redemption already requires `ACTIVE`.
- Admin can reject a pending refund. Rejection returns purchased coupon status to `ACTIVE` if it is still not expired; otherwise it becomes `EXPIRED`.
- Admin can approve a pending refund. Approval changes refund request to `APPROVED_PROCESSING`; coupon stays `REFUND_PENDING`.
- Approved refunds are not instant. UI copy must say: “Возврат одобрен. Деньги вернутся в течение до 5 рабочих дней.”
- Admin can mark approved refund as completed. Completion changes refund request to `REFUNDED` and purchased coupon status to `REFUNDED`.
- A purchased coupon can have only one active or completed refund request. Duplicate refund requests are blocked.
- Complaints are allowed for user-owned purchased coupons in any status except when the coupon does not belong to the user.
- Complaint processing does not automatically refund money.
- Notifications are required for refund created, refund approved, refund rejected, refund completed, complaint created, and complaint resolved/rejected.

## Current State To Respect

- Existing order refund endpoint is order-level: `POST /api/v1/orders/{orderId}/refund`.
- Existing `RefundRequest` is linked to `Order`, not `PurchasedCoupon`.
- Existing `RefundRequest.RefundStatus` values are `PENDING`, `APPROVED`, `REJECTED`.
- Existing `PurchasedCouponStatus` has `ACTIVE`, `USED`, `EXPIRED`, `CANCELLED`; comments mention `REFUNDED` but enum does not contain it yet.
- Existing complaints are order-level but can be extended to purchased-coupon-level.
- Existing complaint resolution already publishes notification events.
- Admin app already has support menu item for complaints, but route/page may not be wired.
- Notification backend exists at `GET /api/v1/notifications` and `PATCH /api/v1/notifications/{id}/read`, but web-app has no notification center yet.

---

## Task 1: Add Per-Coupon Refund Schema And Statuses

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/PurchasedCouponStatus.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/RefundRequest.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/Complaint.java`
- Create: `services/order-service/src/main/resources/db/migration/V11__per_coupon_refunds_and_complaints.sql`

- [ ] Update `PurchasedCouponStatus`:

```java
public enum PurchasedCouponStatus {
    ACTIVE,
    USED,
    EXPIRED,
    REFUND_PENDING,
    REFUNDED,
    CANCELLED
}
```

- [ ] Update `RefundRequest.RefundStatus`:

```java
public enum RefundStatus {
    PENDING,
    APPROVED_PROCESSING,
    REFUNDED,
    REJECTED
}
```

- [ ] Add fields to `RefundRequest`:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "purchased_coupon_id")
private PurchasedCoupon purchasedCoupon;

@Column(name = "refund_amount", precision = 12, scale = 2)
private BigDecimal refundAmount;

@Column(name = "expected_refund_at")
private LocalDateTime expectedRefundAt;

@Column(name = "completed_at")
private LocalDateTime completedAt;
```

- [ ] Keep `order` on `RefundRequest` for backward compatibility and admin context.

- [ ] Add optional purchased coupon link to `Complaint`:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "purchased_coupon_id")
private PurchasedCoupon purchasedCoupon;
```

- [ ] Create migration:

```sql
ALTER TABLE refund_requests
    ADD COLUMN IF NOT EXISTS purchased_coupon_id BIGINT REFERENCES purchased_coupons(id),
    ADD COLUMN IF NOT EXISTS refund_amount DECIMAL(12, 2),
    ADD COLUMN IF NOT EXISTS expected_refund_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_refund_requests_purchased_coupon
    ON refund_requests(purchased_coupon_id);

CREATE INDEX IF NOT EXISTS idx_refund_requests_status_created
    ON refund_requests(status, created_at DESC);

ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS purchased_coupon_id BIGINT REFERENCES purchased_coupons(id);

CREATE INDEX IF NOT EXISTS idx_complaints_purchased_coupon
    ON complaints(purchased_coupon_id);
```

- [ ] Do not make `purchased_coupon_id` `NOT NULL` in this migration because legacy order-level rows may exist.

---

## Task 2: Add Refund DTOs And Repository Methods

**Files:**
- Create: `services/order-service/src/main/java/uz/topdim/order/dto/CreateCouponRefundRequest.java`
- Create: `services/order-service/src/main/java/uz/topdim/order/dto/RefundRequestResponse.java`
- Create: `services/order-service/src/main/java/uz/topdim/order/dto/RefundDecisionRequest.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/RefundRequestRepository.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/dto/PurchasedCouponResponse.java`

- [ ] Create `CreateCouponRefundRequest`:

```java
@Data
public class CreateCouponRefundRequest {
    @NotNull(message = "ID купленного купона обязателен")
    private Long purchasedCouponId;

    @NotBlank(message = "Причина возврата обязательна")
    @Size(min = 10, max = 1000, message = "Причина должна быть от 10 до 1000 символов")
    private String reason;
}
```

- [ ] Create `RefundDecisionRequest`:

```java
@Data
public class RefundDecisionRequest {
    @Size(max = 1000, message = "Комментарий не должен превышать 1000 символов")
    private String adminComment;
}
```

- [ ] Create `RefundRequestResponse`:

```java
@Data
@Builder
public class RefundRequestResponse {
    private Long id;
    private Long orderId;
    private Long purchasedCouponId;
    private Long userId;
    private String couponTitle;
    private String optionTitle;
    private String couponCode;
    private String merchantName;
    private BigDecimal refundAmount;
    private String reason;
    private RefundRequest.RefundStatus status;
    private String adminComment;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime expectedRefundAt;
    private LocalDateTime completedAt;
}
```

- [ ] Add repository methods:

```java
List<RefundRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

Page<RefundRequest> findByStatusOrderByCreatedAtDesc(RefundRequest.RefundStatus status, Pageable pageable);

Page<RefundRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

boolean existsByPurchasedCouponIdAndStatusIn(
        Long purchasedCouponId,
        Collection<RefundRequest.RefundStatus> statuses
);

List<RefundRequest> findByPurchasedCouponIdOrderByCreatedAtDesc(Long purchasedCouponId);
```

- [ ] Extend `PurchasedCouponResponse` with refund fields:

```java
private Long refundRequestId;
private String refundStatus;
private LocalDateTime refundExpectedAt;
```

These fields may be `null` when there is no refund request.

---

## Task 3: Implement Refund Business Logic In OrderService

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Test: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`

- [ ] Add helper for 5 working days:

```java
private LocalDateTime addWorkingDays(LocalDateTime start, int workingDays) {
    LocalDateTime result = start;
    int added = 0;
    while (added < workingDays) {
        result = result.plusDays(1);
        DayOfWeek day = result.getDayOfWeek();
        if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
            added++;
        }
    }
    return result;
}
```

- [ ] Add `createCouponRefundRequest(Long userId, Long purchasedCouponId, String reason)`:

```text
1. expireOverduePurchasedCoupons()
2. find PurchasedCoupon by id or throw "Купон не найден"
3. verify purchasedCoupon.userId == userId or throw "Купон не принадлежит пользователю"
4. verify status == ACTIVE or throw "Возврат доступен только для активного неиспользованного купона"
5. verify expiresAt is null or expiresAt is after now, otherwise set EXPIRED and throw same message
6. block duplicate if refund exists with PENDING, APPROVED_PROCESSING, or REFUNDED
7. calculate refundAmount from matching OrderItem.unitPrice
8. create RefundRequest with status PENDING, order, purchasedCoupon, userId, reason.trim(), refundAmount
9. set purchasedCoupon.status = REFUND_PENDING
10. save both entities
11. publish notification "Заявка на возврат создана"
12. return mapped RefundRequestResponse
```

- [ ] Add `getUserRefundRequests(Long userId)` returning `List<RefundRequestResponse>` sorted newest first.

- [ ] Add `getAdminRefundRequests(RefundStatus status, int page, int size)` returning a page sorted newest first.

- [ ] Add `approveRefundRequest(Long requestId, String adminComment)`:

```text
Allowed only from PENDING.
Set status APPROVED_PROCESSING.
Set adminComment.
Set resolvedAt = now.
Set expectedRefundAt = addWorkingDays(now, 5).
Keep purchasedCoupon.status = REFUND_PENDING.
Publish notification with "до 5 рабочих дней".
```

- [ ] Add `rejectRefundRequest(Long requestId, String adminComment)`:

```text
Allowed only from PENDING.
Set status REJECTED.
Set adminComment.
Set resolvedAt = now.
If purchasedCoupon.status == REFUND_PENDING:
  if expiresAt is before now -> set EXPIRED
  else set ACTIVE
Publish notification with rejection comment.
```

- [ ] Add `completeRefundRequest(Long requestId, String adminComment)`:

```text
Allowed only from APPROVED_PROCESSING.
Set status REFUNDED.
Set completedAt = now.
Set resolvedAt = now if empty.
Append or set adminComment if provided.
Set purchasedCoupon.status = REFUNDED.
Publish notification "Возврат завершён".
```

- [ ] Update `mapToCouponResponse(PurchasedCoupon coupon)` to include latest refund request fields.

- [ ] Add unit tests:

```java
@Test
void createCouponRefund_activeCoupon_createsPendingRequestAndLocksCoupon() {}

@Test
void createCouponRefund_usedCoupon_throwsAndDoesNotCreateRequest() {}

@Test
void createCouponRefund_expiredCoupon_throwsAndMarksExpired() {}

@Test
void createCouponRefund_otherUserCoupon_throwsForbiddenBusinessError() {}

@Test
void createCouponRefund_duplicatePendingRequest_throwsConflict() {}

@Test
void approveRefund_pending_setsProcessingAndExpectedRefundAtFiveWorkingDays() {}

@Test
void rejectRefund_pending_reactivatesCouponWhenStillValid() {}

@Test
void rejectRefund_pending_marksExpiredWhenCouponExpiredDuringReview() {}

@Test
void completeRefund_processing_marksRequestAndCouponRefunded() {}

@Test
void completeRefund_pendingRequest_throwsInvalidState() {}
```

- [ ] Run:

```bash
./gradlew :services:order-service:test
```

---

## Task 4: Add Refund REST Endpoints

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java`
- Test: add/update controller tests if existing order controller tests cover refund endpoints.

- [ ] Add user endpoints:

```java
@PostMapping("/api/v1/refunds")
public ResponseEntity<ApiResponse<RefundRequestResponse>> createCouponRefund(
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody CreateCouponRefundRequest request
) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            "Заявка на возврат создана",
            orderService.createCouponRefundRequest(userId, request.getPurchasedCouponId(), request.getReason())
    ));
}

@GetMapping("/api/v1/refunds/my")
public ResponseEntity<ApiResponse<List<RefundRequestResponse>>> getMyRefunds(
        @RequestHeader("X-User-Id") Long userId
) {
    return ResponseEntity.ok(ApiResponse.success(orderService.getUserRefundRequests(userId)));
}
```

- [ ] Keep existing `POST /api/v1/orders/{orderId}/refund` for compatibility, but web-app must use new `/api/v1/refunds`.

- [ ] Add admin endpoints:

```java
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@GetMapping("/api/v1/admin/refunds")
public ResponseEntity<ApiResponse<Page<RefundRequestResponse>>> getAdminRefunds(
        @RequestParam(required = false) RefundRequest.RefundStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
) {
    return ResponseEntity.ok(ApiResponse.success(orderService.getAdminRefundRequests(status, page, size)));
}

@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@PatchMapping("/api/v1/admin/refunds/{id}/approve")
public ResponseEntity<ApiResponse<RefundRequestResponse>> approveRefund(
        @PathVariable Long id,
        @RequestBody RefundDecisionRequest request
) {
    return ResponseEntity.ok(ApiResponse.success(
            "Возврат одобрен",
            orderService.approveRefundRequest(id, request.getAdminComment())
    ));
}

@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@PatchMapping("/api/v1/admin/refunds/{id}/reject")
public ResponseEntity<ApiResponse<RefundRequestResponse>> rejectRefund(
        @PathVariable Long id,
        @RequestBody RefundDecisionRequest request
) {
    return ResponseEntity.ok(ApiResponse.success(
            "Возврат отклонён",
            orderService.rejectRefundRequest(id, request.getAdminComment())
    ));
}

@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@PatchMapping("/api/v1/admin/refunds/{id}/complete")
public ResponseEntity<ApiResponse<RefundRequestResponse>> completeRefund(
        @PathVariable Long id,
        @RequestBody RefundDecisionRequest request
) {
    return ResponseEntity.ok(ApiResponse.success(
            "Возврат завершён",
            orderService.completeRefundRequest(id, request.getAdminComment())
    ));
}
```

- [ ] Map invalid states to HTTP `409 Conflict` through existing exception handling or controller catch blocks.

---

## Task 5: Extend Complaint Flow To Purchased Coupons

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/dto/CreateComplaintRequest.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/dto/ComplaintResponse.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/ComplaintService.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/ComplaintRepository.java`
- Test: create/update `services/order-service/src/test/java/uz/topdim/order/service/ComplaintServiceTest.java`

- [ ] Extend `CreateComplaintRequest`:

```java
private Long orderId;

@NotNull(message = "ID купленного купона обязателен")
private Long purchasedCouponId;
```

`orderId` may remain for legacy compatibility, but new web-app flow must send `purchasedCouponId`.

- [ ] Extend `ComplaintResponse`:

```java
private Long purchasedCouponId;
private String couponTitle;
private String optionTitle;
private String couponCode;
private String merchantName;
```

- [ ] Update `ComplaintService.createComplaint`:

```text
If purchasedCouponId is present:
  find PurchasedCoupon
  verify user owns it
  derive order from purchasedCoupon.order
  create complaint linked to order and purchasedCoupon
If only orderId is present:
  keep legacy behavior
On successful creation:
  publish notification "Обращение создано"
```

- [ ] Add repository method:

```java
Page<Complaint> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
Page<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status, Pageable pageable);
```

These already exist; keep them and only add purchased-coupon-specific methods if needed.

- [ ] Add tests:

```java
@Test
void createComplaint_forOwnPurchasedCoupon_createsPendingComplaint() {}

@Test
void createComplaint_forOtherUserPurchasedCoupon_throws() {}

@Test
void createComplaint_orderLegacyFlow_stillWorks() {}

@Test
void resolveComplaint_resolved_sendsNotification() {}
```

---

## Task 6: Add Web-App Refund, Complaint, And Notification APIs

**Files:**
- Create: `frontend/web-app/src/api/refunds.ts`
- Create: `frontend/web-app/src/api/complaints.ts`
- Create: `frontend/web-app/src/api/notifications.ts`
- Modify: `frontend/web-app/src/api/orders.ts`

- [ ] Update purchased coupon statuses:

```ts
status: 'ACTIVE' | 'USED' | 'EXPIRED' | 'REFUND_PENDING' | 'REFUNDED' | 'CANCELLED';
refundRequestId?: number;
refundStatus?: string;
refundExpectedAt?: string;
```

- [ ] Create `refunds.ts`:

```ts
export interface RefundRequestData {
  id: number;
  orderId: number;
  purchasedCouponId: number;
  userId: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  merchantName?: string;
  refundAmount?: number;
  reason: string;
  status: 'PENDING' | 'APPROVED_PROCESSING' | 'REFUNDED' | 'REJECTED';
  adminComment?: string;
  createdAt: string;
  resolvedAt?: string;
  expectedRefundAt?: string;
  completedAt?: string;
}

export const refundsApi = {
  create: (data: { purchasedCouponId: number; reason: string }) =>
    apiClient.post<ApiResponse<RefundRequestData>>('/api/v1/refunds', data),

  getMine: () =>
    apiClient.get<ApiResponse<RefundRequestData[]>>('/api/v1/refunds/my'),
};
```

- [ ] Create `complaints.ts`:

```ts
export interface ComplaintData {
  id: number;
  userId: number;
  orderId: number;
  purchasedCouponId?: number;
  couponTitle?: string;
  optionTitle?: string;
  couponCode?: string;
  merchantName?: string;
  subject: string;
  description: string;
  status: 'PENDING' | 'IN_REVIEW' | 'RESOLVED' | 'REJECTED';
  resolution?: string;
  createdAt: string;
}

export const complaintsApi = {
  create: (data: { purchasedCouponId: number; subject: string; description: string }) =>
    apiClient.post<ApiResponse<number>>('/api/v1/complaints', data),

  getMine: (page = 0, size = 20) =>
    apiClient.get<ApiResponse<PagedResponse<ComplaintData>>>('/api/v1/complaints/my', {
      params: { page, size },
    }),
};
```

- [ ] Create `notifications.ts`:

```ts
export interface NotificationData {
  id: number;
  title: string;
  message: string;
  type: string;
  read: boolean;
  createdAt: string;
}

export const notificationsApi = {
  getMine: (unreadOnly?: boolean, page = 0, size = 20) =>
    apiClient.get<ApiResponse<PagedResponse<NotificationData>>>('/api/v1/notifications', {
      params: { unreadOnly, page, size },
    }),

  markRead: (id: number) =>
    apiClient.patch<ApiResponse<string>>(`/api/v1/notifications/${id}/read`),
};
```

---

## Task 7: Wire User Profile Actions In Web-App

**Files:**
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- Create: `frontend/web-app/src/components/profile/RefundRequestModal.tsx`
- Create: `frontend/web-app/src/components/profile/ComplaintModal.tsx`
- Create: `frontend/web-app/src/components/profile/RefundsSection.tsx`
- Create: `frontend/web-app/src/components/profile/ComplaintsSection.tsx`
- Create: `frontend/web-app/src/components/profile/NotificationsSection.tsx`
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.css`
- Modify: `frontend/web-app/src/pages/ProfilePage.css`

- [ ] Add optional action props to `PurchasedCouponCard`:

```ts
interface PurchasedCouponCardProps {
  coupon: PurchasedCoupon;
  onRefundRequest?: (coupon: PurchasedCoupon) => void;
  onComplaintRequest?: (coupon: PurchasedCoupon) => void;
}
```

- [ ] Show actions:

```text
ACTIVE -> “Запросить возврат” and “Сообщить о проблеме”
REFUND_PENDING -> badge “Возврат на рассмотрении”
REFUNDED -> badge “Возвращён”
USED -> “Сообщить о проблеме”
EXPIRED -> “Сообщить о проблеме”
CANCELLED -> no refund action
```

- [ ] `RefundRequestModal` form fields:

```text
Read-only coupon title/code/merchant.
Reason textarea, 10-1000 chars.
Submit text: “Отправить заявку на возврат”.
Warning text: “После отправки заявки купон будет временно заблокирован от использования.”
```

- [ ] On refund create success:

```text
Close modal.
Show success toast/message.
Invalidate ['my-coupons'] and ['my-refunds'].
```

- [ ] `ComplaintModal` form fields:

```text
Subject select:
  Партнёр не принял купон
  QR/PIN не сработал
  Адрес или контакты неверные
  Условия не совпали
  Другое
Description textarea, min 10 chars.
```

- [ ] Add profile tabs or sections for:

```text
Возвраты
Жалобы
Уведомления
```

If Stage 1 profile hub already exists, add these tabs to it. If Stage 1 is not merged yet, add them to current `ProfilePage` without removing existing coupon tabs.

- [ ] `RefundsSection` must show status copy:

```text
PENDING -> На рассмотрении
APPROVED_PROCESSING -> Возврат одобрен, деньги вернутся до 5 рабочих дней
REFUNDED -> Возврат завершён
REJECTED -> Возврат отклонён
```

- [ ] `ComplaintsSection` must show status copy:

```text
PENDING -> На рассмотрении
IN_REVIEW -> В работе
RESOLVED -> Обработано
REJECTED -> Отклонено
```

- [ ] `NotificationsSection` must allow:

```text
List notifications.
Filter unreadOnly.
Mark notification as read.
Empty state.
```

- [ ] Run:

```bash
cd frontend/web-app && npm run build
```

---

## Task 8: Add Admin Support Pages

**Files:**
- Modify: `frontend/admin-app/src/components/layout/AdminLayout.tsx`
- Modify: `frontend/admin-app/src/App.tsx`
- Modify: `frontend/admin-app/src/features/support/api.ts`
- Create: `frontend/admin-app/src/features/support/RefundsPage.tsx`
- Create: `frontend/admin-app/src/features/support/ComplaintsPage.tsx`

- [ ] Add support menu item:

```tsx
{ key: '/support/refunds', icon: <ShoppingCartOutlined />, label: 'Возвраты', roles: ['ADMIN', 'SUPER_ADMIN'] }
```

- [ ] Wire routes:

```tsx
<Route path="/support/refunds" element={<RefundsPage />} />
<Route path="/support/complaints" element={<ComplaintsPage />} />
```

- [ ] Extend support API:

```ts
export async function getAdminRefunds(status?: string, page = 0, size = 20) {
  const res = await api.get('/api/v1/admin/refunds', { params: { status, page, size } });
  return res.data.data;
}

export async function approveRefund(id: number, adminComment?: string) {
  const res = await api.patch(`/api/v1/admin/refunds/${id}/approve`, { adminComment });
  return res.data.data;
}

export async function rejectRefund(id: number, adminComment?: string) {
  const res = await api.patch(`/api/v1/admin/refunds/${id}/reject`, { adminComment });
  return res.data.data;
}

export async function completeRefund(id: number, adminComment?: string) {
  const res = await api.patch(`/api/v1/admin/refunds/${id}/complete`, { adminComment });
  return res.data.data;
}

export async function getPendingComplaints(page = 0, size = 20) {
  const res = await api.get('/api/v1/mod/complaints', { params: { page, size } });
  return res.data.data;
}

export async function resolveComplaint(id: number, decision: 'RESOLVE' | 'REJECT', resolution: string) {
  await api.patch(`/api/v1/mod/complaints/${id}/resolve`, { decision, resolution });
}
```

- [ ] `RefundsPage` must show:

```text
Coupon title/code
User id
Merchant
Refund amount
Reason
Status
Expected refund date
Approve action for PENDING
Reject action for PENDING
Complete action for APPROVED_PROCESSING
```

- [ ] `ComplaintsPage` must show:

```text
Coupon title/code when available
Order id
User id
Subject
Description
Status
Resolution
Resolve action
Reject action
```

- [ ] Run:

```bash
cd frontend/admin-app && npm run build
```

---

## Task 9: QA And Product Docs

**Files:**
- Create: `docs/qa/refund-complaint-notifications-checklist.md`
- Modify: `docs/product/flows/coupon-flow.md`
- Modify: `docs/product/roles.md`

- [ ] Document refund lifecycle:

```text
ACTIVE purchased coupon
→ user creates refund request
→ purchased coupon REFUND_PENDING
→ admin approves
→ refund APPROVED_PROCESSING, expected refund within 5 working days
→ admin marks completed
→ refund REFUNDED, purchased coupon REFUNDED
```

- [ ] Document rejection lifecycle:

```text
ACTIVE purchased coupon
→ user creates refund request
→ purchased coupon REFUND_PENDING
→ admin rejects
→ refund REJECTED
→ purchased coupon ACTIVE if still valid, otherwise EXPIRED
```

- [ ] Document complaint lifecycle:

```text
User creates complaint for purchased coupon
→ PENDING
→ admin/moderator resolves or rejects
→ user sees response and receives notification
```

- [ ] Add QA checklist:

```text
1. ACTIVE coupon shows refund action.
2. USED coupon does not show refund action and shows complaint action.
3. EXPIRED coupon does not show refund action and shows complaint action.
4. User creates refund for ACTIVE coupon.
5. Coupon moves to REFUND_PENDING and cannot be redeemed.
6. Duplicate refund is blocked.
7. Admin approves refund.
8. User sees “до 5 рабочих дней”.
9. Admin completes refund.
10. Coupon becomes REFUNDED.
11. Admin rejects refund.
12. Coupon returns ACTIVE if still valid.
13. User creates complaint.
14. Admin resolves complaint.
15. User sees notification for each important support event.
```

---

## Final Verification

- [ ] Run backend tests:

```bash
./gradlew :services:order-service:test
```

- [ ] If notification-service code is changed, run:

```bash
./gradlew :services:notification-service:test
```

- [ ] Run frontend builds:

```bash
cd frontend/web-app && npm run build
cd frontend/admin-app && npm run build
```

- [ ] Manual full support smoke:

```text
Buyer has ACTIVE coupon.
Buyer requests refund.
Coupon becomes REFUND_PENDING.
Cashier cannot redeem REFUND_PENDING coupon.
Admin approves refund.
Buyer sees status and notification.
Admin completes refund.
Buyer sees REFUNDED coupon.
Buyer opens complaint on USED/EXPIRED coupon.
Admin resolves complaint.
Buyer sees resolution and notification.
```

