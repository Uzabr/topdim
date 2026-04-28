# Purchase Flow Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `cart -> checkout -> purchased coupon` safe by ensuring `order-service` validates every purchased coupon against canonical coupon data from `coupon-service`.

**Architecture:** `coupon-service` exposes a narrow internal purchase snapshot endpoint that does not increment public `viewCount`. `order-service` uses that snapshot during `addToCart` and again during `createOrder`, so client-submitted title, option title, and unit price are never trusted as business truth. Frontend remains a display/input layer and only surfaces server errors clearly.

**Tech Stack:** Java 21, Spring Boot, OpenFeign, JUnit 5, Mockito, MockMvc, React, TypeScript, TanStack Query/Zustand.

---

## Scope

Implement only purchase flow hardening:

- Coupon purchase validation between `order-service` and `coupon-service`.
- Cart and checkout behavior that protects price, coupon status, option status, limits, and `buyUntil`.
- Minimal storefront error handling where needed.

Do not implement:

- New payment provider behavior.
- New merchant module behavior.
- Bazaar/directory migration.
- Redis cache restoration.
- Broad DTO cleanup outside the purchase flow.

## Required Local Skills

- Root: `.agent/skills/test-driven-development/SKILL.md`
- Root: `.agent/skills/systematic-debugging/SKILL.md`
- Root: `.agent/skills/verification-before-completion/SKILL.md`
- Services: `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
- Services: `services/.agent/skills/api-design-principles/SKILL.md`
- Frontend: `frontend/.agent/skills/react/SKILL.md`
- Frontend: `frontend/.agent/skills/frontend-design/SKILL.md`

Follow TDD for backend behavior changes: write the failing test, run it and verify the expected failure, then implement the smallest code that passes.

## Business Rules

- `order-service` must not trust `couponTitle`, `optionTitle`, or `unitPrice` sent by the client.
- A cart item can be added only if the coupon exists, coupon status is `ACTIVE`, `buyUntil` is not in the past, the option exists, option status is `ACTIVE`, and available quantity is enough.
- Checkout must revalidate every cart line because coupon state, price, or limits may change after the item was added.
- Order items store the canonical title and price at the moment of successful checkout.
- If a stale cart item is no longer purchasable, checkout must fail with a business error and must not create an order, clear the cart, or publish `order.created`.
- Public `GET /api/v1/coupons/{id}` must not be reused for order validation because it increments `viewCount`.
- Repeated add-to-cart calls for the same `couponOfferId + couponOptionId` must validate the combined quantity, then increment the existing backend cart item instead of creating duplicate lines.

## Files

- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponPurchaseSnapshotResponse.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/InternalCouponController.java`
- Create: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/InternalCouponPurchaseControllerTest.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/client/CouponClient.java`
- Create: `services/order-service/src/main/java/uz/topdim/order/client/CouponPurchaseSnapshot.java`
- Create: `services/order-service/src/main/java/uz/topdim/order/exception/GlobalExceptionHandler.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- Create: `services/order-service/src/test/java/uz/topdim/order/controller/OrderControllerBusinessErrorTest.java`
- Modify: `frontend/web-app/src/store/cartStore.ts`
- Modify: `frontend/web-app/src/pages/CheckoutPage.tsx`
- Modify: `frontend/web-app/src/components/cart/CartDrawer.tsx`

---

### Task 1: Add Coupon Purchase Snapshot In Coupon Service

**Files:**
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponPurchaseSnapshotResponse.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/InternalCouponController.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/InternalCouponPurchaseControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `InternalCouponPurchaseControllerTest` with these scenarios:

```java
@ExtendWith(MockitoExtension.class)
class InternalCouponPurchaseControllerTest {

    @Mock private CouponOfferService couponOfferService;
    @InjectMocks private InternalCouponController internalCouponController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(internalCouponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET internal purchase snapshot: returns coupon and selected option without using public detail")
    void getPurchaseSnapshot_returnsCanonicalPurchaseData() throws Exception {
        when(couponOfferService.getPurchaseSnapshot(10L, 20L)).thenReturn(
                CouponPurchaseSnapshotResponse.builder()
                        .couponOfferId(10L)
                        .couponOptionId(20L)
                        .couponTitle("Canonical coupon")
                        .optionTitle("Canonical option")
                        .couponStatus("ACTIVE")
                        .optionStatus("ACTIVE")
                        .couponPrice(BigDecimal.valueOf(99000))
                        .quantityLimit(5)
                        .quantitySold(2)
                        .buyUntil(LocalDateTime.of(2027, 6, 1, 12, 0))
                        .useUntil(LocalDateTime.of(2027, 7, 1, 12, 0))
                        .build()
        );

        mockMvc.perform(get("/api/v1/internal/coupons/10/options/20/purchase-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.couponOfferId").value(10))
                .andExpect(jsonPath("$.data.couponOptionId").value(20))
                .andExpect(jsonPath("$.data.couponTitle").value("Canonical coupon"))
                .andExpect(jsonPath("$.data.optionTitle").value("Canonical option"))
                .andExpect(jsonPath("$.data.couponPrice").value(99000));
    }

    @Test
    @DisplayName("GET internal purchase snapshot: missing coupon or option returns 404")
    void getPurchaseSnapshot_missingOption_returns404() throws Exception {
        when(couponOfferService.getPurchaseSnapshot(10L, 99L))
                .thenThrow(new ResourceNotFoundException("Опция купона не найдена"));

        mockMvc.perform(get("/api/v1/internal/coupons/10/options/99/purchase-snapshot"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Опция купона не найдена"));
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalCouponPurchaseControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because `CouponPurchaseSnapshotResponse`, `getPurchaseSnapshot`, and the internal endpoint do not exist yet.

- [ ] **Step 3: Add response DTO**

Create `CouponPurchaseSnapshotResponse`:

```java
package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponPurchaseSnapshotResponse {
    private Long couponOfferId;
    private Long couponOptionId;
    private String couponTitle;
    private String optionTitle;
    private String couponStatus;
    private String optionStatus;
    private BigDecimal couponPrice;
    private Integer quantityLimit;
    private int quantitySold;
    private LocalDateTime buyUntil;
    private LocalDateTime useUntil;
}
```

- [ ] **Step 4: Add service method**

Add to `CouponOfferService` near public read methods:

```java
@Transactional(readOnly = true)
public CouponPurchaseSnapshotResponse getPurchaseSnapshot(Long couponId, Long optionId) {
    CouponOffer offer = couponOfferRepository.findById(couponId)
            .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

    CouponOption option = offer.getOptions().stream()
            .filter(opt -> opt.getId().equals(optionId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Опция купона не найдена"));

    return CouponPurchaseSnapshotResponse.builder()
            .couponOfferId(offer.getId())
            .couponOptionId(option.getId())
            .couponTitle(offer.getTitle())
            .optionTitle(option.getTitle())
            .couponStatus(offer.getStatus().name())
            .optionStatus(option.getStatus().name())
            .couponPrice(option.getCouponPrice())
            .quantityLimit(option.getQuantityLimit())
            .quantitySold(option.getQuantitySold())
            .buyUntil(offer.getBuyUntil())
            .useUntil(offer.getUseUntil())
            .build();
}
```

- [ ] **Step 5: Add internal endpoint controller**

Create `InternalCouponController`:

```java
package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CouponPurchaseSnapshotResponse;
import uz.topdim.coupon.service.CouponOfferService;

@RestController
@RequestMapping("/api/v1/internal/coupons")
@RequiredArgsConstructor
public class InternalCouponController {
    private final CouponOfferService couponOfferService;

    @GetMapping("/{couponId}/options/{optionId}/purchase-snapshot")
    public ResponseEntity<ApiResponse<CouponPurchaseSnapshotResponse>> getPurchaseSnapshot(
            @PathVariable Long couponId,
            @PathVariable Long optionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                couponOfferService.getPurchaseSnapshot(couponId, optionId)
        ));
    }
}
```

- [ ] **Step 6: Run test to verify GREEN**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalCouponPurchaseControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponPurchaseSnapshotResponse.java services/coupon-service/src/main/java/uz/topdim/coupon/controller/InternalCouponController.java services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java services/coupon-service/src/test/java/uz/topdim/coupon/controller/InternalCouponPurchaseControllerTest.java
git commit -m "feat: expose coupon purchase snapshot"
```

---

### Task 2: Make Order Service Validate Cart Items Against Coupon Service

**Files:**
- Create: `services/order-service/src/main/java/uz/topdim/order/client/CouponPurchaseSnapshot.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/client/CouponClient.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Test: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`

- [ ] **Step 1: Write failing `addToCart` tests**

Update `OrderServiceTest` mocks:

```java
@Mock private CouponClient couponClient;
```

Add helper:

```java
private CouponPurchaseSnapshot activeSnapshot() {
    return CouponPurchaseSnapshot.builder()
            .couponOfferId(5L)
            .couponOptionId(3L)
            .couponTitle("Canonical SPA")
            .optionTitle("Canonical Standard")
            .couponStatus("ACTIVE")
            .optionStatus("ACTIVE")
            .couponPrice(BigDecimal.valueOf(99000))
            .quantityLimit(10)
            .quantitySold(2)
            .buyUntil(LocalDateTime.now().plusDays(3))
            .useUntil(LocalDateTime.now().plusDays(30))
            .build();
}

private ApiResponse<CouponPurchaseSnapshot> snapshotResponse(CouponPurchaseSnapshot snapshot) {
    return ApiResponse.success(snapshot);
}
```

Add tests:

```java
@Test
@DisplayName("Добавление в корзину: сервер использует канонические цену и названия из coupon-service")
void addToCart_usesCanonicalCouponSnapshot() {
    Cart cart = Cart.builder().id(1L).userId(10L).items(new ArrayList<>()).build();
    when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(activeSnapshot()));
    when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

    Cart result = orderService.addToCart(
            10L, 5L, 3L,
            "Fake title", "Fake option", BigDecimal.ONE,
            1, false, null, null
    );

    CartItem item = result.getItems().get(0);
    assertThat(item.getCouponTitle()).isEqualTo("Canonical SPA");
    assertThat(item.getOptionTitle()).isEqualTo("Canonical Standard");
    assertThat(item.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(99000));
}

@Test
@DisplayName("Добавление в корзину: купон не ACTIVE → IllegalStateException")
void addToCart_nonActiveCoupon_throwsException() {
    CouponPurchaseSnapshot snapshot = activeSnapshot();
    snapshot.setCouponStatus("SOLD_OUT");
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

    assertThatThrownBy(() -> orderService.addToCart(
            10L, 5L, 3L,
            "SPA", "Standard", BigDecimal.valueOf(99000),
            1, false, null, null
    ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Купон недоступен для покупки");
}

@Test
@DisplayName("Добавление в корзину: срок покупки истёк → IllegalStateException")
void addToCart_expiredBuyUntil_throwsException() {
    CouponPurchaseSnapshot snapshot = activeSnapshot();
    snapshot.setBuyUntil(LocalDateTime.now().minusMinutes(1));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

    assertThatThrownBy(() -> orderService.addToCart(
            10L, 5L, 3L,
            "SPA", "Standard", BigDecimal.valueOf(99000),
            1, false, null, null
    ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Срок покупки купона истёк");
}

@Test
@DisplayName("Добавление в корзину: запрошенное количество превышает остаток → IllegalStateException")
void addToCart_quantityExceedsRemainingLimit_throwsException() {
    CouponPurchaseSnapshot snapshot = activeSnapshot();
    snapshot.setQuantityLimit(3);
    snapshot.setQuantitySold(2);
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

    assertThatThrownBy(() -> orderService.addToCart(
            10L, 5L, 3L,
            "SPA", "Standard", BigDecimal.valueOf(99000),
            2, false, null, null
    ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Недостаточно купонов в наличии");
}

@Test
@DisplayName("Добавление в корзину: повторная та же опция валидирует общий quantity и увеличивает существующую строку")
void addToCart_existingSameOption_incrementsExistingLineAfterCombinedValidation() {
    CartItem existing = CartItem.builder()
            .couponOfferId(5L).couponOptionId(3L)
            .couponTitle("Old title").optionTitle("Old option")
            .unitPrice(BigDecimal.valueOf(50000))
            .quantity(1)
            .gift(false)
            .build();
    Cart cart = Cart.builder().id(1L).userId(10L)
            .items(new ArrayList<>(List.of(existing))).build();

    when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(activeSnapshot()));
    when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

    Cart result = orderService.addToCart(
            10L, 5L, 3L,
            "Fake title", "Fake option", BigDecimal.ONE,
            2, false, null, null
    );

    assertThat(result.getItems()).hasSize(1);
    CartItem item = result.getItems().get(0);
    assertThat(item.getQuantity()).isEqualTo(3);
    assertThat(item.getCouponTitle()).isEqualTo("Canonical SPA");
    assertThat(item.getOptionTitle()).isEqualTo("Canonical Standard");
    assertThat(item.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(99000));
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because `CouponClient` is not injected into `OrderService`, typed snapshot does not exist, and validation is missing.

- [ ] **Step 3: Add typed Feign response**

Create `CouponPurchaseSnapshot`:

```java
package uz.topdim.order.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponPurchaseSnapshot {
    private Long couponOfferId;
    private Long couponOptionId;
    private String couponTitle;
    private String optionTitle;
    private String couponStatus;
    private String optionStatus;
    private BigDecimal couponPrice;
    private Integer quantityLimit;
    private int quantitySold;
    private LocalDateTime buyUntil;
    private LocalDateTime useUntil;
}
```

- [ ] **Step 4: Update `CouponClient`**

Replace both existing `Map<String, Object>` methods with the typed snapshot call:

```java
@GetMapping("/internal/coupons/{couponId}/options/{optionId}/purchase-snapshot")
ApiResponse<CouponPurchaseSnapshot> getPurchaseSnapshot(
        @PathVariable("couponId") Long couponId,
        @PathVariable("optionId") Long optionId
);
```

Current production code does not use `getCouponById` or `getCouponOption`; remove both `Map<String, Object>` methods from `CouponClient`.

- [ ] **Step 5: Inject `CouponClient` and validate in `addToCart`**

Add field to `OrderService`:

```java
private final CouponClient couponClient;
```

Add helpers near the bottom of `OrderService`:

```java
private CouponPurchaseSnapshot loadPurchaseSnapshot(Long couponOfferId, Long couponOptionId) {
    ApiResponse<CouponPurchaseSnapshot> response = couponClient.getPurchaseSnapshot(couponOfferId, couponOptionId);
    if (response == null || response.getData() == null) {
        throw new IllegalStateException("Купон недоступен для покупки");
    }
    return response.getData();
}

private void assertPurchasable(CouponPurchaseSnapshot snapshot, int quantity) {
    if (!"ACTIVE".equals(snapshot.getCouponStatus())) {
        throw new IllegalStateException("Купон недоступен для покупки");
    }
    if (!"ACTIVE".equals(snapshot.getOptionStatus())) {
        throw new IllegalStateException("Опция купона недоступна для покупки");
    }
    if (snapshot.getBuyUntil() != null && snapshot.getBuyUntil().isBefore(LocalDateTime.now())) {
        throw new IllegalStateException("Срок покупки купона истёк");
    }
    if (snapshot.getQuantityLimit() != null && snapshot.getQuantityLimit() > 0) {
        int remaining = snapshot.getQuantityLimit() - snapshot.getQuantitySold();
        if (quantity > remaining) {
            throw new IllegalStateException("Недостаточно купонов в наличии");
        }
    }
}
```

Update `addToCart` so duplicate option lines are merged after validating the combined quantity:

```java
CouponPurchaseSnapshot snapshot = loadPurchaseSnapshot(couponOfferId, couponOptionId);

Cart cart = getCartByUserId(userId);
CartItem existing = cart.getItems().stream()
        .filter(item -> item.getCouponOfferId().equals(couponOfferId)
                && item.getCouponOptionId().equals(couponOptionId))
        .findFirst()
        .orElse(null);

int requestedQuantity = quantity + (existing != null ? existing.getQuantity() : 0);
assertPurchasable(snapshot, requestedQuantity);

if (existing != null) {
    existing.setCouponTitle(snapshot.getCouponTitle());
    existing.setOptionTitle(snapshot.getOptionTitle());
    existing.setUnitPrice(snapshot.getCouponPrice());
    existing.setQuantity(requestedQuantity);
    existing.setGift(isGift);
    existing.setGiftRecipientName(giftName);
    existing.setGiftRecipientPhone(giftPhone);
    return cartRepository.save(cart);
}

CartItem item = CartItem.builder()
        .cart(cart)
        .couponOfferId(snapshot.getCouponOfferId())
        .couponOptionId(snapshot.getCouponOptionId())
        .couponTitle(snapshot.getCouponTitle())
        .optionTitle(snapshot.getOptionTitle())
        .unitPrice(snapshot.getCouponPrice())
        .quantity(quantity)
        .gift(isGift)
        .giftRecipientName(giftName)
        .giftRecipientPhone(giftPhone)
        .build();
```

- [ ] **Step 6: Run tests to verify GREEN**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS for updated `addToCart` tests and existing order tests after adding snapshot stubs where needed.

- [ ] **Step 7: Commit**

```bash
git add services/order-service/src/main/java/uz/topdim/order/client/CouponClient.java services/order-service/src/main/java/uz/topdim/order/client/CouponPurchaseSnapshot.java services/order-service/src/main/java/uz/topdim/order/service/OrderService.java services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java
git commit -m "feat: validate cart items with coupon service"
```

---

### Task 3: Revalidate Cart At Checkout

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Test: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`

- [ ] **Step 1: Write failing checkout tests**

Add tests:

```java
@Test
@DisplayName("Checkout: stale cart item became unavailable → rejects without creating order")
void createOrder_staleUnavailableCartItem_rejectsWithoutSideEffects() {
    CartItem item = CartItem.builder()
            .couponOfferId(5L).couponOptionId(3L)
            .couponTitle("Old title").optionTitle("Old option")
            .unitPrice(BigDecimal.valueOf(99000)).quantity(1)
            .gift(false).build();
    Cart cart = Cart.builder().id(1L).userId(10L)
            .items(new ArrayList<>(List.of(item))).build();

    CouponPurchaseSnapshot snapshot = activeSnapshot();
    snapshot.setCouponStatus("SOLD_OUT");

    when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));

    assertThatThrownBy(() -> orderService.createOrder(10L, "user@test.com", "+998901234567"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Купон недоступен для покупки");

    verify(orderRepository, never()).save(any());
    verify(cartRepository, never()).save(argThat(savedCart -> savedCart.getItems().isEmpty()));
    verify(rabbitTemplate, never()).convertAndSend(eq("order.exchange"), eq("order.created"), any(Object.class));
}

@Test
@DisplayName("Checkout: canonical price changed after add-to-cart → creates order with current server price")
void createOrder_priceChanged_usesCurrentCanonicalPrice() {
    CartItem item = CartItem.builder()
            .couponOfferId(5L).couponOptionId(3L)
            .couponTitle("Old title").optionTitle("Old option")
            .unitPrice(BigDecimal.valueOf(99000)).quantity(2)
            .gift(false).build();
    Cart cart = Cart.builder().id(1L).userId(10L)
            .items(new ArrayList<>(List.of(item))).build();

    CouponPurchaseSnapshot snapshot = activeSnapshot();
    snapshot.setCouponPrice(BigDecimal.valueOf(120000));

    when(cartRepository.findByUserId(10L)).thenReturn(Optional.of(cart));
    when(couponClient.getPurchaseSnapshot(5L, 3L)).thenReturn(snapshotResponse(snapshot));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
        Order o = inv.getArgument(0);
        o.setId(100L);
        return o;
    });
    when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

    Order order = orderService.createOrder(10L, "user@test.com", "+998901234567");

    assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(240000));
    assertThat(order.getItems()).hasSize(1);
    assertThat(order.getItems().get(0).getCouponTitle()).isEqualTo("Canonical SPA");
    assertThat(order.getItems().get(0).getOptionTitle()).isEqualTo("Canonical Standard");
    assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(120000));
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because `createOrder` still trusts stale cart data.

- [ ] **Step 3: Revalidate each cart line before creating order**

Update `createOrder` so it builds order lines from fresh snapshots:

```java
List<OrderItem> orderItems = new ArrayList<>();
BigDecimal totalAmount = BigDecimal.ZERO;

for (CartItem cartItem : cart.getItems()) {
    CouponPurchaseSnapshot snapshot = loadPurchaseSnapshot(
            cartItem.getCouponOfferId(),
            cartItem.getCouponOptionId()
    );
    assertPurchasable(snapshot, cartItem.getQuantity());

    BigDecimal lineTotal = snapshot.getCouponPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
    totalAmount = totalAmount.add(lineTotal);

    OrderItem orderItem = OrderItem.builder()
            .couponOfferId(snapshot.getCouponOfferId())
            .couponOptionId(snapshot.getCouponOptionId())
            .couponTitle(snapshot.getCouponTitle())
            .optionTitle(snapshot.getOptionTitle())
            .unitPrice(snapshot.getCouponPrice())
            .quantity(cartItem.getQuantity())
            .gift(cartItem.isGift())
            .giftRecipientName(cartItem.getGiftRecipientName())
            .giftRecipientPhone(cartItem.getGiftRecipientPhone())
            .build();
    orderItems.add(orderItem);
}

Order order = Order.builder()
        .orderNumber(generateOrderNumber())
        .userId(userId)
        .userEmail(userEmail)
        .userPhone(userPhone)
        .totalAmount(totalAmount)
        .status(OrderStatus.PENDING)
        .build();

for (OrderItem orderItem : orderItems) {
    orderItem.setOrder(order);
}
order.setItems(orderItems);
```

Important: do not clear the cart or publish `order.created` until all lines validate and `orderRepository.save(order)` succeeds.

- [ ] **Step 4: Run tests to verify GREEN**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/java/uz/topdim/order/service/OrderService.java services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java
git commit -m "feat: revalidate cart during checkout"
```

---

### Task 4: Map Order Business Errors To HTTP Statuses

**Files:**
- Create: `services/order-service/src/main/java/uz/topdim/order/exception/GlobalExceptionHandler.java`
- Test: `services/order-service/src/test/java/uz/topdim/order/controller/OrderControllerBusinessErrorTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `OrderControllerBusinessErrorTest`:

```java
@ExtendWith(MockitoExtension.class)
class OrderControllerBusinessErrorTest {

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
    @DisplayName("POST /api/v1/cart/items: unavailable coupon returns 409 with business message")
    void addToCart_unavailableCoupon_returns409() throws Exception {
        when(orderService.addToCart(
                eq(10L), eq(5L), eq(3L), anyString(), anyString(), any(BigDecimal.class),
                eq(1), eq(false), isNull(), isNull()
        )).thenThrow(new IllegalStateException("Купон недоступен для покупки"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "couponOfferId": 5,
                                  "couponOptionId": 3,
                                  "couponTitle": "SPA",
                                  "optionTitle": "Standard",
                                  "unitPrice": 99000,
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Купон недоступен для покупки"));
    }

    @Test
    @DisplayName("POST /api/v1/orders: stale cart returns 409 and keeps message")
    void createOrder_staleCart_returns409() throws Exception {
        when(orderService.createOrder(eq(10L), eq("user@test.com"), eq("+998901234567")))
                .thenThrow(new IllegalStateException("Срок покупки купона истёк"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@test.com",
                                  "phone": "+998901234567"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Срок покупки купона истёк"));
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.controller.OrderControllerBusinessErrorTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because `order-service` has no controller advice mapping `IllegalStateException` to `409`.

- [ ] **Step 3: Add `GlobalExceptionHandler`**

Create:

```java
package uz.topdim.order.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.topdim.common.dto.ApiResponse;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Ошибка валидации",
                        (a, b) -> a
                ));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Ошибка валидации"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled order-service exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Внутренняя ошибка сервера"));
    }
}
```

- [ ] **Step 4: Run controller test to verify GREEN**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.controller.OrderControllerBusinessErrorTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/java/uz/topdim/order/exception/GlobalExceptionHandler.java services/order-service/src/test/java/uz/topdim/order/controller/OrderControllerBusinessErrorTest.java
git commit -m "feat: map order business errors"
```

---

### Task 5: Surface Purchase Errors In Storefront

**Files:**
- Modify: `frontend/web-app/src/store/cartStore.ts`
- Modify: `frontend/web-app/src/pages/CheckoutPage.tsx`
- Modify: `frontend/web-app/src/components/cart/CartDrawer.tsx`

- [ ] **Step 1: Add cart action error state**

In `cartStore.ts`, extend `CartState`:

```ts
error: string | null;
clearError: () => void;
```

Initialize:

```ts
error: null,
clearError: () => set({ error: null }),
```

Update `addToBackendCart`:

```ts
addToBackendCart: async (request) => {
  set({ isLoading: true, error: null });
  try {
    const response = await ordersApi.addToCart(request);
    const cart = response.data.data;
    const backendItems = cart.items || [];
    set({
      backendItems,
      backendCartId: cart.id,
      items: backendToLocal(backendItems),
      isOpen: true,
      isLoading: false,
      error: null,
      ...calcBackendTotals(backendItems),
    });
  } catch (err: any) {
    const message = err?.response?.data?.message || 'Не удалось добавить купон в корзину';
    set({ isLoading: false, error: message });
    throw err;
  }
},
```

- [ ] **Step 2: Show checkout business error without clearing local state**

In `CheckoutPage.tsx`, make sure `ordersApi.createOrder` errors are displayed from `err.response.data.message`:

```ts
try {
  setError('');
  const response = await ordersApi.createOrder(userEmail || '', userPhone || '');
  setOrder(response.data.data);
  clearCart();
} catch (err: any) {
  setError(err?.response?.data?.message || 'Не удалось оформить заказ');
}
```

Do not call `clearCart()` when checkout fails.

- [ ] **Step 3: Add cart drawer error display**

Update `CartDrawer` to read `error` from the cart store:

```ts
const { items, isOpen, closeCart, removeFromCart, updateQuantity, totalItems, totalPrice, error } = useCartStore();
```

Add a compact error row near the checkout button:

```tsx
{error && (
  <div className="cart-drawer__error" role="alert">
    {error}
  </div>
)}
```

Keep styling consistent with existing error UI. Do not redesign the cart.

- [ ] **Step 4: Run frontend checks**

Run in `frontend/web-app`:

```bash
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/web-app/src/store/cartStore.ts frontend/web-app/src/pages/CheckoutPage.tsx frontend/web-app/src/components/cart/CartDrawer.tsx
git commit -m "feat: show purchase flow business errors"
```

---

### Task 6: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused coupon-service tests**

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalCouponPurchaseControllerTest" --tests "uz.topdim.coupon.controller.CouponPublicVisibilityTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 2: Run focused order-service tests**

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" --tests "uz.topdim.order.controller.OrderControllerBusinessErrorTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 3: Run frontend build**

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

- [ ] **Step 4: Inspect diff**

```bash
git status --short
git diff --stat
```

Expected: only purchase-flow files from this plan are modified.

- [ ] **Step 5: Manual smoke checklist**

Start the services through the repo's normal local run commands, then verify:

- Add a valid `ACTIVE` coupon option to cart.
- Try adding with a fake client price and confirm cart/order uses canonical server price.
- Try checkout after making the coupon unavailable and confirm checkout fails with a visible message.
- Confirm failed checkout does not clear the cart.
- Confirm public coupon detail still increments view count only through public detail, not through purchase snapshot.

---

## Self-Review

- Spec coverage: covers canonical server-side purchase validation, stale cart revalidation, business error mapping, and storefront error presentation.
- Placeholder scan: no `TBD`, no broad "handle edge cases" instructions without concrete tests.
- Type consistency: `CouponPurchaseSnapshotResponse` in `coupon-service` maps to `CouponPurchaseSnapshot` in `order-service`; Feign endpoint path is `/api/v1/internal/coupons/{couponId}/options/{optionId}/purchase-snapshot`.
- Scope check: this plan touches `coupon-service`, `order-service`, and minimal web-app UI because the purchase flow crosses those boundaries. It does not enter merchant, bazaar, payment provider, or broad storefront demo cleanup.
