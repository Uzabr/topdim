# Purchased Coupon Lifecycle Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the post-payment coupon lifecycle safe: one payment creates one set of purchased coupons, purchased coupons expire correctly, and only the owning merchant can redeem them.

**Architecture:** Build on the purchase-flow hardening work. `order-service` stores purchase-time facts needed after payment: merchant ownership and `expiresAt`. Redemption validates against those stored facts, not request body trust. Expiration is handled server-side before reads and redeem operations, so profile tabs and partner redemption see consistent statuses without adding a new scheduler in this step.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, RabbitMQ listener path, JUnit 5, Mockito, MockMvc, React, TypeScript.

---

## Scope

Implement only purchased-coupon lifecycle hardening after `purchase-flow-hardening` lands:

- Idempotent `generatePurchasedCoupons(orderId)`.
- `expiresAt` copied from coupon `useUntil` into purchased coupons.
- Merchant ownership copied into purchased coupons.
- Redemption uses `X-Merchant-Id` from gateway/controller, not client request body.
- Redemption rejects expired coupons and coupons owned by another merchant.
- Reads keep `ACTIVE`/`EXPIRED` status accurate.
- Minimal Profile UI correctness for expiry/used date display.

Do not implement:

- Payment provider changes.
- Refund money movement.
- Merchant module expansion.
- Bazaar/directory work.
- New QR scanner UI.

## Required Local Skills

- Root: `.agent/skills/test-driven-development/SKILL.md`
- Root: `.agent/skills/systematic-debugging/SKILL.md`
- Root: `.agent/skills/verification-before-completion/SKILL.md`
- Services: `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
- Services: `services/.agent/skills/api-design-principles/SKILL.md`
- Frontend: `frontend/.agent/skills/react/SKILL.md`
- Frontend: `frontend/.agent/skills/frontend-design/SKILL.md`

Follow TDD for backend changes. For every behavior change, first write the failing test and run it to confirm the failure is meaningful.

## Business Rules

- Payment completion must be idempotent. Retrying `generatePurchasedCoupons(orderId)` must not create duplicate purchased coupons.
- Each purchased coupon must have `expiresAt`, copied from the coupon option/order item purchase terms.
- Each purchased coupon must know its owning `merchantId`.
- A coupon can be redeemed only when status is `ACTIVE`, `expiresAt` is not in the past, and the redeeming merchant owns it.
- `merchantId` for redemption must come from trusted gateway header `X-Merchant-Id`, not from request JSON.
- Expired coupons must not remain visible in the user's `ACTIVE` tab after order-service reads.
- Repeated redemption of the same coupon must stay rejected and must not create a second redemption row.

## Files

- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponPurchaseSnapshotResponse.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/client/CouponPurchaseSnapshot.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/OrderItem.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/PurchasedCoupon.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/dto/RedeemCouponRequest.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/PurchasedCouponRepository.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Create: `services/order-service/src/main/resources/db/migration/V8__purchased_coupon_lifecycle_fields.sql`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- Create: `services/order-service/src/test/java/uz/topdim/order/controller/RedeemCouponControllerTest.java`
- Modify: `frontend/web-app/src/api/orders.ts`
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`

---

### Task 1: Carry Merchant And Expiry Data From Coupon Snapshot

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponPurchaseSnapshotResponse.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/client/CouponPurchaseSnapshot.java`

- [ ] **Step 1: Write failing coupon-service snapshot test**

Extend the existing `InternalCouponPurchaseControllerTest` from the purchase-flow plan. Add assertions:

```java
.andExpect(jsonPath("$.data.merchantId").value(77))
.andExpect(jsonPath("$.data.useUntil").value("2027-07-01T12:00:00"));
```

Build the mocked response with:

```java
.merchantId(77L)
.useUntil(LocalDateTime.of(2027, 7, 1, 12, 0))
```

- [ ] **Step 2: Run coupon-service test to verify RED**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalCouponPurchaseControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL if `merchantId` is not present in snapshot DTO/mapping yet.

- [ ] **Step 3: Add `merchantId` to coupon snapshot DTO**

Update `CouponPurchaseSnapshotResponse`:

```java
private Long merchantId;
```

Update `CouponOfferService.getPurchaseSnapshot(...)`:

```java
.merchantId(offer.getMerchant() != null ? offer.getMerchant().getId() : null)
```

- [ ] **Step 4: Mirror field in order-service Feign DTO**

Update `CouponPurchaseSnapshot`:

```java
private Long merchantId;
```

- [ ] **Step 5: Run focused tests**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalCouponPurchaseControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

---

### Task 2: Persist Purchase-Time Merchant And Expiry On Orders

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/OrderItem.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/PurchasedCoupon.java`
- Create: `services/order-service/src/main/resources/db/migration/V8__purchased_coupon_lifecycle_fields.sql`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`

- [ ] **Step 1: Write failing checkout persistence test**

In `OrderServiceTest`, add:

```java
@Test
@DisplayName("Checkout: order item stores merchantId and expiresAt from canonical coupon snapshot")
void createOrder_storesMerchantAndExpiresAtOnOrderItem() {
    CartItem item = CartItem.builder()
            .couponOfferId(5L).couponOptionId(3L)
            .couponTitle("Old title").optionTitle("Old option")
            .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
            .gift(false).build();
    Cart cart = Cart.builder().id(1L).userId(10L)
            .items(new ArrayList<>(List.of(item))).build();

    CouponPurchaseSnapshot snapshot = activeSnapshot();
    snapshot.setMerchantId(77L);
    snapshot.setUseUntil(LocalDateTime.of(2027, 7, 1, 12, 0));

    when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
        Order o = inv.getArgument(0);
        o.setId(100L);
        return o;
    });
    when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

    Order order = orderService.createOrder(10L, "user@test.com", "+998901234567");

    assertThat(order.getItems()).hasSize(1);
    assertThat(order.getItems().get(0).getMerchantId()).isEqualTo(77L);
    assertThat(order.getItems().get(0).getExpiresAt())
            .isEqualTo(LocalDateTime.of(2027, 7, 1, 12, 0));
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because `OrderItem.merchantId` and `OrderItem.expiresAt` do not exist yet.

- [ ] **Step 3: Add entity fields**

Add to `OrderItem`:

```java
@Column(name = "merchant_id")
private Long merchantId;

@Column(name = "expires_at")
private LocalDateTime expiresAt;
```

Add to `PurchasedCoupon`:

```java
@Column(name = "merchant_id")
private Long merchantId;
```

- [ ] **Step 4: Add migration**

Create `V8__purchased_coupon_lifecycle_fields.sql`:

```sql
ALTER TABLE order_items
    ADD COLUMN merchant_id BIGINT,
    ADD COLUMN expires_at TIMESTAMP;

ALTER TABLE purchased_coupons
    ADD COLUMN merchant_id BIGINT;

CREATE INDEX idx_order_items_merchant ON order_items(merchant_id);
CREATE INDEX idx_purchased_coupons_merchant ON purchased_coupons(merchant_id);
CREATE INDEX idx_purchased_coupons_expires_at ON purchased_coupons(expires_at);
```

- [ ] **Step 5: Store fields during checkout**

When building each `OrderItem` from snapshot in `createOrder`, set:

```java
.merchantId(snapshot.getMerchantId())
.expiresAt(snapshot.getUseUntil())
```

- [ ] **Step 6: Run test to verify GREEN**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

---

### Task 3: Make Purchased Coupon Generation Idempotent And Complete

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/PurchasedCouponRepository.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`

- [ ] **Step 1: Add failing idempotency test**

Add repository mock usage:

```java
when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of(existing));
```

Add test:

```java
@Test
@DisplayName("Генерация купонов: повторный вызов для уже оплаченного заказа возвращает существующие купоны без дублей")
void generateCoupons_existingCoupons_returnsExistingWithoutCreatingDuplicates() {
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
    verify(purchasedCouponRepository, never()).save(any(PurchasedCoupon.class));
    verify(rabbitTemplate, never()).convertAndSend(eq("coupon.exchange"), eq("coupon.purchased"), any(Object.class));
}
```

- [ ] **Step 2: Add failing completeness test**

Add:

```java
@Test
@DisplayName("Генерация купонов: копирует merchantId и expiresAt из order item")
void generateCoupons_copiesMerchantAndExpiresAt() {
    OrderItem item = OrderItem.builder()
            .couponOfferId(1L).couponOptionId(2L)
            .couponTitle("SPA").optionTitle("Standard")
            .unitPrice(BigDecimal.valueOf(150000)).quantity(1)
            .merchantId(77L)
            .expiresAt(LocalDateTime.of(2027, 7, 1, 12, 0))
            .gift(false).build();
    Order order = Order.builder().id(100L).userId(10L)
            .userEmail("a@b.com").userPhone("123")
            .items(List.of(item)).status(OrderStatus.PENDING).build();

    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
    when(purchasedCouponRepository.findByOrderId(100L)).thenReturn(List.of());
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> {
        PurchasedCoupon c = inv.getArgument(0);
        c.setId(1L);
        return c;
    });

    List<PurchasedCoupon> result = orderService.generatePurchasedCoupons(100L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getMerchantId()).isEqualTo(77L);
    assertThat(result.get(0).getExpiresAt()).isEqualTo(LocalDateTime.of(2027, 7, 1, 12, 0));
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because generation does not short-circuit existing coupons and does not copy merchant/expiry yet.

- [ ] **Step 4: Implement idempotency**

At the start of `generatePurchasedCoupons` after loading order:

```java
List<PurchasedCoupon> existingCoupons = purchasedCouponRepository.findByOrderId(orderId);
if (!existingCoupons.isEmpty()) {
    return existingCoupons;
}
```

- [ ] **Step 5: Copy merchant and expiry into purchased coupons**

When building `PurchasedCoupon`, add:

```java
.merchantId(item.getMerchantId())
.expiresAt(item.getExpiresAt())
```

- [ ] **Step 6: Run tests to verify GREEN**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

---

### Task 4: Enforce Redemption Ownership And Expiration

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/dto/RedeemCouponRequest.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- Create: `services/order-service/src/test/java/uz/topdim/order/controller/RedeemCouponControllerTest.java`

- [ ] **Step 1: Add failing service tests**

Add:

```java
@Test
@DisplayName("Погашение: чужой merchantId → IllegalStateException")
void redeemCoupon_wrongMerchant_throwsException() {
    PurchasedCoupon coupon = PurchasedCoupon.builder()
            .id(1L)
            .couponCode("CP-TEST1234")
            .merchantId(77L)
            .expiresAt(LocalDateTime.now().plusDays(1))
            .status(PurchasedCouponStatus.ACTIVE)
            .build();

    when(purchasedCouponRepository.findByCouponCode("CP-TEST1234")).thenReturn(Optional.of(coupon));

    assertThatThrownBy(() -> orderService.redeemCoupon("CP-TEST1234", 88L, "Анна"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Купон принадлежит другому мерчанту");

    verify(redemptionRepository, never()).save(any(Redemption.class));
}

@Test
@DisplayName("Погашение: expired coupon переводится в EXPIRED и не погашается")
void redeemCoupon_expiredCoupon_marksExpiredAndThrows() {
    PurchasedCoupon coupon = PurchasedCoupon.builder()
            .id(1L)
            .couponCode("CP-OLD1234")
            .merchantId(77L)
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .status(PurchasedCouponStatus.ACTIVE)
            .build();

    when(purchasedCouponRepository.findByCouponCode("CP-OLD1234")).thenReturn(Optional.of(coupon));
    when(purchasedCouponRepository.save(any(PurchasedCoupon.class))).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(() -> orderService.redeemCoupon("CP-OLD1234", 77L, "Анна"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Срок действия купона истёк");

    assertThat(coupon.getStatus()).isEqualTo(PurchasedCouponStatus.EXPIRED);
    verify(redemptionRepository, never()).save(any(Redemption.class));
}
```

- [ ] **Step 2: Add failing controller test for trusted merchant header**

Create `RedeemCouponControllerTest`:

```java
@ExtendWith(MockitoExtension.class)
class RedeemCouponControllerTest {

    @Mock private OrderService orderService;
    @InjectMocks private OrderController orderController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/orders/redeem: uses X-Merchant-Id header instead of request merchantId")
    void redeemCoupon_usesTrustedMerchantHeader() throws Exception {
        PurchasedCoupon coupon = PurchasedCoupon.builder()
                .id(1L)
                .couponCode("CP-TEST1234")
                .status(PurchasedCouponStatus.USED)
                .build();
        when(orderService.redeemCoupon("CP-TEST1234", 77L, "Анна")).thenReturn(coupon);

        mockMvc.perform(post("/api/v1/orders/redeem")
                        .header("X-Merchant-Id", "77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponCode": "CP-TEST1234",
                                  "merchantId": 999,
                                  "staffName": "Анна"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(orderService).redeemCoupon("CP-TEST1234", 77L, "Анна");
    }
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" --tests "uz.topdim.order.controller.RedeemCouponControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because redemption does not validate ownership/expiration and controller still uses request `merchantId`.

- [ ] **Step 4: Remove body merchant ownership trust**

Update `OrderController.redeemCoupon` signature:

```java
public ResponseEntity<ApiResponse<PurchasedCoupon>> redeemCoupon(
        @RequestHeader("X-Merchant-Id") Long merchantId,
        @Valid @RequestBody RedeemCouponRequest request
)
```

Call:

```java
PurchasedCoupon coupon = orderService.redeemCoupon(
        request.getCouponCode(),
        merchantId,
        request.getStaffName()
);
```

Remove `merchantId` from `RedeemCouponRequest` after controller tests are updated:

```java
private String staffName;
```

- [ ] **Step 5: Add service validations**

In `redeemCoupon` after status check:

```java
if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(LocalDateTime.now())) {
    coupon.setStatus(PurchasedCouponStatus.EXPIRED);
    purchasedCouponRepository.save(coupon);
    throw new IllegalStateException("Срок действия купона истёк");
}

if (coupon.getMerchantId() != null && !coupon.getMerchantId().equals(merchantId)) {
    throw new IllegalStateException("Купон принадлежит другому мерчанту");
}
```

Keep the existing status check before these rules so `USED`, `EXPIRED`, and `CANCELLED` remain rejected.

- [ ] **Step 6: Run tests to verify GREEN**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" --tests "uz.topdim.order.controller.RedeemCouponControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

---

### Task 5: Keep Expired Coupons Out Of ACTIVE Reads

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/PurchasedCouponRepository.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`

- [ ] **Step 1: Add repository method**

Add to `PurchasedCouponRepository`:

```java
@Modifying
@Query("""
        update PurchasedCoupon pc
           set pc.status = uz.topdim.order.entity.PurchasedCouponStatus.EXPIRED
         where pc.status = uz.topdim.order.entity.PurchasedCouponStatus.ACTIVE
           and pc.expiresAt is not null
           and pc.expiresAt < :now
        """)
int expireActiveCouponsBefore(@Param("now") LocalDateTime now);
```

- [ ] **Step 2: Add failing read test**

Add:

```java
@Test
@DisplayName("Мои купоны: перед чтением переводит просроченные ACTIVE в EXPIRED")
void getUserCouponsByStatus_expiresOverdueCouponsBeforeRead() {
    when(purchasedCouponRepository.expireActiveCouponsBefore(any(LocalDateTime.class))).thenReturn(2);
    when(purchasedCouponRepository.findByUserIdAndStatus(10L, PurchasedCouponStatus.ACTIVE)).thenReturn(List.of());

    List<PurchasedCoupon> result = orderService.getUserCouponsByStatus(10L, PurchasedCouponStatus.ACTIVE);

    assertThat(result).isEmpty();
    verify(purchasedCouponRepository).expireActiveCouponsBefore(any(LocalDateTime.class));
    verify(purchasedCouponRepository).findByUserIdAndStatus(10L, PurchasedCouponStatus.ACTIVE);
}
```

- [ ] **Step 3: Run test to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because read methods do not expire stale coupons yet.

- [ ] **Step 4: Expire before user reads**

Add helper:

```java
private void expireOverduePurchasedCoupons() {
    purchasedCouponRepository.expireActiveCouponsBefore(LocalDateTime.now());
}
```

Call it at the start of:

```java
getUserCoupons(Long userId)
getUserCouponsByStatus(Long userId, PurchasedCouponStatus status)
redeemCoupon(String couponCode, Long merchantId, String staffName)
```

Remove `@Transactional(readOnly = true)` from read methods that call expiry update, or split expiry into a separate transactional service method. Simpler MVP path: make those read methods regular `@Transactional`.

- [ ] **Step 5: Run tests to verify GREEN**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

---

### Task 6: Align Profile UI With Lifecycle Fields

**Files:**
- Modify: `frontend/web-app/src/api/orders.ts`
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`

- [ ] **Step 1: Confirm frontend status type stays aligned**

In `orders.ts`, keep the existing status union unless backend enum changes in this same branch:

```ts
status: 'ACTIVE' | 'USED' | 'EXPIRED' | 'CANCELLED';
```

- [ ] **Step 2: Show used date correctly**

In `ProfilePage.tsx`, replace current date label logic with:

```tsx
{coupon.status === 'USED' && coupon.usedAt && (
  <span className="coupon-expires">
    Использован: {new Date(coupon.usedAt).toLocaleDateString('ru-RU')}
  </span>
)}
{coupon.status !== 'USED' && coupon.expiresAt && (
  <span className="coupon-expires">
    Действует до {new Date(coupon.expiresAt).toLocaleDateString('ru-RU')}
  </span>
)}
```

- [ ] **Step 3: Run frontend build**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

---

### Task 7: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused coupon-service tests**

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalCouponPurchaseControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 2: Run focused order-service tests**

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" --tests "uz.topdim.order.controller.RedeemCouponControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 3: Run frontend build**

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

- [ ] **Step 4: Manual smoke checklist**

- Payment event retry for the same order returns existing purchased coupons and sends no duplicate notification events.
- Purchased coupon contains `merchantId` and `expiresAt`.
- User profile `ACTIVE` tab no longer shows expired coupons.
- Wrong merchant cannot redeem a coupon.
- Correct merchant can redeem an active, unexpired coupon once.
- Second redemption attempt fails without creating a second redemption row.

---

## Self-Review

- Spec coverage: covers idempotent generation, merchant ownership, expiration, redemption validation, and profile display.
- Placeholder scan: no `TBD`, no broad "handle edge cases" instructions without specific tests.
- Type consistency: `merchantId` and `expiresAt` are carried from coupon snapshot to order item, then to purchased coupon.
- Scope check: this is the post-payment lifecycle only. It does not touch payment provider integration, merchant module expansion, or bazaar/directory.
