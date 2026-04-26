# Partner Cashier Redeem UX Implementation Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** Build the minimal merchant/cashier flow that lets a partner redeem a purchased coupon by PIN code and see redemption history safely.

**Architecture:** Keep coupon ownership in `coupon-service` as the source of truth for `Merchant.userId -> Merchant.id`, and let `order-service` resolve the current merchant from the trusted `X-User-Id` gateway header before redeeming. Add a partner-facing page in `frontend/admin-app` for PIN entry, success/error feedback, and redemption history. Do not build a full merchant cabinet, QR scanner, refund automation, or bazaar work in this stage.

**Tech Stack:** Java 21, Spring Boot 3, OpenFeign, Spring Data JPA, JUnit 5, Mockito, MockMvc, React 19, TypeScript, React Query, React Hook Form, Zod, Ant Design 6, Vite.

---

## Required Skills

Before implementation, the AI must read and follow these skills:

- Root process: `.agent/skills/using-superpowers/SKILL.md`
- Root execution: `.agent/skills/executing-plans/SKILL.md`
- Root TDD: `.agent/skills/test-driven-development/SKILL.md`
- Root verification: `.agent/skills/verification-before-completion/SKILL.md`
- Backend API design: `services/.agent/skills/api-design-principles/SKILL.md`
- Backend tests: `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
- Frontend React: `frontend/.agent/skills/react/SKILL.md`
- Frontend forms: `frontend/.agent/skills/react-hook-form-zod/SKILL.md`
- Frontend design: `frontend/.agent/skills/frontend-design/SKILL.md`

## Scope

Implement only:

- Secure backend merchant resolution for partner redeem/stat/history flows.
- A safe partner redemption endpoint that returns DTOs, not raw JPA entities.
- Partner/cashier UI in `frontend/admin-app`.
- Redemption history UI.
- Minimal partner stats if it can be derived from `merchantId` without trusting client-provided coupon ids.
- Docs and focused tests.

Do not implement:

- QR scanner camera UI.
- Full merchant profile/cabinet.
- Staff invitation/login redesign.
- Refund automation.
- Complaint workflow.
- Bazaar/shop migration.
- Real payment provider changes.

## Current State And Important Risk

Current backend already has:

- `POST /api/v1/orders/redeem`
- `GET /api/v1/partner/redemptions`
- `GET /api/v1/partner/stats`
- `OrderService.redeemCoupon(couponCode, merchantId, staffName)`
- `Merchant.userId` in `coupon-service`

But there is a safety gap:

- `order-service` currently expects `X-Merchant-Id`.
- `api-gateway` currently forwards `X-User-Id`, `X-User-Email`, `X-User-Role`, but does not derive `X-Merchant-Id`.
- Browser/frontend must not be the source of truth for `merchantId`.

Therefore this plan must resolve merchant ownership server-side from `X-User-Id`.

## Business Rules

- A partner/cashier can redeem only coupons belonging to their merchant.
- The UI sends only `couponCode` and optional `staffName`; it must not send `merchantId`.
- Backend resolves `merchantId` from trusted `X-User-Id`.
- `couponCode` input should be trimmed and normalized to uppercase before request.
- Successful redeem changes purchased coupon status to `USED`, sets `usedAt`, and creates one `Redemption`.
- Already used, expired, cancelled, missing, or wrong-merchant coupons must show clear errors.
- Redemption history shows newest first and only for the current merchant.
- No fake/demo redemptions in production UI.

---

### Task 1: Add Coupon-Service Internal Merchant Context

**Files:**
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantContextResponse.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java`
- Create: `services/coupon-service/src/main/java/uz/topdim/coupon/controller/InternalMerchantController.java`
- Create or modify test: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/InternalMerchantControllerTest.java`
- Modify if needed: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java`

**Step 1: Write failing controller test**

Create `InternalMerchantControllerTest` with MockMvc standalone setup.

Test:

```java
@Test
@DisplayName("GET /api/v1/internal/merchants/by-user/{userId}: returns merchant context")
void getMerchantContextByUser_returnsMerchantContext() throws Exception {
    MerchantContextResponse response = MerchantContextResponse.builder()
            .merchantId(77L)
            .userId(10L)
            .name("SPA Oasis")
            .active(true)
            .build();

    when(merchantService.getMerchantContextByUserId(10L)).thenReturn(response);

    mockMvc.perform(get("/api/v1/internal/merchants/by-user/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.merchantId").value(77))
            .andExpect(jsonPath("$.data.userId").value(10))
            .andExpect(jsonPath("$.data.name").value("SPA Oasis"))
            .andExpect(jsonPath("$.data.active").value(true));
}
```

**Step 2: Run test to verify RED**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalMerchantControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because controller/DTO do not exist.

**Step 3: Add DTO**

Create `MerchantContextResponse`:

```java
package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantContextResponse {
    private Long merchantId;
    private Long userId;
    private String name;
    private boolean active;
}
```

**Step 4: Add service method**

In `MerchantService`, add:

```java
@Transactional(readOnly = true)
public MerchantContextResponse getMerchantContextByUserId(Long userId) {
    Merchant merchant = merchantRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Мерчант для пользователя не найден"));

    return MerchantContextResponse.builder()
            .merchantId(merchant.getId())
            .userId(merchant.getUserId())
            .name(merchant.getName())
            .active(merchant.isActive())
            .build();
}
```

**Step 5: Add controller**

Create `InternalMerchantController`:

```java
package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.MerchantContextResponse;
import uz.topdim.coupon.service.MerchantService;

@RestController
@RequestMapping("/api/v1/internal/merchants")
@RequiredArgsConstructor
public class InternalMerchantController {
    private final MerchantService merchantService;

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<ApiResponse<MerchantContextResponse>> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantService.getMerchantContextByUserId(userId)
        ));
    }
}
```

**Step 6: Run coupon-service focused tests**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalMerchantControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

**Step 7: Commit**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantContextResponse.java \
        services/coupon-service/src/main/java/uz/topdim/coupon/service/MerchantService.java \
        services/coupon-service/src/main/java/uz/topdim/coupon/controller/InternalMerchantController.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/controller/InternalMerchantControllerTest.java
git commit -m "feat: expose internal merchant context by user"
```

---

### Task 2: Resolve Partner Merchant In Order-Service

**Files:**
- Create: `services/order-service/src/main/java/uz/topdim/order/client/MerchantContext.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/client/CouponClient.java`
- Create: `services/order-service/src/main/java/uz/topdim/order/service/PartnerMerchantResolver.java`
- Create: `services/order-service/src/test/java/uz/topdim/order/service/PartnerMerchantResolverTest.java`

**Step 1: Write failing resolver tests**

Create tests:

```java
@Test
@DisplayName("resolveMerchantId: active merchant returns merchantId")
void resolveMerchantId_activeMerchant_returnsId() {
    MerchantContext context = MerchantContext.builder()
            .merchantId(77L)
            .userId(10L)
            .name("SPA Oasis")
            .active(true)
            .build();
    when(couponClient.getMerchantContextByUserId(10L)).thenReturn(ApiResponse.success(context));

    Long merchantId = resolver.resolveMerchantId(10L);

    assertThat(merchantId).isEqualTo(77L);
}

@Test
@DisplayName("resolveMerchantId: inactive merchant is rejected")
void resolveMerchantId_inactiveMerchant_throws() {
    MerchantContext context = MerchantContext.builder()
            .merchantId(77L)
            .userId(10L)
            .name("SPA Oasis")
            .active(false)
            .build();
    when(couponClient.getMerchantContextByUserId(10L)).thenReturn(ApiResponse.success(context));

    assertThatThrownBy(() -> resolver.resolveMerchantId(10L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Мерчант не активен");
}
```

**Step 2: Run test to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.PartnerMerchantResolverTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because resolver/client DTO do not exist.

**Step 3: Add client DTO**

Create `MerchantContext`:

```java
package uz.topdim.order.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantContext {
    private Long merchantId;
    private Long userId;
    private String name;
    private boolean active;
}
```

**Step 4: Extend Feign client**

In `CouponClient`, add:

```java
@GetMapping("/internal/merchants/by-user/{userId}")
ApiResponse<MerchantContext> getMerchantContextByUserId(@PathVariable("userId") Long userId);
```

**Step 5: Add resolver service**

Create `PartnerMerchantResolver`:

```java
package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.client.CouponClient;
import uz.topdim.order.client.MerchantContext;

@Service
@RequiredArgsConstructor
public class PartnerMerchantResolver {
    private final CouponClient couponClient;

    public Long resolveMerchantId(Long userId) {
        ApiResponse<MerchantContext> response = couponClient.getMerchantContextByUserId(userId);
        MerchantContext context = response != null ? response.getData() : null;

        if (context == null || context.getMerchantId() == null) {
            throw new IllegalStateException("Мерчант для пользователя не найден");
        }
        if (!context.isActive()) {
            throw new IllegalStateException("Мерчант не активен");
        }

        return context.getMerchantId();
    }
}
```

**Step 6: Run resolver tests**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.PartnerMerchantResolverTest" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

**Step 7: Commit**

```bash
git add services/order-service/src/main/java/uz/topdim/order/client/MerchantContext.java \
        services/order-service/src/main/java/uz/topdim/order/client/CouponClient.java \
        services/order-service/src/main/java/uz/topdim/order/service/PartnerMerchantResolver.java \
        services/order-service/src/test/java/uz/topdim/order/service/PartnerMerchantResolverTest.java
git commit -m "feat: resolve partner merchant context in order service"
```

---

### Task 3: Add Safe Partner Redemption Endpoint And DTO

**Files:**
- Create: `services/order-service/src/main/java/uz/topdim/order/dto/CreateRedemptionRequest.java`
- Create: `services/order-service/src/main/java/uz/topdim/order/dto/RedeemCouponResponse.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/controller/RedeemCouponControllerTest.java`
- Create or modify: `services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`

**Step 1: Write failing PartnerController test**

Add a test for the preferred endpoint:

```java
@Test
@DisplayName("POST /api/v1/partner/redemptions: resolves merchant from X-User-Id and returns DTO")
void createRedemption_resolvesMerchantFromUserHeader() throws Exception {
    PurchasedCoupon coupon = PurchasedCoupon.builder()
            .id(501L)
            .couponTitle("SPA")
            .optionTitle("Standard")
            .couponCode("CP-TEST1234")
            .merchantId(77L)
            .status(PurchasedCouponStatus.USED)
            .usedAt(LocalDateTime.of(2026, 4, 26, 12, 0))
            .build();

    when(partnerMerchantResolver.resolveMerchantId(10L)).thenReturn(77L);
    when(orderService.redeemCoupon("CP-TEST1234", 77L, "Анна")).thenReturn(coupon);

    mockMvc.perform(post("/api/v1/partner/redemptions")
                    .header("X-User-Id", "10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "couponCode": " cp-test1234 ",
                              "staffName": "Анна"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.couponCode").value("CP-TEST1234"))
            .andExpect(jsonPath("$.data.status").value("USED"))
            .andExpect(jsonPath("$.data.merchantId").value(77));

    verify(partnerMerchantResolver).resolveMerchantId(10L);
    verify(orderService).redeemCoupon("CP-TEST1234", 77L, "Анна");
}
```

**Step 2: Run test to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.controller.PartnerControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because endpoint/request/DTO are missing.

**Step 3: Add request DTO**

Create `CreateRedemptionRequest`:

```java
package uz.topdim.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRedemptionRequest {
    @NotBlank(message = "Код купона обязателен")
    private String couponCode;

    private String staffName;
}
```

**Step 4: Add response DTO**

Create `RedeemCouponResponse`:

```java
package uz.topdim.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedeemCouponResponse {
    private Long purchasedCouponId;
    private Long couponOfferId;
    private Long couponOptionId;
    private String couponTitle;
    private String optionTitle;
    private String couponCode;
    private String status;
    private Long merchantId;
    private String merchantName;
    private LocalDateTime purchasedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
```

**Step 5: Add mapper**

In `OrderService`, add:

```java
public RedeemCouponResponse mapToRedeemResponse(PurchasedCoupon coupon) {
    return RedeemCouponResponse.builder()
            .purchasedCouponId(coupon.getId())
            .couponOfferId(coupon.getCouponOfferId())
            .couponOptionId(coupon.getCouponOptionId())
            .couponTitle(coupon.getCouponTitle())
            .optionTitle(coupon.getOptionTitle())
            .couponCode(coupon.getCouponCode())
            .status(coupon.getStatus().name())
            .merchantId(coupon.getMerchantId())
            .merchantName(coupon.getMerchantName())
            .purchasedAt(coupon.getPurchasedAt())
            .expiresAt(coupon.getExpiresAt())
            .usedAt(coupon.getUsedAt())
            .build();
}
```

**Step 6: Add preferred endpoint**

In `PartnerController`, inject `OrderService` and `PartnerMerchantResolver`, then add:

```java
@PostMapping("/redemptions")
public ResponseEntity<ApiResponse<RedeemCouponResponse>> createRedemption(
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody CreateRedemptionRequest request
) {
    Long merchantId = partnerMerchantResolver.resolveMerchantId(userId);
    String couponCode = request.getCouponCode().trim().toUpperCase();
    PurchasedCoupon coupon = orderService.redeemCoupon(couponCode, merchantId, request.getStaffName());
    return ResponseEntity.ok(ApiResponse.success("Купон использован", orderService.mapToRedeemResponse(coupon)));
}
```

**Step 7: Harden legacy redeem response**

In `OrderController`, change `POST /api/v1/orders/redeem` response from raw `PurchasedCoupon` to `RedeemCouponResponse`.

If legacy endpoint remains, keep its existing `X-Merchant-Id` behavior only for backward compatibility, but the new UI must not use it.

**Step 8: Run focused tests**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.controller.PartnerControllerTest" --tests "uz.topdim.order.controller.RedeemCouponControllerTest" --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

**Step 9: Commit**

```bash
git add services/order-service/src/main/java/uz/topdim/order/dto/CreateRedemptionRequest.java \
        services/order-service/src/main/java/uz/topdim/order/dto/RedeemCouponResponse.java \
        services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java \
        services/order-service/src/main/java/uz/topdim/order/controller/OrderController.java \
        services/order-service/src/main/java/uz/topdim/order/service/OrderService.java \
        services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java \
        services/order-service/src/test/java/uz/topdim/order/controller/RedeemCouponControllerTest.java \
        services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java
git commit -m "feat: add safe partner coupon redemption endpoint"
```

---

### Task 4: Secure Partner Stats And History Contracts

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/PartnerService.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/repository/PurchasedCouponRepository.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/PartnerServiceTest.java`
- Modify: `services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java`

**Step 1: Write failing stats test**

Update `PartnerServiceTest` so `getStats` takes only `merchantId`:

```java
@Test
@DisplayName("getStats: calculates stats by merchantId without client-provided coupon ids")
void getStats_byMerchantId_returnsCalculatedStats() {
    when(purchasedCouponRepository.countByMerchantId(77L)).thenReturn(100L);
    when(purchasedCouponRepository.countByMerchantIdAndStatus(77L, PurchasedCouponStatus.USED)).thenReturn(40L);
    when(purchasedCouponRepository.countDistinctCouponOfferIdsByMerchantId(77L)).thenReturn(2L);
    when(purchasedCouponRepository.sumRevenueByMerchantId(77L)).thenReturn(BigDecimal.valueOf(500000));

    PartnerStatsResponse stats = partnerService.getStats(77L);

    assertThat(stats.getTotalCoupons()).isEqualTo(2);
    assertThat(stats.getTotalSold()).isEqualTo(100);
    assertThat(stats.getTotalRedeemed()).isEqualTo(40);
    assertThat(stats.getTotalRevenue()).isEqualTo(BigDecimal.valueOf(500000));
}
```

**Step 2: Run test to verify RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.PartnerServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because repository/service signatures do not exist.

**Step 3: Add repository methods**

In `PurchasedCouponRepository`, add:

```java
long countByMerchantId(Long merchantId);

long countByMerchantIdAndStatus(Long merchantId, PurchasedCouponStatus status);

@Query("select count(distinct pc.couponOfferId) from PurchasedCoupon pc where pc.merchantId = :merchantId")
long countDistinctCouponOfferIdsByMerchantId(@Param("merchantId") Long merchantId);

@Query("select coalesce(sum(oi.unitPrice * oi.quantity), 0) from OrderItem oi where oi.merchantId = :merchantId")
BigDecimal sumRevenueByMerchantId(@Param("merchantId") Long merchantId);
```

**Step 4: Update PartnerService**

Replace `getStats(Long merchantId, Collection<Long> couponOfferIds)` with:

```java
@Transactional(readOnly = true)
public PartnerStatsResponse getStats(Long merchantId) {
    long totalCoupons = purchasedCouponRepository.countDistinctCouponOfferIdsByMerchantId(merchantId);
    long totalSold = purchasedCouponRepository.countByMerchantId(merchantId);
    long totalRedeemed = purchasedCouponRepository.countByMerchantIdAndStatus(
            merchantId, PurchasedCouponStatus.USED);
    BigDecimal revenue = purchasedCouponRepository.sumRevenueByMerchantId(merchantId);

    return PartnerStatsResponse.builder()
            .totalCoupons((int) totalCoupons)
            .totalSold(totalSold)
            .totalRedeemed(totalRedeemed)
            .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
            .build();
}
```

**Step 5: Update PartnerController**

Change `GET /api/v1/partner/stats` to:

```java
@GetMapping("/stats")
public ResponseEntity<ApiResponse<PartnerStatsResponse>> getStats(@RequestHeader("X-User-Id") Long userId) {
    Long merchantId = partnerMerchantResolver.resolveMerchantId(userId);
    return ResponseEntity.ok(ApiResponse.success(partnerService.getStats(merchantId)));
}
```

Change `GET /api/v1/partner/redemptions` to use `X-User-Id`, resolve merchantId, and no longer require `X-Merchant-Id`.

**Step 6: Run focused tests**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.PartnerServiceTest" --tests "uz.topdim.order.controller.PartnerControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

**Step 7: Commit**

```bash
git add services/order-service/src/main/java/uz/topdim/order/controller/PartnerController.java \
        services/order-service/src/main/java/uz/topdim/order/service/PartnerService.java \
        services/order-service/src/main/java/uz/topdim/order/repository/PurchasedCouponRepository.java \
        services/order-service/src/test/java/uz/topdim/order/service/PartnerServiceTest.java \
        services/order-service/src/test/java/uz/topdim/order/controller/PartnerControllerTest.java
git commit -m "fix: derive partner stats and history from trusted user context"
```

---

### Task 5: Add Admin-App Partner API Client

**Files:**
- Create: `frontend/admin-app/src/features/partner-redemptions/api.ts`
- Modify if needed: `frontend/admin-app/src/types/index.ts`

**Step 1: Create typed API module**

Create `api.ts`:

```typescript
import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';

export interface CreateRedemptionRequest {
  couponCode: string;
  staffName?: string;
}

export interface RedeemCouponResponse {
  purchasedCouponId: number;
  couponOfferId: number;
  couponOptionId: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  status: 'ACTIVE' | 'USED' | 'EXPIRED' | 'CANCELLED';
  merchantId: number;
  merchantName?: string;
  purchasedAt?: string;
  expiresAt?: string;
  usedAt?: string;
}

export interface RedemptionResponse {
  id: number;
  couponTitle?: string;
  optionTitle?: string;
  couponCode?: string;
  redeemedByStaff?: string;
  note?: string;
  redeemedAt?: string;
}

export interface PartnerStatsResponse {
  totalCoupons: number;
  totalSold: number;
  totalRedeemed: number;
  totalRevenue: number;
}

export const partnerRedemptionsApi = {
  redeem: (request: CreateRedemptionRequest) =>
    api.post<ApiResponse<RedeemCouponResponse>>('/api/v1/partner/redemptions', request),

  getRedemptions: (page = 0, size = 10) =>
    api.get<ApiResponse<PageResponse<RedemptionResponse>>>('/api/v1/partner/redemptions', {
      params: { page, size },
    }),

  getStats: () =>
    api.get<ApiResponse<PartnerStatsResponse>>('/api/v1/partner/stats'),
};
```

**Step 2: Run TypeScript build**

Run:

```bash
cd frontend/admin-app
npm run build
```

Expected: PASS after the API module is created.

**Step 3: Commit**

```bash
git add frontend/admin-app/src/features/partner-redemptions/api.ts frontend/admin-app/src/types/index.ts
git commit -m "feat: add partner redemption admin api client"
```

---

### Task 6: Build Partner Cashier Page

**Files:**
- Create: `frontend/admin-app/src/features/partner-redemptions/PartnerRedeemPage.tsx`
- Create: `frontend/admin-app/src/features/partner-redemptions/PartnerRedeemPage.css`
- Modify: `frontend/admin-app/src/App.tsx`
- Modify: `frontend/admin-app/src/components/layout/AdminLayout.tsx`

**Step 1: Create page with React Hook Form + Zod**

Use `frontend/.agent/skills/react-hook-form-zod/SKILL.md`.

Core form schema:

```typescript
const redeemSchema = z.object({
  couponCode: z.string().trim().min(4, 'Введите PIN-код купона'),
  staffName: z.string().trim().max(80, 'Слишком длинное имя').optional(),
});

type RedeemFormValues = z.infer<typeof redeemSchema>;
```

Submit behavior:

```typescript
const onSubmit = handleSubmit((values) => {
  const couponCode = values.couponCode.trim().toUpperCase();
  redeemMutation.mutate({
    couponCode,
    staffName: values.staffName?.trim() || undefined,
  });
});
```

Mutation behavior:

- On success: show `message.success('Купон использован')`.
- Reset only `couponCode`, preserve `staffName`.
- Invalidate `partner-redemptions` and `partner-stats`.
- Render success panel with title, option, code, status, usedAt.
- On error: read `error.response?.data?.message` and show it clearly.

**Step 2: Add redemption history**

Use React Query:

```typescript
const redemptionsQuery = useQuery({
  queryKey: ['partner-redemptions'],
  queryFn: () => partnerRedemptionsApi.getRedemptions(0, 10).then((res) => res.data.data),
});
```

Render newest redemptions with Ant Design `Table` or `List`.

Do not show demo rows if query fails or returns empty.

**Step 3: Add stats cards**

Use `partnerRedemptionsApi.getStats()` and render:

- sold coupons;
- redeemed coupons;
- total coupon offers;
- revenue.

If stats request fails, show a small alert but keep redeem form usable.

**Step 4: Add route**

In `frontend/admin-app/src/App.tsx`, import page and add a protected partner route:

```tsx
<Route element={<ProtectedRoute allowedRoles={['PARTNER', 'ADMIN', 'SUPER_ADMIN']} />}>
  <Route element={<AdminLayout />}>
    <Route path="/partner/redeem" element={<PartnerRedeemPage />} />
  </Route>
</Route>
```

Keep existing moderation/admin routes unchanged.

**Step 5: Add menu item**

In `AdminLayout.tsx`, add a partner section:

```tsx
{
  key: '/partner',
  icon: <SafetyCertificateOutlined />,
  label: 'Партнёр',
  roles: ['PARTNER', 'ADMIN', 'SUPER_ADMIN'],
  children: [
    { key: '/partner/redeem', icon: <TagOutlined />, label: 'Погашение купонов', roles: ['PARTNER', 'ADMIN', 'SUPER_ADMIN'] },
  ],
}
```

**Step 6: Handle partner root redirect**

If the current root redirect sends everyone to `/dashboard`, add a small role-aware redirect component so `PARTNER` lands on `/partner/redeem`.

Example:

```tsx
function HomeRedirect() {
  const role = useAuthStore((state) => state.user?.role);
  return <Navigate to={role === 'PARTNER' ? '/partner/redeem' : '/dashboard'} replace />;
}
```

Use it for `/` and optionally `*` fallback.

**Step 7: Run frontend checks**

Run:

```bash
cd frontend/admin-app
npm run build
npm run lint
```

Expected: PASS.

**Step 8: Commit**

```bash
git add frontend/admin-app/src/features/partner-redemptions/PartnerRedeemPage.tsx \
        frontend/admin-app/src/features/partner-redemptions/PartnerRedeemPage.css \
        frontend/admin-app/src/App.tsx \
        frontend/admin-app/src/components/layout/AdminLayout.tsx
git commit -m "feat: add partner cashier redemption page"
```

---

### Task 7: Gateway And Contract Documentation

**Files:**
- Modify: `infrastructure/api-gateway/src/main/java/uz/topdim/gateway/filter/JwtAuthenticationFilter.java`
- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`
- Modify if present: `services/order-service/README.md`

**Step 1: Clean spoofable merchant header**

In gateway request cleanup, remove external `X-Merchant-Id` too:

```java
headers.remove("X-Merchant-Id");
```

This is safe because the new partner flow uses `X-User-Id`, not `X-Merchant-Id`.

**Step 2: Add gateway test**

In `JwtAuthenticationFilterTest`, add or update a test proving external `X-Merchant-Id` is stripped.

Expected downstream headers:

- `X-User-Id` exists.
- `X-User-Role` exists.
- `X-Merchant-Id` does not preserve client-provided value.

**Step 3: Update API docs**

Document:

- `POST /api/v1/partner/redemptions`
- `GET /api/v1/partner/redemptions`
- `GET /api/v1/partner/stats`
- Request body contains no `merchantId`.
- Merchant is resolved from authenticated partner user.
- Legacy `POST /api/v1/orders/redeem` is not used by frontend.

**Step 4: Update PRD**

Add MVP rule:

```markdown
Partner cashier MVP:
- cashier enters purchased coupon PIN;
- backend resolves merchant from authenticated user;
- successful redeem changes coupon to USED;
- history shows merchant-only redemptions;
- QR scanner is out of MVP scope.
```

**Step 5: Run docs grep**

Run:

```bash
rg -n "partner/redemptions|X-Merchant-Id|Погашение купонов|cashier|кассир" docs services/order-service/README.md infrastructure/api-gateway/src/main/java
```

Expected: docs mention the new endpoint and no doc tells frontend to send `merchantId`.

**Step 6: Commit**

```bash
git add infrastructure/api-gateway/src/main/java/uz/topdim/gateway/filter/JwtAuthenticationFilter.java \
        infrastructure/api-gateway/src/test/java/uz/topdim/gateway/filter/JwtAuthenticationFilterTest.java \
        docs/API_CONTRACT.md docs/PRODUCT_REQUIREMENTS_DOCUMENT.md services/order-service/README.md
git commit -m "docs: document partner redemption contract"
```

---

### Task 8: Final Verification

**Files:**
- No new files.

**Step 1: Run coupon-service tests**

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalMerchantControllerTest" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

**Step 2: Run order-service focused tests**

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.PartnerMerchantResolverTest" --tests "uz.topdim.order.controller.PartnerControllerTest" --tests "uz.topdim.order.controller.RedeemCouponControllerTest" --tests "uz.topdim.order.service.PartnerServiceTest" --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

**Step 3: Run gateway focused tests**

```bash
./gradlew :infrastructure:api-gateway:test --tests "uz.topdim.gateway.filter.JwtAuthenticationFilterTest" -x jacocoTestReport -x jacocoTestCoverageVerification --rerun-tasks
```

Expected: PASS.

**Step 4: Run admin frontend checks**

```bash
cd frontend/admin-app
npm run build
npm run lint
```

Expected: PASS.

**Step 5: Run contract grep checks**

```bash
rg -n "merchantId" frontend/admin-app/src/features/partner-redemptions
```

Expected: no request payload sends `merchantId`. Types may include `merchantId` only in response DTOs.

```bash
rg -n "X-Merchant-Id|partner/redemptions|partner/stats" services/order-service/src/main/java infrastructure/api-gateway/src/main/java docs/API_CONTRACT.md
```

Expected: new partner endpoints use `X-User-Id`; gateway strips external `X-Merchant-Id`; legacy references are documented as legacy/backward compatible only.

**Step 6: Manual smoke checklist**

Run app locally and verify:

- Partner user opens `/partner/redeem`.
- Empty PIN shows validation.
- Valid active coupon PIN returns success and shows USED result.
- Same PIN second time shows already-used error.
- Wrong merchant coupon shows wrong-merchant error.
- Expired coupon shows expired error.
- History refreshes after success.
- PARTNER cannot open moderation/admin routes.
- ADMIN/SUPER_ADMIN can open partner redeem page for support testing.

---

## Self-Review

- Security: UI does not send merchantId; backend derives merchant from trusted user context.
- Scope: no QR scanner, no merchant cabinet, no refunds, no bazaar.
- Contract: new partner endpoint returns DTO, not raw JPA entity.
- UX: cashier can redeem by PIN and see history/errors.
- Data integrity: existing `OrderService.redeemCoupon` remains the single status transition point.
- Skills: implementation touches root, services, frontend, and infrastructure skills as required.

Plan complete and saved to `docs/superpowers/plans/2026-04-26-partner-cashier-redeem-ux.md`.

Next step: run `.agent/workflows/execute-plan.md` to execute this plan task-by-task in single-flow mode.
