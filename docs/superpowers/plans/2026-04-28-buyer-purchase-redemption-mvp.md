# Buyer Purchase To Redemption MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the full buyer flow testable end-to-end: select coupon option, checkout, demo/payment completion, purchased coupon appears in profile with PIN/QR data, partner/cashier redeems by PIN or QR, duplicate and wrong-merchant redemptions are blocked.

**Architecture:** The system already has the main pieces, so this stage is hardening and UX completion, not a rewrite. Backend must own all critical business validation in `order-service` and `payment-service`; frontend must make the buyer's PIN/QR and partner redemption path clear enough for manual MVP testing.

**Tech Stack:** Spring Boot services with JUnit 5/Mockito, RabbitMQ events, React 19 + Vite frontend apps, React Query, Zustand, Ant Design in partner/admin apps.

---

## Required Context And Skills

Before changing code, read:

- `AGENTS.md`
- `docs/README.md`
- `docs/product/flows/coupon-flow.md`
- `docs/backend/README.md`
- `docs/frontend/README.md`
- `docs/qa/README.md`

Use these skills when relevant:

- `superpowers:test-driven-development`
- `superpowers:systematic-debugging` for failing tests or unexpected behavior
- `superpowers:verification-before-completion` before claiming the stage is complete
- `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
- `services/.agent/skills/api-design-principles/SKILL.md`
- `frontend/.agent/skills/react/SKILL.md`
- `frontend/.agent/skills/react-hook-form-zod/SKILL.md`

## Current Code Map

Backend:

- `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java`
- `services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java`
- `services/order-service/src/main/java/uz/topdim/order/service/PartnerAccessResolver.java`
- `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- `services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java`
- `services/payment-service/src/main/java/uz/topdim/payment/service/PaymentService.java`
- `services/payment-service/src/test/java/uz/topdim/payment/service/PaymentServiceTest.java`
- `services/payment-service/src/test/java/uz/topdim/payment/listener/OrderCreatedListenerTest.java`

Frontend:

- `frontend/web-app/src/pages/CouponDetailPage.tsx`
- `frontend/web-app/src/components/coupon-detail/CouponVariantsSection.tsx`
- `frontend/web-app/src/pages/CheckoutPage.tsx`
- `frontend/web-app/src/pages/PaymentPage.tsx`
- `frontend/web-app/src/pages/ProfilePage.tsx`
- `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- `frontend/web-app/src/components/profile/PurchasedCouponCard.css`
- `frontend/web-app/src/api/orders.ts`
- `frontend/partner/src/pages/RedeemPage.tsx`
- `frontend/partner/src/api.ts`

---

## Business Rules To Preserve

- Checkout must create an order only from the authenticated user's backend cart.
- Checkout must revalidate canonical coupon data in backend: coupon status, option status, buyUntil, quantity limit, current price, merchant data, useUntil.
- Payment completion must be idempotent: duplicate callback or duplicate demo-complete cannot generate duplicate purchased coupons.
- Purchased coupon generation must copy merchant and expiry data from order items.
- Purchased coupon must expose PIN (`couponCode`) and QR token (`qrToken`) to the buyer.
- Redemption must only work for the merchant resolved from the partner/cashier access context.
- Cashier must not manually choose a branch; branch comes from `PartnerAccessResolver`.
- PIN/QR redemption can be used only once.
- Wrong merchant, used coupon, expired coupon, missing coupon, and missing merchant must produce business errors.

---

### Task 1: Lock Checkout Revalidation With Regression Tests

**Files:**
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- Expected production file only if tests expose a gap: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`

- [ ] **Step 1: Add regression tests for missing purchase snapshot and stale option**

Add these tests in `OrderServiceTest` near existing `createOrder_*` tests:

```java
@Test
@DisplayName("Checkout: coupon-service returns empty snapshot -> rejects without creating order")
void createOrder_missingSnapshot_rejectsWithoutSideEffects() {
    CartItem item = CartItem.builder()
            .couponOfferId(5L).couponOptionId(3L)
            .couponTitle("Old title").optionTitle("Old option")
            .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
            .gift(false).build();
    Cart cart = Cart.builder().id(1L).userId(10L)
            .items(new ArrayList<>(List.of(item))).build();

    when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(ApiResponse.success(null));

    assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Купон недоступен для покупки");

    verify(orderRepository, never()).save(any(Order.class));
    verify(cartRepository, never()).save(argThat(savedCart -> savedCart.getItems().isEmpty()));
    verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
}

@Test
@DisplayName("Checkout: option became inactive -> rejects without creating order")
void createOrder_optionInactive_rejectsWithoutSideEffects() {
    CartItem item = CartItem.builder()
            .couponOfferId(5L).couponOptionId(3L)
            .couponTitle("Old title").optionTitle("Old option")
            .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
            .gift(false).build();
    Cart cart = Cart.builder().id(1L).userId(10L)
            .items(new ArrayList<>(List.of(item))).build();

    CouponPurchaseSnapshot snapshot = activeSnapshot();
    snapshot.setOptionStatus("INACTIVE");

    when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

    assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Опция купона недоступна для покупки");

    verify(orderRepository, never()).save(any(Order.class));
    verify(cartRepository, never()).save(argThat(savedCart -> savedCart.getItems().isEmpty()));
    verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
}
```

- [ ] **Step 2: Add boundary tests for buyUntil and quantity limit**

Add these tests:

```java
@Test
@DisplayName("Checkout: buyUntil already passed -> rejects without creating order")
void createOrder_buyUntilExpired_rejectsWithoutSideEffects() {
    CartItem item = CartItem.builder()
            .couponOfferId(5L).couponOptionId(3L)
            .couponTitle("SPA").optionTitle("Standard")
            .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
            .gift(false).build();
    Cart cart = Cart.builder().id(1L).userId(10L)
            .items(new ArrayList<>(List.of(item))).build();

    CouponPurchaseSnapshot snapshot = activeSnapshot();
    snapshot.setBuyUntil(LocalDateTime.now().minusMinutes(1));

    when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

    assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Срок покупки купона истёк");

    verify(orderRepository, never()).save(any(Order.class));
    verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
}

@Test
@DisplayName("Checkout: requested quantity exceeds remaining limit -> rejects without creating order")
void createOrder_quantityLimitExceeded_rejectsWithoutSideEffects() {
    CartItem item = CartItem.builder()
            .couponOfferId(5L).couponOptionId(3L)
            .couponTitle("SPA").optionTitle("Standard")
            .unitPrice(BigDecimal.valueOf(99000)).quantity(9)
            .gift(false).build();
    Cart cart = Cart.builder().id(1L).userId(10L)
            .items(new ArrayList<>(List.of(item))).build();

    CouponPurchaseSnapshot snapshot = activeSnapshot();
    snapshot.setQuantityLimit(10);
    snapshot.setQuantitySold(2);

    when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

    assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Недостаточно купонов в наличии");

    verify(orderRepository, never()).save(any(Order.class));
    verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
}
```

- [ ] **Step 3: Run focused order-service test**

Run:

```bash
./gradlew :services:order-service:test --tests uz.topdim.order.service.OrderServiceTest
```

Expected: tests pass if the current implementation already satisfies the rules. If a new test fails, fix only the corresponding rule in `OrderService.java` and rerun the same command.

---

### Task 2: Lock Purchased Coupon Generation And Sale Registration

**Files:**
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- Expected production file only if tests expose a gap: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`

- [ ] **Step 1: Add test that coupon-service sale registration failure does not break purchased coupons**

Add this test near existing `generateCoupons_*` tests:

```java
@Test
@DisplayName("Генерация купонов: registerSale failure logs but still returns created purchased coupons")
void generateCoupons_registerSaleFailure_stillReturnsPurchasedCoupons() {
    OrderItem item = OrderItem.builder()
            .couponOfferId(1L).couponOptionId(2L)
            .couponTitle("SPA").optionTitle("Standard")
            .unitPrice(BigDecimal.valueOf(150000)).quantity(1)
            .merchantId(77L)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .gift(false).build();
    Order order = Order.builder().id(100L).userId(10L)
            .userEmail("a@b.com").userPhone("+998901234567")
            .items(List.of(item)).status(OrderStatus.PENDING).build();

    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
    when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of());
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> {
        PurchasedCoupon coupon = inv.getArgument(0);
        coupon.setId(501L);
        return coupon;
    });
    doThrow(new RuntimeException("coupon-service unavailable"))
            .when(couponClient)
            .registerSale(eq(1L), eq(2L), any());

    List<PurchasedCoupon> result = orderService.generatePurchasedCoupons(100L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(PurchasedCouponStatus.ACTIVE);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    verify(rabbitTemplate).convertAndSend(eq("coupon.exchange"), eq("coupon.purchased"), any(Object.class));
}
```

- [ ] **Step 2: Add test that idempotency skips sale registration too**

Add this test:

```java
@Test
@DisplayName("Генерация купонов: existing purchased coupons skips registerSale to avoid duplicate sale count")
void generateCoupons_existingCoupons_skipsSaleRegistration() {
    Order order = Order.builder().id(100L).userId(10L)
            .status(OrderStatus.PAID).items(List.of()).build();
    PurchasedCoupon existing = PurchasedCoupon.builder()
            .id(1L)
            .order(order)
            .couponCode("CP-EXISTING")
            .status(PurchasedCouponStatus.ACTIVE)
            .build();

    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
    when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of(existing));

    List<PurchasedCoupon> result = orderService.generatePurchasedCoupons(100L);

    assertThat(result).containsExactly(existing);
    verify(couponClient, never()).registerSale(anyLong(), anyLong(), any());
    verify(rabbitTemplate, never()).convertAndSend(eq("coupon.exchange"), eq("coupon.purchased"), any(Object.class));
}
```

- [ ] **Step 3: Run focused order-service test**

Run:

```bash
./gradlew :services:order-service:test --tests uz.topdim.order.service.OrderServiceTest
```

Expected: pass. If `registerSale` is called in the idempotent branch, update `OrderService.generatePurchasedCoupons` so the existing-coupons return happens before any event or sale registration.

---

### Task 3: Lock Demo Payment Completion Idempotency

**Files:**
- Modify: `services/payment-service/src/test/java/uz/topdim/payment/service/PaymentServiceTest.java`
- Expected production file only if tests expose a gap: `services/payment-service/src/main/java/uz/topdim/payment/service/PaymentService.java`

- [ ] **Step 1: Add demoComplete success test**

Add this test:

```java
@Test
@DisplayName("demoComplete: pending payment -> COMPLETED and publishes PaymentCompletedEvent")
void demoComplete_pendingPayment_completesAndPublishesEvent() {
    Payment pending = Payment.builder()
            .id(1L)
            .orderId(100L)
            .userId(10L)
            .amount(BigDecimal.valueOf(150000))
            .currency("UZS")
            .provider(PaymentProvider.PAYME)
            .status(PaymentStatus.PENDING)
            .build();

    when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(pending));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    Payment result = paymentService.demoComplete(100L);

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.getCompletedAt()).isNotNull();
    assertThat(result.getTransactionId()).startsWith("DEMO-");
    verify(rabbitTemplate).convertAndSend(eq("payment.exchange"), eq("payment.completed"), any(Object.class));
}
```

- [ ] **Step 2: Add demoComplete duplicate and invalid-status tests**

Add these tests:

```java
@Test
@DisplayName("demoComplete: already COMPLETED payment -> returns existing without republishing")
void demoComplete_completedPayment_doesNotRepublishEvent() {
    Payment completed = Payment.builder()
            .id(1L)
            .orderId(100L)
            .userId(10L)
            .amount(BigDecimal.valueOf(150000))
            .currency("UZS")
            .provider(PaymentProvider.PAYME)
            .status(PaymentStatus.COMPLETED)
            .transactionId("DEMO-OLD123")
            .completedAt(LocalDateTime.now().minusMinutes(5))
            .build();

    when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(completed));

    Payment result = paymentService.demoComplete(100L);

    assertThat(result).isSameAs(completed);
    assertThat(result.getTransactionId()).isEqualTo("DEMO-OLD123");
    verify(paymentRepository, never()).save(any(Payment.class));
    verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
}

@Test
@DisplayName("demoComplete: failed payment -> business error and no event")
void demoComplete_failedPayment_throwsWithoutPublishingEvent() {
    Payment failed = Payment.builder()
            .id(1L)
            .orderId(100L)
            .userId(10L)
            .amount(BigDecimal.valueOf(150000))
            .currency("UZS")
            .provider(PaymentProvider.PAYME)
            .status(PaymentStatus.FAILED)
            .build();

    when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(failed));

    assertThatThrownBy(() -> paymentService.demoComplete(100L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Невозможно завершить платёж");

    verify(paymentRepository, never()).save(any(Payment.class));
    verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
}
```

- [ ] **Step 3: Run focused payment-service test**

Run:

```bash
./gradlew :services:payment-service:test --tests uz.topdim.payment.service.PaymentServiceTest
```

Expected: pass. If a test fails, fix only the matching `demoComplete` behavior.

---

### Task 4: Lock Redemption Access Context And QR Controller Behavior

**Files:**
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java`
- Expected production files only if tests expose a gap:
  - `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
  - `services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java`

- [ ] **Step 1: Add service test that redemption stores cashier context**

Add this test near redemption tests in `OrderServiceTest`:

```java
@Test
@DisplayName("Погашение: сохраняет staffId, merchantLocationId and method from access context")
void redeemCoupon_withAccessContext_savesRedemptionContext() {
    PurchasedCoupon coupon = PurchasedCoupon.builder()
            .id(1L)
            .couponCode("CP-CONTEXT1")
            .merchantId(77L)
            .expiresAt(LocalDateTime.now().plusDays(7))
            .status(PurchasedCouponStatus.ACTIVE)
            .build();

    when(purchasedCouponRepository.findByCouponCode("CP-CONTEXT1")).thenReturn(Optional.of(coupon));
    when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> inv.getArgument(0));
    when(redemptionRepository.save(any(Redemption.class))).thenAnswer(inv -> inv.getArgument(0));

    orderService.redeemCoupon("CP-CONTEXT1", 77L, "Кассир Али", 200L, 5L, "PIN");

    verify(redemptionRepository).save(argThat(redemption ->
            redemption.getMerchantId().equals(77L)
                    && redemption.getMerchantLocationId().equals(200L)
                    && redemption.getStaffId().equals(5L)
                    && redemption.getRedeemedByStaff().equals("Кассир Али")
                    && redemption.getRedeemMethod().equals("PIN")
    ));
}
```

- [ ] **Step 2: Add QR controller test that uses trusted access context**

Add this test to `PartnerControllerTest`:

```java
@Test
@DisplayName("POST /api/v1/partner/redemptions/qr: cashier uses trusted context and not request merchant data")
void redeemByQr_cashierUsesTrustedAccessContext() throws Exception {
    PartnerAccessContext ctx = PartnerAccessContext.builder()
            .merchantId(77L)
            .merchantLocationId(200L)
            .staffId(5L)
            .staffName("Кассир Али")
            .role("CASHIER")
            .build();
    PurchasedCoupon coupon = PurchasedCoupon.builder()
            .id(501L)
            .couponTitle("SPA")
            .optionTitle("Standard")
            .couponCode("CP-TEST5678")
            .merchantId(77L)
            .merchantName("SPA Oasis")
            .status(PurchasedCouponStatus.USED)
            .usedAt(LocalDateTime.now())
            .build();

    when(partnerAccessResolver.resolveForRedemption(100L)).thenReturn(ctx);
    when(orderService.redeemByQrToken("qr-token-abc", 77L, "Кассир Али", 200L, 5L)).thenReturn(coupon);

    mockMvc.perform(post("/api/v1/partner/redemptions/qr")
                    .header("X-User-Id", "100")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"qrToken\":\"qr-token-abc\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("USED"))
            .andExpect(jsonPath("$.data.couponCode").value("CP-TEST5678"));

    verify(orderService).redeemByQrToken("qr-token-abc", 77L, "Кассир Али", 200L, 5L);
}
```

Imports likely needed:

```java
import java.time.LocalDateTime;
```

- [ ] **Step 3: Run focused order-service controller and service tests**

Run:

```bash
./gradlew :services:order-service:test --tests uz.topdim.order.service.OrderServiceTest --tests uz.topdim.order.controller.PartnerControllerTest
```

Expected: pass. If controller test fails because response mapping lacks `usedAt`, fix `OrderService.mapToRedeemResponse`.

---

### Task 5: Make Buyer Profile PIN/QR Usable For Manual Testing

**Files:**
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.css`
- Modify only if needed: `frontend/web-app/src/api/orders.ts`

Business reason: The buyer currently sees PIN clearly, but QR token is only mentioned as available. For MVP manual testing, cashier needs a scannable or copyable QR value. Do not add a QR dependency in this stage; show and copy the QR token as a fallback.

- [ ] **Step 1: Extend `PurchasedCouponCard` with safe copy helpers**

In `PurchasedCouponCard.tsx`, replace the current `copyCode` helper with:

```tsx
const copyText = async (value?: string) => {
  if (!value) {
    return;
  }
  await navigator.clipboard.writeText(value);
};
```

Update the PIN copy button:

```tsx
<button type="button" onClick={() => copyText(coupon.couponCode)}>
  <Copy size={16} />
  Скопировать
</button>
```

- [ ] **Step 2: Render QR token value for ACTIVE coupons**

Below the PIN block, add:

```tsx
{isActive && coupon.qrToken ? (
  <div className="purchased-coupon-card__qr-box">
    <div>
      <span className="purchased-coupon-card__label">QR-токен для проверки</span>
      <code>{coupon.qrToken}</code>
    </div>
    <button type="button" onClick={() => copyText(coupon.qrToken)}>
      <Copy size={16} />
      Скопировать QR
    </button>
  </div>
) : null}
```

Keep the existing note in the footer, but change its text to:

```tsx
QR можно показать партнёру или скопировать как токен
```

- [ ] **Step 3: Add CSS for QR token block**

In `PurchasedCouponCard.css`, add styles matching the existing card:

```css
.purchased-coupon-card__qr-box {
  margin-top: 0.75rem;
  padding: 1rem;
  border: 1px dashed rgba(34, 197, 94, 0.35);
  border-radius: 16px;
  background: rgba(34, 197, 94, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.purchased-coupon-card__qr-box code {
  display: block;
  max-width: 260px;
  margin-top: 0.25rem;
  padding: 0.35rem 0.5rem;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.08);
  color: var(--text-primary);
  font-size: 0.82rem;
  word-break: break-all;
}

.purchased-coupon-card__qr-box button {
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .purchased-coupon-card__qr-box {
    align-items: stretch;
    flex-direction: column;
  }
}
```

- [ ] **Step 4: Build web app**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: TypeScript and Vite build pass.

---

### Task 6: Make Partner Redemption Page Clear For PIN And QR Testing

**Files:**
- Modify: `frontend/partner/src/pages/RedeemPage.tsx`

Business reason: Partner/cashier must easily test both PIN and QR redemption. QR scanning can remain manual token input for this MVP, but success/error states must be persistent enough for QA.

- [ ] **Step 1: Extend `RedeemResult` type**

Change the type in `RedeemPage.tsx` to:

```tsx
interface RedeemResult {
  purchasedCouponId: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  merchantName: string;
  status: string;
  expiresAt?: string;
  usedAt?: string;
}
```

- [ ] **Step 2: Add persistent error state**

Add state:

```tsx
const [errorText, setErrorText] = useState('');
```

At the start of both `handlePinRedeem` and `handleQrRedeem`, clear it:

```tsx
setErrorText('');
```

In both catch blocks, replace only the message logic with:

```tsx
const msg = err.response?.data?.message || 'Ошибка погашения';
setErrorText(msg);
message.error(msg);
```

- [ ] **Step 3: Show usedAt/expiresAt on success**

In `Result.subTitle`, under merchant name, add:

```tsx
{result.usedAt ? <Text type="secondary">Использован: {new Date(result.usedAt).toLocaleString('ru-RU')}</Text> : null}
{result.expiresAt ? <Text type="secondary">Действовал до: {new Date(result.expiresAt).toLocaleString('ru-RU')}</Text> : null}
```

- [ ] **Step 4: Show persistent error below tabs**

Import `Alert` from Ant Design:

```tsx
import { Card, Input, Button, Typography, message, Result, Space, Tag, Tabs, Alert } from 'antd';
```

Below `<Tabs items={tabItems} centered size="large" />`, add:

```tsx
{errorText ? (
  <Alert
    style={{ marginTop: 16 }}
    type="error"
    showIcon
    message="Не удалось погасить купон"
    description={errorText}
  />
) : null}
```

- [ ] **Step 5: Build partner app**

Run:

```bash
cd frontend/partner
npm run build
```

Expected: TypeScript and Vite build pass.

---

### Task 7: Update QA Manual Checklist For This Stage

**Files:**
- Create: `docs/qa/buyer-purchase-redemption-checklist.md`
- Modify: `docs/qa/README.md`

- [ ] **Step 1: Create checklist**

Create `docs/qa/buyer-purchase-redemption-checklist.md` with:

```markdown
# Buyer Purchase To Redemption Checklist

Дата: 2026-04-28.

Цель: вручную пройти полный MVP flow покупки и погашения купона.

## Preconditions

- Есть активный покупатель `USER`.
- Есть активный партнёр `PARTNER`.
- Есть кассир `PARTNER_CASHIER`, привязанный к merchant/location.
- Есть ACTIVE купон с минимум одним ACTIVE вариантом.
- У купона есть merchantId, useUntil, buyUntil и лимиты, достаточные для покупки.
- Payment service работает в demo mode или доступен demo-complete endpoint.

## Happy Path

- [ ] Покупатель открывает каталог купонов.
- [ ] Покупатель открывает детальную страницу ACTIVE купона.
- [ ] Покупатель выбирает вариант купона.
- [ ] Покупатель добавляет вариант в корзину.
- [ ] Покупатель открывает checkout.
- [ ] Checkout показывает email/phone пользователя.
- [ ] Покупатель создаёт order.
- [ ] Покупатель попадает на payment page.
- [ ] Demo payment подтверждается.
- [ ] Payment page показывает success.
- [ ] Покупатель открывает профиль.
- [ ] Купон отображается в ACTIVE tab.
- [ ] Купон показывает PIN.
- [ ] Купон показывает QR token или понятный QR fallback.
- [ ] Кассир открывает partner redeem page.
- [ ] Кассир гасит купон по PIN.
- [ ] Buyer profile после обновления показывает купон в USED.
- [ ] Partner dashboard/history показывает redemption.

## QR Path

- [ ] Покупатель покупает второй купон.
- [ ] Покупатель открывает QR token в профиле.
- [ ] Кассир вводит QR token в partner redeem page.
- [ ] Купон гасится успешно.

## Negative Cases

- [ ] Повторное погашение PIN возвращает ошибку.
- [ ] Повторное погашение QR token возвращает ошибку.
- [ ] Кассир другого мерчанта не может погасить купон.
- [ ] Истёкший купон не гасится и получает статус EXPIRED.
- [ ] Пользователь не может купить купон после buyUntil.
- [ ] Пользователь не может купить inactive/sold-out option.
- [ ] Checkout с пустой корзиной не создаёт order.
- [ ] Demo-complete повторным вызовом не создаёт дубли purchased coupons.

## Evidence To Capture

- orderId;
- payment transactionId;
- purchasedCouponId;
- couponCode;
- merchantId;
- cashier userId;
- screenshot/profile before redemption;
- screenshot/redeem success;
- screenshot/profile after redemption.
```

- [ ] **Step 2: Link checklist from QA README**

Add a row to the docs table in `docs/qa/README.md`:

```markdown
| [buyer-purchase-redemption-checklist.md](buyer-purchase-redemption-checklist.md) | полный MVP чеклист покупки и погашения |
```

- [ ] **Step 3: Verify docs links**

Run:

```bash
rg -n "buyer-purchase-redemption-checklist|manual-test-plan|purchase-flow-checklist" docs/qa
```

Expected: the new checklist is linked from `docs/qa/README.md`.

---

### Task 8: Full Verification

**Files:**
- All changed files.

- [ ] **Step 1: Run backend tests**

```bash
./gradlew :services:order-service:test
```

Expected: order-service tests pass.

Run:

```bash
./gradlew :services:payment-service:test
```

Expected: payment-service tests pass.

- [ ] **Step 2: Run frontend builds**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: build passes.

Run:

```bash
cd frontend/partner
npm run build
```

Expected: build passes.

- [ ] **Step 3: Check docs and git status**

Run:

```bash
rg -n "T[O]DO|T[B]D|file:/{2}" docs/qa docs/frontend docs/backend docs/product docs/README.md
```

Expected: no matches.

Run:

```bash
git status --short
```

Expected: only intentional changes from this stage plus existing docs reorganization changes.

---

## Manual QA Handoff

After tests and builds pass, manually test using:

- `docs/qa/buyer-purchase-redemption-checklist.md`
- `docs/qa/manual-test-plan.md`

Minimum acceptance:

- One coupon bought by buyer appears as ACTIVE in profile.
- Same coupon can be redeemed once by correct partner/cashier.
- Same coupon cannot be redeemed second time.
- QR path works with QR token fallback.
- Wrong merchant redemption is blocked by backend.
