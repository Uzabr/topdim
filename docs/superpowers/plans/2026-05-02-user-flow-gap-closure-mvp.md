# User Flow Gap Closure MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining buyer-facing user-flow gaps so a real user can move clearly from purchase to coupon usage, review, support, refund/complaint tracking, and notification follow-up.

**Architecture:** Keep existing services and contracts where possible. Most work is in `frontend/web-app`, with one small security fix in `coupon-service` for review eligibility. Do not add new major features, payment providers, SMS/email delivery, or redesign the whole site.

**Tech Stack:** React 19, TypeScript, Vite, React Router, TanStack Query, Zustand, CSS, Spring Boot Security, JUnit/MockMvc where backend security changes need tests.

---

## Required Skills And Rules

- Read and follow root `AGENTS.md`.
- Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`.
- Use `superpowers:systematic-debugging` for unclear behavior or test failures.
- Use `superpowers:verification-before-completion` before reporting done.
- Use `frontend/.agent/skills/react/SKILL.md` for component and state work.
- Use `frontend/.agent/skills/frontend-design/SKILL.md` for UI polish, but do not redesign unrelated pages.
- Use `frontend/.agent/skills/react-hook-form-zod/SKILL.md` if touching forms.
- Use `services/.agent/skills/api-design-principles/SKILL.md` for endpoint/security changes.
- Use `services/.agent/skills/spring-boot-test-patterns/SKILL.md` for backend tests.

## Business Context

The coupon MVP has the main mechanics implemented:

- buyer can buy coupons;
- purchased coupons appear in profile;
- QR/PIN redemption works in partner app;
- refunds/complaints/notifications exist;
- reviews exist and require used coupon eligibility.

The current gap is user-flow continuity. The user can do things, but the UI does not always guide them to the next correct action.

## Core User Journey To Support

```text
Catalog
→ coupon detail
→ cart
→ checkout
→ payment
→ profile coupon
→ show QR/PIN to partner
→ coupon becomes USED
→ user can leave review
→ if problem: user can create complaint
→ if refund: user can track refund status
→ user sees notification/reply
```

## Main Gaps To Close

- Profile coupon tabs do not expose all current statuses clearly.
- After payment success, user is not guided strongly enough to the purchased coupon.
- Used coupon does not provide a clear “Оставить отзыв” next action.
- Refund/complaint statuses exist, but user needs clearer status guidance and next steps.
- Notifications are buried in profile and need an unread badge/entry point.
- Review eligibility endpoint must require authentication; public reviews should stay public.
- QA checklist must cover the full buyer journey, not only isolated services.

---

## Task 1: Make Purchased Coupon Status Navigation Complete

**Files:**
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`
- Modify: `frontend/web-app/src/pages/ProfilePage.css`
- Verify: `frontend/web-app/src/api/orders.ts`

### Requirements

- User must be able to find coupons in all important statuses.
- Existing statuses:
  - `ACTIVE`
  - `USED`
  - `EXPIRED`
  - `REFUND_PENDING`
  - `REFUNDED`
  - `CANCELLED`
- Do not hide refund-related coupons in an unrelated place.

### Implementation

- [ ] Replace current coupon tabs:

```ts
const COUPON_TABS = [
  { key: 'ACTIVE', label: 'Активные', icon: <Clock size={16} /> },
  { key: 'USED', label: 'Использованные', icon: <CheckCircle size={16} /> },
  { key: 'EXPIRED', label: 'Истёкшие', icon: <AlertCircle size={16} /> },
];
```

with:

```ts
const COUPON_TABS = [
  { key: 'ACTIVE', label: 'Активные', icon: <Clock size={16} /> },
  { key: 'REFUND_PENDING', label: 'На возврате', icon: <RotateCcw size={16} /> },
  { key: 'USED', label: 'Использованные', icon: <CheckCircle size={16} /> },
  { key: 'EXPIRED', label: 'Истёкшие', icon: <AlertCircle size={16} /> },
  { key: 'REFUNDED', label: 'Возвращённые', icon: <RotateCcw size={16} /> },
];
```

- [ ] Keep `CANCELLED` out of main tabs unless it exists in data. It can be handled by a fallback empty state or future support tab.
- [ ] Update empty state copy per status:

```ts
const EMPTY_COUPON_COPY: Record<string, { title: string; text: string }> = {
  ACTIVE: {
    title: 'У вас пока нет активных купонов',
    text: 'Выберите предложение в каталоге и купон появится здесь после оплаты.',
  },
  REFUND_PENDING: {
    title: 'Нет купонов на возврате',
    text: 'Когда вы запросите возврат, его статус появится здесь.',
  },
  USED: {
    title: 'Пока нет использованных купонов',
    text: 'После визита к партнёру использованные купоны будут здесь.',
  },
  EXPIRED: {
    title: 'Нет истёкших купонов',
    text: 'Купоны с истёкшим сроком будут отображаться в этом разделе.',
  },
  REFUNDED: {
    title: 'Нет возвращённых купонов',
    text: 'Завершённые возвраты будут отображаться здесь.',
  },
};
```

- [ ] Ensure `ordersApi.getMyCoupons(couponSubTab)` still receives a valid backend status.
- [ ] If backend returns an error for `REFUNDED` or `REFUND_PENDING`, stop and fix backend status filtering instead of hiding the tab.

### Manual Check

- [ ] Open `/profile?tab=coupons`.
- [ ] Switch through every coupon tab.
- [ ] Empty states are clear and not generic.
- [ ] Active, used, refund-pending, and refunded coupons appear in the right tab.

---

## Task 2: Add Post-Purchase Guidance On Payment Success

**Files:**
- Modify: `frontend/web-app/src/pages/PaymentPage.tsx`
- Modify: `frontend/web-app/src/pages/PaymentPage.css`

### Requirements

After successful payment, the user must understand:

- purchase is complete;
- coupons are in profile;
- partner will scan QR or enter PIN;
- next action is to open purchased coupons.

### Implementation

- [ ] On success state, show a short “what next” block:

```tsx
<div className="payment-next-steps">
  <h3>Что дальше?</h3>
  <ol>
    <li>Откройте купон в профиле.</li>
    <li>Покажите QR или PIN кассиру партнёра.</li>
    <li>После использования вы сможете оставить отзыв.</li>
  </ol>
</div>
```

- [ ] Primary CTA must be:

```text
Открыть мои купоны
```

and navigate to:

```ts
navigate(lp('/profile') + '?tab=coupons')
```

- [ ] Secondary CTA:

```text
История заказов
```

must navigate to:

```ts
navigate(lp('/profile') + '?tab=orders')
```

- [ ] Do not add another confirmation page.
- [ ] Do not change payment provider logic.

### Manual Check

- [ ] Complete demo payment.
- [ ] Success screen tells the user how to use the coupon.
- [ ] “Открыть мои купоны” opens profile coupons.

---

## Task 3: Add Review CTA For Used Coupons

**Files:**
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.css`
- Modify: `frontend/web-app/src/pages/CouponDetailPage.tsx`

### Requirements

After coupon is `USED`, user should have a clear next action:

```text
Оставить отзыв
```

This should lead to the coupon detail review tab.

### Implementation

- [ ] In `PurchasedCouponCard`, add `useLocalePath` and `Link` if not already available.
- [ ] Show review CTA only for `USED` coupons:

```tsx
{coupon.status === 'USED' ? (
  <Link
    className="coupon-ticket__action-btn coupon-ticket__action-btn--review"
    to={lp(`/coupons/${coupon.couponOfferId}`) + '?tab=reviews'}
  >
    Оставить отзыв
  </Link>
) : null}
```

- [ ] Place review CTA in the secondary action area/details panel, not as the primary active-coupon QR action.
- [ ] In `CouponDetailPage`, read `tab` from URL:

```tsx
const [searchParams, setSearchParams] = useSearchParams();
const initialTab = searchParams.get('tab') === 'reviews' ? 'reviews' : 'info';
const [activeTab, setActiveTab] = useState<'info' | 'reviews'>(initialTab);
```

- [ ] When user changes tab, update URL:

```tsx
const changeTab = (tab: 'info' | 'reviews') => {
  setActiveTab(tab);
  setSearchParams(tab === 'reviews' ? { tab: 'reviews' } : {});
};
```

- [ ] Replace direct `setActiveTab('reviews')` / `setActiveTab('info')` calls with `changeTab(...)`.
- [ ] Keep eligibility query behavior unchanged.

### Manual Check

- [ ] A `USED` coupon shows “Оставить отзыв”.
- [ ] Clicking opens coupon detail with reviews tab active.
- [ ] If user is eligible, review form is visible.
- [ ] If user is not eligible, proper notice is shown.

---

## Task 4: Make Notification Entry Visible

**Files:**
- Modify: `frontend/web-app/src/components/layout/Header.tsx`
- Modify: `frontend/web-app/src/components/layout/Header.css`
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`
- Optional: `frontend/web-app/src/components/layout/BottomNav.tsx`
- Optional: `frontend/web-app/src/components/layout/BottomNav.css`

### Requirements

User should notice important events:

- refund approved/rejected/completed;
- complaint resolved/rejected;
- system notification after important support event.

### Implementation

- [ ] Use existing `notificationsApi.getMine(true, 0, 1)` to check unread notifications.
- [ ] Only query when user is authenticated.
- [ ] Add a small unread dot/badge on profile/notification entry in header.
- [ ] In profile sidebar, show a badge next to “Уведомления” when unread exists.
- [ ] Do not poll aggressively. Use a normal React Query stale time:

```ts
staleTime: 60_000
```

- [ ] If notification-service is down, header must not break. Treat as no unread badge.

### Manual Check

- [ ] User with unread notifications sees badge.
- [ ] User opens profile notifications.
- [ ] Marking notification as read removes badge after query invalidation/refetch.
- [ ] If notifications request fails, header still renders.

---

## Task 5: Improve Refund And Complaint Guidance In Profile

**Files:**
- Modify: `frontend/web-app/src/components/profile/RefundsSection.tsx`
- Modify: `frontend/web-app/src/components/profile/RefundsSection.css`
- Modify: `frontend/web-app/src/components/profile/ComplaintsSection.tsx`
- Modify: `frontend/web-app/src/components/profile/ComplaintsSection.css`
- Modify: `frontend/web-app/src/components/profile/RefundRequestModal.tsx`
- Modify: `frontend/web-app/src/components/profile/ComplaintModal.tsx`

### Requirements

User must understand what happens after submitting a refund or complaint.

### Copy Rules

Refund modal before submit:

```text
Возврат не происходит мгновенно. Мы рассмотрим заявку, и если возврат будет одобрен, деньги вернутся в течение до 5 рабочих дней.
```

Refund statuses:

```text
PENDING -> Заявка на рассмотрении
APPROVED_PROCESSING -> Возврат одобрен, деньги вернутся до указанной даты
REFUNDED -> Возврат завершён
REJECTED -> Возврат отклонён
```

Complaint modal before submit:

```text
Опишите проблему с купоном. Поддержка проверит обращение и ответит в этом разделе.
```

Complaint statuses:

```text
PENDING -> Обращение принято
IN_REVIEW -> В работе
RESOLVED -> Решено
REJECTED -> Отклонено
```

### Implementation

- [ ] Add explanatory copy in refund modal.
- [ ] Add explanatory copy in complaint modal.
- [ ] Ensure `RefundsSection` empty state tells user where refunds are created:

```text
Запросить возврат можно в карточке активного купона.
```

- [ ] Ensure `ComplaintsSection` empty state tells user where complaints are created:

```text
Сообщить о проблеме можно в карточке купона.
```

- [ ] Make rejected/approved/completed statuses visually distinct but not alarming.

### Manual Check

- [ ] User understands refund timing before submit.
- [ ] User understands where to track refund.
- [ ] User understands where to track complaint.

---

## Task 6: Fix Review Eligibility Security

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/config/SecurityConfig.java`
- Test: add or update coupon-service security/controller test if existing patterns allow.
- Verify: `frontend/web-app/src/api/reviews.ts`

### Current Risk

The current matcher:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/reviews/coupon/**").permitAll()
```

also matches:

```http
GET /api/v1/reviews/coupon/{couponId}/eligibility
```

Eligibility should require authentication because it is user-specific.

### Required Behavior

- Public:

```http
GET /api/v1/reviews/coupon/{couponId}
```

- Authenticated:

```http
GET /api/v1/reviews/coupon/{couponId}/eligibility
GET /api/v1/reviews/my
POST /api/v1/reviews
```

### Implementation

- [ ] Replace broad public matcher with a stricter matcher.
- [ ] Recommended option:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/reviews/coupon/*/eligibility").authenticated()
.requestMatchers(HttpMethod.GET, "/api/v1/reviews/coupon/*").permitAll()
```

- [ ] Put eligibility matcher before public reviews matcher.
- [ ] If path pattern behavior differs, use MVC matchers or split controller routes.
- [ ] Add/adjust tests:

```text
GET /api/v1/reviews/coupon/1 -> 200 without auth
GET /api/v1/reviews/coupon/1/eligibility -> 401/403 without auth
GET /api/v1/reviews/coupon/1/eligibility -> 200 with valid user headers/JWT
```

### Verification

- [ ] Run:

```bash
./gradlew :services:coupon-service:test
```

---

## Task 7: Guest Cart/Favorites Sync QA Pass

**Files:**
- Verify: `frontend/web-app/src/store/cartStore.ts`
- Verify: `frontend/web-app/src/store/favoritesStore.ts`
- Verify: `frontend/web-app/src/store/authStore.ts`
- Modify only if a real bug is found.

### Requirements

Guest user behavior must be predictable:

- guest can add coupon to cart;
- guest can favorite coupon locally;
- after login/register, local cart syncs to backend;
- favorites merge with backend;
- checkout requires auth but does not lose cart.

### Manual QA

- [ ] Logout.
- [ ] Add coupon to cart.
- [ ] Add coupon to favorites.
- [ ] Go to checkout.
- [ ] Login/register.
- [ ] Confirm cart still has item.
- [ ] Confirm favorite still exists.
- [ ] Complete checkout.

### Code Guidance

- [ ] If sync fails, do not rewrite stores fully.
- [ ] Fix only the specific bug.
- [ ] Keep localStorage fallback behavior.

---

## Task 8: Create Full Buyer Journey QA Checklist

**Files:**
- Create: `docs/qa/user-flow-gap-closure-checklist.md`
- Modify: `docs/qa/beta-readiness-report.md`

### Checklist Content

Create the checklist with these sections:

```md
# User Flow Gap Closure Checklist

## Discovery
- [ ] Guest opens homepage.
- [ ] Guest searches or opens catalog.
- [ ] Coupon detail opens.
- [ ] Reviews are visible to guest.
- [ ] Review eligibility is not publicly exposed.

## Cart And Auth
- [ ] Guest adds coupon to cart.
- [ ] Guest cart survives login/register.
- [ ] Guest favorites merge after login/register.

## Checkout And Payment
- [ ] Checkout explains where coupon will appear.
- [ ] Payment success explains next steps.
- [ ] User can open profile coupons from success screen.

## Coupon Usage
- [ ] Active coupon appears in profile.
- [ ] Active coupon has compact pass card.
- [ ] QR/PIN opens in one click.
- [ ] Partner redeems coupon.
- [ ] Used coupon moves to used tab.

## After Usage
- [ ] Used coupon shows “Оставить отзыв”.
- [ ] Review CTA opens reviews tab.
- [ ] Eligible user can submit review.
- [ ] Submitted review shows pending/moderation copy.

## Support
- [ ] Active coupon can request refund.
- [ ] Refund timing is clear before submit.
- [ ] Refund pending appears in correct tab.
- [ ] Refund status appears in refunds section.
- [ ] Used/expired coupon can create complaint.
- [ ] Complaint status appears in complaints section.

## Notifications
- [ ] Unread notification badge appears.
- [ ] User opens notification section.
- [ ] User marks notification as read.
- [ ] Badge disappears.
```

- [ ] Update `docs/qa/beta-readiness-report.md` with a new section:

```md
## User Flow Gap Closure

| Area | Status | Notes |
|------|--------|-------|
| Coupon status tabs | ⬜ | |
| Post-payment guidance | ⬜ | |
| Used coupon review CTA | ⬜ | |
| Notification badge | ⬜ | |
| Refund/complaint guidance | ⬜ | |
| Review eligibility security | ⬜ | |
| Guest cart/favorites sync | ⬜ | |
```

---

## Final Verification

- [ ] Run frontend build:

```bash
cd frontend/web-app && npm run build
```

- [ ] Run coupon-service tests:

```bash
./gradlew :services:coupon-service:test
```

- [ ] Manual full journey:

```text
Guest browses coupon.
Guest adds to cart/favorites.
Guest logs in.
Cart/favorites survive.
User checks out.
Payment success explains next step.
User opens profile coupons.
Active coupon has QR/PIN reveal.
Partner redeems coupon.
Used coupon shows review CTA.
User opens reviews tab and submits review.
User creates complaint or refund where appropriate.
Notification badge appears and can be cleared.
```

## Acceptance Criteria

- User can find every important purchased coupon status in profile.
- Payment success tells user exactly what to do next.
- Used coupon leads naturally to review flow.
- Refund and complaint flows have clear user-facing guidance.
- Notifications are visible enough for a user to notice replies/status changes.
- Review eligibility is not public.
- Guest cart/favorites sync is verified.
- QA checklist exists for the full user journey.
