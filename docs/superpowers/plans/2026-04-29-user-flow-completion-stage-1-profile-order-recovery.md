# User Flow Completion Stage 1: Profile Hub + Order Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the user profile into a clear post-purchase hub where a buyer can manage profile contact data, see purchased coupons, see order/payment history, and recover unfinished payments.

**Architecture:** This stage is storefront-first and should reuse existing backend contracts where possible. `identity-service` already owns profile update, `order-service` already owns orders and purchased coupons, and `payment-service` already owns payment status by order. The web app should connect those flows into a coherent profile experience without starting refund/complaint/notification implementation yet.

**Tech Stack:** React 19, Vite, React Router, React Query, Zustand, React Hook Form, Zod, Axios, Java/Spring Boot tests only if backend behavior is touched.

---

## Required Skills And Project Rules

- Read and follow `AGENTS.md` before changing business logic.
- Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this plan.
- Use `frontend/.agent/skills/react/SKILL.md` for component structure and React patterns.
- Use `frontend/.agent/skills/react-hook-form-zod/SKILL.md` for profile settings form validation.
- Use `frontend/.agent/skills/frontend-design/SKILL.md` only to keep the profile UI polished inside the current TopDim design system. Do not redesign the whole site.
- Use `services/.agent/skills/api-design-principles/SKILL.md` if any backend/API contract change becomes necessary.
- Use `services/.agent/skills/spring-boot-test-patterns/SKILL.md` if backend code is changed.

## Scope Boundaries

This stage must implement:

- Profile hub tabs/sections.
- Editable profile contact data.
- Order history in user profile.
- Payment recovery actions for unfinished/failed order payment states.
- Better checkout guidance when profile email/phone is missing.
- QA docs for the completed user flow stage.

This stage must not implement deeply yet:

- Refund request UI.
- Complaint/report problem UI.
- Notification center UI.
- Review flow. Reviews are handled by `docs/superpowers/plans/2026-04-29-reviews-flow-mvp.md`.
- Admin refund/complaint pages.
- Merchant or partner functionality.

It is acceptable to add disabled/coming-next cards for refunds, complaints, reviews, and notifications so users understand that these areas belong in the profile hub, but do not wire their full flows in this stage.

---

## Current Project Context To Respect

- Web routes live in `frontend/web-app/src/App.tsx`.
- Current profile page is `frontend/web-app/src/pages/ProfilePage.tsx`.
- Current profile CSS is `frontend/web-app/src/pages/ProfilePage.css`.
- Purchased coupon card is `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`.
- Auth state lives in `frontend/web-app/src/store/authStore.ts`.
- Auth API client is `frontend/web-app/src/api/auth.ts`.
- Order API client is `frontend/web-app/src/api/orders.ts`.
- Payment API client is `frontend/web-app/src/api/payments.ts`.
- Checkout page is `frontend/web-app/src/pages/CheckoutPage.tsx`.
- Payment recovery page is `frontend/web-app/src/pages/PaymentPage.tsx`.
- Backend profile endpoints already exist:

```text
GET /api/v1/users/me
PUT /api/v1/users/me
```

- Backend order endpoints already exist:

```text
GET /api/v1/orders?page=0&size=20
GET /api/v1/orders/{id}
GET /api/v1/orders/my-coupons?status=ACTIVE
```

- Backend payment endpoints already exist:

```text
GET /api/v1/payments/order/{orderId}
POST /api/v1/payments/order/{orderId}/demo-complete
```

---

## Target UX

The profile should answer these user questions:

- “Где мои купоны?”
- “Что с моим заказом?”
- “Оплата прошла или нет?”
- “Если оплата зависла, что мне нажать?”
- “Почему checkout не даёт купить?”
- “Где заполнить телефон?”
- “Куда потом придут возвраты, жалобы, отзывы и уведомления?”

Recommended profile sections:

```text
Overview
Мои купоны
Мои заказы
Настройки профиля
Скоро: Возвраты
Скоро: Жалобы
Скоро: Мои отзывы
Скоро: Уведомления
```

For MVP implementation, tabs may be:

```text
Купоны
Заказы
Профиль
Помощь
```

Where `Помощь` shows disabled cards for refunds/complaints/notifications and explains these are next stages.

---

## Task 1: Extend Frontend API Clients For Profile And Orders

**Files:**
- Modify: `frontend/web-app/src/api/auth.ts`
- Modify: `frontend/web-app/src/api/orders.ts`
- Modify: `frontend/web-app/src/store/authStore.ts`

- [ ] Add profile DTOs and API methods to `auth.ts`:

```ts
export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  avatarUrl?: string;
}

export const authApi = {
  register: (data: RegisterRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/register', data),

  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/login', data),

  refresh: (refreshToken: string) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/refresh', { refreshToken }),

  logout: (refreshToken: string) =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/logout', { refreshToken }),

  guestAuth: (data: { phone: string; name: string }) =>
    apiClient.post<ApiResponse<AuthResponse>>('/api/v1/auth/guest', data),

  getMe: () =>
    apiClient.get<ApiResponse<UserDto>>('/api/v1/users/me'),

  updateProfile: (data: UpdateProfileRequest) =>
    apiClient.put<ApiResponse<UserDto>>('/api/v1/users/me', data),
};
```

- [ ] Add order list typing and API method to `orders.ts`:

```ts
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  last: boolean;
}

export const ordersApi = {
  // keep existing methods

  getOrders: (page = 0, size = 20) =>
    apiClient.get<ApiResponse<PagedResponse<OrderResponse>>>('/api/v1/orders', {
      params: { page, size },
    }),
};
```

- [ ] Add profile refresh/update actions to `authStore.ts`:

```ts
refreshProfile: () => Promise<void>;
updateProfile: (data: UpdateProfileRequest) => Promise<UserDto>;
```

Implementation rule:

```text
After successful updateProfile, update Zustand user state and localStorage user.
After refreshProfile, update Zustand user state and localStorage user.
If refreshProfile fails with 401, let existing apiClient interceptor handle auth cleanup.
```

- [ ] Run frontend build:

```bash
cd frontend/web-app && npm run build
```

Expected: TypeScript passes after new API/store types are wired.

---

## Task 2: Rebuild Profile Page As A User Hub

**Files:**
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`
- Modify: `frontend/web-app/src/pages/ProfilePage.css`
- Create: `frontend/web-app/src/components/profile/ProfileOverview.tsx`
- Create: `frontend/web-app/src/components/profile/ProfileHelpSection.tsx`

- [ ] Update `ProfilePage.tsx` to support section state from URL query:

```ts
type ProfileTab = 'coupons' | 'orders' | 'profile' | 'help';

function getInitialTab(search: string): ProfileTab {
  const tab = new URLSearchParams(search).get('tab');
  if (tab === 'orders' || tab === 'profile' || tab === 'help') return tab;
  return 'coupons';
}
```

- [ ] When tab changes, update URL query without full reload:

```ts
const setTab = (tab: ProfileTab) => {
  setActiveProfileTab(tab);
  navigate(`${location.pathname}?tab=${tab}`, { replace: true });
};
```

- [ ] Keep the unauthenticated state, but make copy precise:

```text
Войдите или зарегистрируйтесь, чтобы видеть купоны, заказы и статус оплаты.
```

- [ ] Add `ProfileOverview` above tabs. It should show:

```text
User name
Email
Phone or “Телефон не указан”
Count of active coupons
Count of used coupons
Count of latest orders if loaded
Profile completeness warning when phone is missing
CTA “Заполнить телефон” -> tab=profile
```

- [ ] Add top-level profile tabs:

```text
Купоны
Заказы
Профиль
Помощь
```

- [ ] Move existing purchased coupon tabs inside the `Купоны` section:

```text
ACTIVE
USED
EXPIRED
```

- [ ] Add `ProfileHelpSection` with non-wired but clear next-step cards:

```text
Возвраты — скоро здесь можно будет запросить возврат и видеть статус.
Жалобы — скоро здесь можно будет сообщить о проблеме с купоном или партнёром.
Отзывы — отзывы сейчас находятся на странице купона после использования.
Уведомления — скоро здесь будут важные события по заказам и купонам.
```

- [ ] Do not remove the existing `PurchasedCouponCard`.

- [ ] Run:

```bash
cd frontend/web-app && npm run build
```

---

## Task 3: Add Order History Section

**Files:**
- Create: `frontend/web-app/src/components/profile/OrderHistorySection.tsx`
- Create: `frontend/web-app/src/components/profile/OrderHistorySection.css` or place styles in `ProfilePage.css` if the project prefers page-level CSS.
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`
- Modify: `frontend/web-app/src/api/orders.ts`

- [ ] Build `OrderHistorySection` with React Query:

```ts
const { data, isLoading, isError } = useQuery({
  queryKey: ['my-orders'],
  queryFn: () => ordersApi.getOrders(0, 20),
  select: (res) => res.data.data,
  enabled: isAuthenticated,
});
```

- [ ] Each order card must show:

```text
Order number or id
Status
Created date
Paid date if present
Total amount
Item count
```

- [ ] Use these status labels:

```ts
const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Ожидает оплаты',
  PAID: 'Оплачен',
  COMPLETED: 'Завершён',
  CANCELLED: 'Отменён',
  REFUND_REQUESTED: 'Запрошен возврат',
  REFUNDED: 'Возвращён',
};
```

- [ ] Add actions:

```text
PENDING -> button “Продолжить оплату” -> navigate /payment/{order.id}
PAID or COMPLETED -> button “Мои купоны” -> switch profile tab to coupons
REFUND_REQUESTED or REFUNDED -> read-only badge
CANCELLED -> read-only badge
```

- [ ] Empty state:

```text
У вас пока нет заказов.
CTA: Перейти в каталог
```

- [ ] Error state:

```text
Не удалось загрузить заказы. Попробуйте обновить страницу.
```

- [ ] Add this section into `ProfilePage` when active tab is `orders`.

- [ ] Run:

```bash
cd frontend/web-app && npm run build
```

---

## Task 4: Add Profile Settings Form

**Files:**
- Create: `frontend/web-app/src/components/profile/ProfileSettingsSection.tsx`
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`
- Modify: `frontend/web-app/src/pages/ProfilePage.css`
- Modify: `frontend/web-app/src/store/authStore.ts`

- [ ] Create Zod schema:

```ts
const profileSchema = z.object({
  firstName: z.string().trim().min(1, 'Имя обязательно').max(100, 'Имя не должно превышать 100 символов'),
  lastName: z.string().trim().max(100, 'Фамилия не должна превышать 100 символов').optional().or(z.literal('')),
  phone: z.string()
    .trim()
    .min(9, 'Укажите телефон')
    .max(20, 'Телефон не должен превышать 20 символов'),
});
```

- [ ] Form fields:

```text
Email — read-only
First name — editable
Last name — editable
Phone — editable and required for checkout
```

- [ ] Submit behavior:

```text
Call useAuthStore.updateProfile
Show success message “Профиль обновлён”
Show backend validation/error message if phone is duplicate
Keep submit disabled while loading
```

- [ ] If user has no phone, show warning:

```text
Телефон нужен для оформления заказа и связи по купону.
```

- [ ] After successful update, `CheckoutPage` must immediately see updated phone from auth store.

- [ ] Run:

```bash
cd frontend/web-app && npm run build
```

---

## Task 5: Improve Checkout Missing Contact Guidance

**Files:**
- Modify: `frontend/web-app/src/pages/CheckoutPage.tsx`
- Modify: `frontend/web-app/src/pages/CheckoutPage.css`

- [ ] When `userEmail` is missing, show clear blocking state:

```text
В профиле не указан email. Добавьте email в аккаунт или обратитесь в поддержку.
```

Email editing may remain disabled if backend does not support email change.

- [ ] When `userPhone` is missing, show:

```text
В профиле не указан телефон. Добавьте телефон, чтобы мы могли связать заказ и купон с вашим аккаунтом.
```

- [ ] Add CTA:

```ts
navigate(lp('/profile') + '?tab=profile')
```

Button text:

```text
Заполнить профиль
```

- [ ] Keep current checkout creation behavior once email and phone exist.

- [ ] Run:

```bash
cd frontend/web-app && npm run build
```

---

## Task 6: Improve Payment Recovery UX

**Files:**
- Modify: `frontend/web-app/src/pages/PaymentPage.tsx`
- Modify: `frontend/web-app/src/pages/PaymentPage.css`

- [ ] Use `useQueryClient` and invalidate relevant queries after successful demo completion:

```ts
queryClient.invalidateQueries({ queryKey: ['my-coupons'] });
queryClient.invalidateQueries({ queryKey: ['my-orders'] });
```

- [ ] On `completed`, show:

```text
Купоны уже доступны в профиле.
CTA: Открыть мои купоны -> /profile?tab=coupons
Secondary CTA: История заказов -> /profile?tab=orders
```

- [ ] On `failed`, show:

```text
Платёж не был завершён. Заказ сохранён, вы можете попробовать оплатить ещё раз.
CTA: Попробовать снова -> reload current page
Secondary CTA: Мои заказы -> /profile?tab=orders
```

- [ ] On `timeout`, show:

```text
Мы не смогли быстро получить платёж. Заказ сохранён в профиле, попробуйте продолжить оплату из раздела “Мои заказы”.
CTA: Мои заказы -> /profile?tab=orders
Secondary CTA: Обновить статус -> reload current page
```

- [ ] Keep demo payment behavior idempotent. Do not create another order on retry.

- [ ] Run:

```bash
cd frontend/web-app && npm run build
```

---

## Task 7: Optional Backend Safety Check For Profile Update

**Files:**
- Only modify backend if frontend work reveals a real backend bug.
- Candidate tests: `services/identity-service/src/test/java/uz/topdim/identity/service/UserServiceTest.java`

- [ ] If backend is unchanged, run existing tests for safety:

```bash
./gradlew :services:identity-service:test
```

- [ ] If backend is changed, add tests for:

```text
updateProfile keeps existing fields when optional fields are omitted.
updateProfile rejects duplicate phone.
updateProfile normalizes phone before saving.
getProfile returns current email/phone/name.
```

- [ ] Do not add email update in this stage unless the backend already supports it safely.

---

## Task 8: QA Docs For User Flow Stage 1

**Files:**
- Create: `docs/qa/user-flow-completion-stage-1-checklist.md`
- Modify: `docs/product/flows/coupon-flow.md`
- Modify: `docs/product/roles.md`

- [ ] Create checklist with these manual scenarios:

```text
1. Guest opens profile -> login CTA.
2. User logs in -> profile overview loads.
3. User without phone opens checkout -> blocked with “Заполнить профиль”.
4. User opens Profile -> Профиль tab -> adds phone.
5. User returns checkout -> can create order.
6. User sees payment page polling.
7. Demo payment complete -> success -> profile coupons.
8. User opens Profile -> Заказы -> sees created/paid order.
9. User opens pending order from orders tab -> continues payment.
10. Payment timeout copy tells user the order is saved.
11. Profile help tab shows next-stage cards for refunds, complaints, reviews, notifications.
```

- [ ] Update docs to reflect current reality:

```text
Profile is no longer only “my coupons”; it is the user post-purchase hub.
Checkout requires auth + email + phone.
Order recovery is done from Profile -> Заказы.
Refunds/complaints/notifications are planned next stages unless already fully implemented by a separate plan.
```

---

## Final Verification

- [ ] Run frontend build:

```bash
cd frontend/web-app && npm run build
```

- [ ] Run identity-service tests:

```bash
./gradlew :services:identity-service:test
```

- [ ] If payment/order code is changed, run:

```bash
./gradlew :services:order-service:test :services:payment-service:test
```

- [ ] Manual smoke test:

```text
Login -> profile -> edit phone -> add coupon to cart -> checkout -> payment -> complete demo payment -> profile coupons -> profile orders.
```

- [ ] Confirm these stage-1 risks are closed:

```text
Checkout no longer dead-ends when phone is missing.
User can update phone from profile.
User can see order/payment history.
Pending payment can be resumed from profile.
Payment timeout/failed states tell the user what to do next.
Profile clearly points to next support areas without pretending they are done.
```

