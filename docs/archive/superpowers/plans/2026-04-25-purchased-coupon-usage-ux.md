# Purchased Coupon Usage UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make purchased coupons in the user profile actionable: users can see where to use the coupon, what to show to the merchant, the validity window, and what to do if the merchant refuses.

**Architecture:** Snapshot merchant usage details at checkout time through the existing coupon-service internal purchase snapshot, persist those details in order-service order items and purchased coupons, expose them through `PurchasedCouponResponse`, then render a focused usage card in `frontend/web-app` profile. Do not build a notification center, complaints workflow, refund automation, or merchant cabinet in this stage.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, Flyway, JUnit 5, Mockito, React 19, TypeScript, TanStack Query, Vite.

---

## Coordination Note

Start this plan only after the current coupon archive/immutability work is merged or the working tree is clean. This plan touches some `coupon-service` files for the internal purchase snapshot and may conflict if implemented at the same time as archive work.

## Required Local Skills

The implementing AI must read and follow the folder skills that match the touched area:

- Root process:
  - `.agent/skills/systematic-debugging/SKILL.md`
  - `.agent/skills/test-driven-development/SKILL.md`
  - `.agent/skills/verification-before-completion/SKILL.md`
- Services/backend:
  - `services/.agent/skills/api-design-principles/SKILL.md`
  - `services/.agent/skills/spring-boot-crud-patterns/SKILL.md`
  - `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
  - `services/.agent/skills/database-schema-designer/SKILL.md`
- Frontend/storefront:
  - `frontend/.agent/skills/react/SKILL.md`
  - `frontend/.agent/skills/frontend-design/SKILL.md`

## Business Rules

- A purchased coupon must preserve the usage context from the moment of purchase.
- Profile must not depend on public coupon detail availability because the original offer may later become `SOLD_OUT` or `ARCHIVED`.
- Active purchased coupons must clearly show:
  - coupon title and option title;
  - PIN code;
  - merchant name;
  - merchant address when available;
  - merchant phone when available;
  - merchant working hours when available;
  - expiration date;
  - instruction: show PIN/QR to merchant staff.
- Used coupons must show `usedAt` and no primary “copy PIN” CTA.
- Expired/cancelled coupons must not look usable.
- If merchant contact data is missing, UI must explicitly say that details are unavailable instead of inventing fallback business data.
- This stage must not add refund automation, complaint workflow, notification center, QR rendering dependency, or merchant public page.

## Files

- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponPurchaseSnapshotResponse.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/InternalCouponPurchaseControllerTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/client/CouponPurchaseSnapshot.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/OrderItem.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/PurchasedCoupon.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/dto/PurchasedCouponResponse.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Create: `services/order-service/src/main/resources/db/migration/V9__purchased_coupon_usage_snapshot.sql`
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`
- Modify: `frontend/web-app/src/api/orders.ts`
- Create: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- Create: `frontend/web-app/src/components/profile/PurchasedCouponCard.css`
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`
- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`

Do not modify archive/immutability logic, refund logic, payment callbacks, or merchant web cabinet.

---

### Task 1: Add Coupon-Service Purchase Snapshot Usage Context Tests

**Files:**
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/controller/InternalCouponPurchaseControllerTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`

- [ ] **Step 1: Update internal controller test expected JSON**

In `InternalCouponPurchaseControllerTest#getPurchaseSnapshot_returnsCanonicalPurchaseData`, add these builder fields:

```java
.merchantName("SPA Oasis")
.merchantAddress("Ташкент, ул. Амира Темура, 10")
.merchantPhone("+998901234567")
.merchantWorkingHours("10:00-22:00")
```

Then add expectations:

```java
.andExpect(jsonPath("$.data.merchantName").value("SPA Oasis"))
.andExpect(jsonPath("$.data.merchantAddress").value("Ташкент, ул. Амира Темура, 10"))
.andExpect(jsonPath("$.data.merchantPhone").value("+998901234567"))
.andExpect(jsonPath("$.data.merchantWorkingHours").value("10:00-22:00"))
```

- [ ] **Step 2: Add service test for merchant usage context**

In `CouponOfferServiceTest`, add:

```java
@Test
@DisplayName("Purchase snapshot: includes merchant usage context from primary location")
void getPurchaseSnapshot_includesMerchantUsageContext() {
    CouponOffer offer = createTestOffer();
    CouponOption option = CouponOption.builder()
            .id(20L)
            .couponOffer(offer)
            .title("Standard")
            .couponPrice(BigDecimal.valueOf(99000))
            .regularPrice(BigDecimal.valueOf(150000))
            .quantityLimit(10)
            .quantitySold(2)
            .status(CouponOptionStatus.ACTIVE)
            .build();
    offer.setOptions(new ArrayList<>(List.of(option)));
    MerchantLocation location = MerchantLocation.builder()
            .id(30L)
            .merchant(offer.getMerchant())
            .address("Ташкент, ул. Амира Темура, 10")
            .phone("+998901234567")
            .workingHours("10:00-22:00")
            .primary(true)
            .active(true)
            .build();

    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
    when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(1L)).thenReturn(Optional.of(location));

    CouponPurchaseSnapshotResponse result = couponOfferService.getPurchaseSnapshot(1L, 20L);

    assertThat(result.getMerchantId()).isEqualTo(1L);
    assertThat(result.getMerchantName()).isEqualTo("SPA Oasis");
    assertThat(result.getMerchantAddress()).isEqualTo("Ташкент, ул. Амира Темура, 10");
    assertThat(result.getMerchantPhone()).isEqualTo("+998901234567");
    assertThat(result.getMerchantWorkingHours()).isEqualTo("10:00-22:00");
}
```

- [ ] **Step 3: Run coupon-service tests and confirm RED**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalCouponPurchaseControllerTest" --tests "uz.topdim.coupon.service.CouponOfferServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because `CouponPurchaseSnapshotResponse` does not yet have merchant usage fields.

---

### Task 2: Implement Coupon-Service Snapshot Usage Context

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponPurchaseSnapshotResponse.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`

- [ ] **Step 1: Add fields to `CouponPurchaseSnapshotResponse`**

Add:

```java
private String merchantName;
private String merchantAddress;
private String merchantPhone;
private String merchantWorkingHours;
```

- [ ] **Step 2: Map primary location in `getPurchaseSnapshot`**

In `CouponOfferService#getPurchaseSnapshot`, before the builder return, add:

```java
Merchant merchant = offer.getMerchant();
MerchantLocation primaryLocation = merchant != null
        ? merchantLocationRepository.findByMerchantIdAndPrimaryTrue(merchant.getId()).orElse(null)
        : null;
```

Then add these fields to the builder:

```java
.merchantName(merchant != null ? merchant.getName() : null)
.merchantAddress(primaryLocation != null ? primaryLocation.getAddress() : null)
.merchantPhone(primaryLocation != null ? primaryLocation.getPhone() : null)
.merchantWorkingHours(primaryLocation != null ? primaryLocation.getWorkingHours() : null)
```

Keep the existing `merchantId`, `buyUntil`, and `useUntil` builder calls.

- [ ] **Step 3: Run coupon-service tests and confirm GREEN**

Run:

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalCouponPurchaseControllerTest" --tests "uz.topdim.coupon.service.CouponOfferServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

---

### Task 3: Add Order-Service Usage Snapshot Persistence Tests

**Files:**
- Modify: `services/order-service/src/test/java/uz/topdim/order/service/OrderServiceTest.java`

- [ ] **Step 1: Extend existing checkout snapshot test**

Find `createOrder_storesMerchantAndExpiresAtOnOrderItem`. Add to the `snapshot` setup:

```java
snapshot.setMerchantName("SPA Oasis");
snapshot.setMerchantAddress("Ташкент, ул. Амира Темура, 10");
snapshot.setMerchantPhone("+998901234567");
snapshot.setMerchantWorkingHours("10:00-22:00");
```

Add assertions:

```java
assertThat(order.getItems().get(0).getMerchantName()).isEqualTo("SPA Oasis");
assertThat(order.getItems().get(0).getMerchantAddress()).isEqualTo("Ташкент, ул. Амира Темура, 10");
assertThat(order.getItems().get(0).getMerchantPhone()).isEqualTo("+998901234567");
assertThat(order.getItems().get(0).getMerchantWorkingHours()).isEqualTo("10:00-22:00");
```

- [ ] **Step 2: Extend existing purchased coupon generation test**

Find `generateCoupons_copiesMerchantAndExpiresAt`. Add to `OrderItem.builder()`:

```java
.merchantName("SPA Oasis")
.merchantAddress("Ташкент, ул. Амира Темура, 10")
.merchantPhone("+998901234567")
.merchantWorkingHours("10:00-22:00")
```

Add assertions:

```java
assertThat(result.get(0).getMerchantName()).isEqualTo("SPA Oasis");
assertThat(result.get(0).getMerchantAddress()).isEqualTo("Ташкент, ул. Амира Темура, 10");
assertThat(result.get(0).getMerchantPhone()).isEqualTo("+998901234567");
assertThat(result.get(0).getMerchantWorkingHours()).isEqualTo("10:00-22:00");
```

- [ ] **Step 3: Add response mapper test**

Add:

```java
@Test
@DisplayName("Purchased coupon response: exposes usage context for profile")
void mapToCouponResponse_includesUsageContext() {
    PurchasedCoupon coupon = PurchasedCoupon.builder()
            .id(501L)
            .couponOfferId(10L)
            .couponOptionId(20L)
            .couponTitle("SPA")
            .optionTitle("Standard")
            .couponCode("CP-1234")
            .qrToken("qr-token")
            .status(PurchasedCouponStatus.ACTIVE)
            .merchantId(77L)
            .merchantName("SPA Oasis")
            .merchantAddress("Ташкент, ул. Амира Темура, 10")
            .merchantPhone("+998901234567")
            .merchantWorkingHours("10:00-22:00")
            .expiresAt(LocalDateTime.of(2027, 7, 1, 12, 0))
            .build();

    PurchasedCouponResponse response = orderService.mapToCouponResponse(coupon);

    assertThat(response.getCouponOfferId()).isEqualTo(10L);
    assertThat(response.getCouponOptionId()).isEqualTo(20L);
    assertThat(response.getMerchantId()).isEqualTo(77L);
    assertThat(response.getMerchantName()).isEqualTo("SPA Oasis");
    assertThat(response.getMerchantAddress()).isEqualTo("Ташкент, ул. Амира Темура, 10");
    assertThat(response.getMerchantPhone()).isEqualTo("+998901234567");
    assertThat(response.getMerchantWorkingHours()).isEqualTo("10:00-22:00");
}
```

Ensure `PurchasedCouponResponse` is imported:

```java
import uz.topdim.order.dto.PurchasedCouponResponse;
```

- [ ] **Step 4: Run order-service tests and confirm RED**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: FAIL because order-service DTO/entities do not yet have usage context fields.

---

### Task 4: Implement Order-Service Usage Snapshot Persistence

**Files:**
- Modify: `services/order-service/src/main/java/uz/topdim/order/client/CouponPurchaseSnapshot.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/OrderItem.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/entity/PurchasedCoupon.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/dto/PurchasedCouponResponse.java`
- Modify: `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- Create: `services/order-service/src/main/resources/db/migration/V9__purchased_coupon_usage_snapshot.sql`

- [ ] **Step 1: Add fields to `CouponPurchaseSnapshot` client DTO**

Add:

```java
private String merchantName;
private String merchantAddress;
private String merchantPhone;
private String merchantWorkingHours;
```

- [ ] **Step 2: Add fields to `OrderItem`**

Add:

```java
@Column(name = "merchant_name")
private String merchantName;

@Column(name = "merchant_address", columnDefinition = "TEXT")
private String merchantAddress;

@Column(name = "merchant_phone")
private String merchantPhone;

@Column(name = "merchant_working_hours")
private String merchantWorkingHours;
```

- [ ] **Step 3: Add fields to `PurchasedCoupon`**

Add:

```java
@Column(name = "merchant_name")
private String merchantName;

@Column(name = "merchant_address", columnDefinition = "TEXT")
private String merchantAddress;

@Column(name = "merchant_phone")
private String merchantPhone;

@Column(name = "merchant_working_hours")
private String merchantWorkingHours;
```

- [ ] **Step 4: Add fields to `PurchasedCouponResponse`**

Add:

```java
private Long couponOfferId;
private Long couponOptionId;
private Long merchantId;
private String merchantName;
private String merchantAddress;
private String merchantPhone;
private String merchantWorkingHours;
```

- [ ] **Step 5: Add migration**

Create `V9__purchased_coupon_usage_snapshot.sql`:

```sql
-- V9: snapshot merchant usage context for purchased coupon profile UX.
-- Values are copied at checkout/generation time and do not depend on public coupon visibility later.

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS merchant_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS merchant_address TEXT,
    ADD COLUMN IF NOT EXISTS merchant_phone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS merchant_working_hours VARCHAR(255);

ALTER TABLE purchased_coupons
    ADD COLUMN IF NOT EXISTS merchant_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS merchant_address TEXT,
    ADD COLUMN IF NOT EXISTS merchant_phone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS merchant_working_hours VARCHAR(255);
```

- [ ] **Step 6: Copy snapshot fields into order item**

In `OrderService#createOrder`, add to `OrderItem.builder()`:

```java
.merchantName(snapshot.getMerchantName())
.merchantAddress(snapshot.getMerchantAddress())
.merchantPhone(snapshot.getMerchantPhone())
.merchantWorkingHours(snapshot.getMerchantWorkingHours())
```

- [ ] **Step 7: Copy order item fields into purchased coupon**

In `OrderService#generatePurchasedCoupons`, add to `PurchasedCoupon.builder()`:

```java
.merchantName(item.getMerchantName())
.merchantAddress(item.getMerchantAddress())
.merchantPhone(item.getMerchantPhone())
.merchantWorkingHours(item.getMerchantWorkingHours())
```

- [ ] **Step 8: Map fields into response**

In `OrderService#mapToCouponResponse`, add:

```java
.couponOfferId(coupon.getCouponOfferId())
.couponOptionId(coupon.getCouponOptionId())
.merchantId(coupon.getMerchantId())
.merchantName(coupon.getMerchantName())
.merchantAddress(coupon.getMerchantAddress())
.merchantPhone(coupon.getMerchantPhone())
.merchantWorkingHours(coupon.getMerchantWorkingHours())
```

- [ ] **Step 9: Run order-service tests and confirm GREEN**

Run:

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

---

### Task 5: Implement Profile Purchased Coupon Usage Card

**Files:**
- Modify: `frontend/web-app/src/api/orders.ts`
- Create: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- Create: `frontend/web-app/src/components/profile/PurchasedCouponCard.css`
- Modify: `frontend/web-app/src/pages/ProfilePage.tsx`

- [ ] **Step 1: Extend frontend `PurchasedCoupon` type**

In `orders.ts`, add:

```ts
  couponOfferId: number;
  couponOptionId: number;
  merchantId?: number;
  merchantName?: string;
  merchantAddress?: string;
  merchantPhone?: string;
  merchantWorkingHours?: string;
```

- [ ] **Step 2: Create `PurchasedCouponCard.tsx`**

Create `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`:

```tsx
import { AlertCircle, CalendarClock, CheckCircle, Clock, Copy, MapPin, Phone, QrCode, Store } from 'lucide-react';
import type { PurchasedCoupon } from '../../api/orders';
import './PurchasedCouponCard.css';

interface PurchasedCouponCardProps {
  coupon: PurchasedCoupon;
}

function formatDate(date?: string): string {
  if (!date) {
    return 'не указан';
  }

  return new Date(date).toLocaleDateString('ru-RU');
}

function getStatusLabel(status: PurchasedCoupon['status']): string {
  switch (status) {
    case 'ACTIVE':
      return 'Активно';
    case 'USED':
      return 'Использовано';
    case 'EXPIRED':
      return 'Истекло';
    case 'CANCELLED':
      return 'Отменено';
    default:
      return status;
  }
}

function getStatusIcon(status: PurchasedCoupon['status']) {
  if (status === 'ACTIVE') {
    return <Clock size={16} />;
  }

  if (status === 'USED') {
    return <CheckCircle size={16} />;
  }

  return <AlertCircle size={16} />;
}

export default function PurchasedCouponCard({ coupon }: PurchasedCouponCardProps) {
  const isActive = coupon.status === 'ACTIVE';

  const copyCode = async () => {
    await navigator.clipboard.writeText(coupon.couponCode);
  };

  return (
    <article className={`purchased-coupon-card purchased-coupon-card--${coupon.status.toLowerCase()}`}>
      <div className="purchased-coupon-card__header">
        <div>
          <p className="purchased-coupon-card__eyebrow">Купон #{coupon.id}</p>
          <h3>{coupon.couponTitle}</h3>
          <p>{coupon.optionTitle}</p>
        </div>
        <span className={`purchased-coupon-card__status purchased-coupon-card__status--${coupon.status.toLowerCase()}`}>
          {getStatusIcon(coupon.status)}
          {getStatusLabel(coupon.status)}
        </span>
      </div>

      <div className="purchased-coupon-card__code-box">
        <div>
          <span className="purchased-coupon-card__label">Покажите сотруднику</span>
          <strong>{coupon.couponCode || 'Код недоступен'}</strong>
        </div>
        {isActive && coupon.couponCode ? (
          <button type="button" onClick={copyCode}>
            <Copy size={16} />
            Скопировать
          </button>
        ) : null}
      </div>

      <div className="purchased-coupon-card__usage">
        <div className="purchased-coupon-card__usage-item">
          <Store size={16} />
          <span>{coupon.merchantName || 'Название партнёра недоступно'}</span>
        </div>
        <div className="purchased-coupon-card__usage-item">
          <MapPin size={16} />
          <span>{coupon.merchantAddress || 'Адрес партнёра уточните перед визитом'}</span>
        </div>
        {coupon.merchantPhone ? (
          <a className="purchased-coupon-card__usage-item" href={`tel:${coupon.merchantPhone}`}>
            <Phone size={16} />
            <span>{coupon.merchantPhone}</span>
          </a>
        ) : null}
        {coupon.merchantWorkingHours ? (
          <div className="purchased-coupon-card__usage-item">
            <CalendarClock size={16} />
            <span>{coupon.merchantWorkingHours}</span>
          </div>
        ) : null}
      </div>

      <div className="purchased-coupon-card__footer">
        <div>
          {coupon.status === 'USED' && coupon.usedAt
            ? `Использован: ${formatDate(coupon.usedAt)}`
            : `Действует до: ${formatDate(coupon.expiresAt)}`}
        </div>
        {coupon.qrToken ? (
          <div className="purchased-coupon-card__qr-note">
            <QrCode size={16} />
            QR-токен доступен для проверки партнёром
          </div>
        ) : null}
      </div>

      {isActive ? (
        <div className="purchased-coupon-card__help">
          Если партнёр не принимает купон, покажите этот экран и обратитесь в поддержку TopDim.
        </div>
      ) : null}
    </article>
  );
}
```

- [ ] **Step 3: Create `PurchasedCouponCard.css`**

Create `frontend/web-app/src/components/profile/PurchasedCouponCard.css`:

```css
.purchased-coupon-card {
  border: 1px solid rgba(20, 20, 20, 0.08);
  border-radius: 24px;
  padding: 20px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(245, 249, 255, 0.9));
  box-shadow: 0 18px 45px rgba(19, 33, 68, 0.08);
}

.purchased-coupon-card--used,
.purchased-coupon-card--expired,
.purchased-coupon-card--cancelled {
  opacity: 0.78;
}

.purchased-coupon-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.purchased-coupon-card__eyebrow,
.purchased-coupon-card__label {
  margin: 0 0 4px;
  color: var(--text-tertiary);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.purchased-coupon-card h3 {
  margin: 0 0 6px;
  font-size: 20px;
}

.purchased-coupon-card p {
  margin: 0;
  color: var(--text-secondary);
}

.purchased-coupon-card__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 8px 12px;
  font-weight: 700;
  background: rgba(120, 120, 120, 0.12);
  white-space: nowrap;
}

.purchased-coupon-card__status--active {
  color: #0f7a45;
  background: rgba(34, 197, 94, 0.14);
}

.purchased-coupon-card__status--used {
  color: #1f5fbf;
  background: rgba(59, 130, 246, 0.14);
}

.purchased-coupon-card__status--expired,
.purchased-coupon-card__status--cancelled {
  color: #9a3412;
  background: rgba(249, 115, 22, 0.14);
}

.purchased-coupon-card__code-box {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin: 18px 0;
  border-radius: 18px;
  padding: 16px;
  background: #111827;
  color: #fff;
}

.purchased-coupon-card__code-box strong {
  display: block;
  font-size: 28px;
  letter-spacing: 0.08em;
}

.purchased-coupon-card__code-box button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 999px;
  padding: 10px 14px;
  color: #111827;
  background: #fff;
  font-weight: 700;
  cursor: pointer;
}

.purchased-coupon-card__usage {
  display: grid;
  gap: 10px;
}

.purchased-coupon-card__usage-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  color: var(--text-primary);
  text-decoration: none;
}

.purchased-coupon-card__usage-item svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--primary);
}

.purchased-coupon-card__footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  color: var(--text-secondary);
  font-size: 14px;
}

.purchased-coupon-card__qr-note {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.purchased-coupon-card__help {
  margin-top: 14px;
  border-radius: 14px;
  padding: 12px;
  color: #7c2d12;
  background: rgba(251, 146, 60, 0.14);
}

@media (max-width: 640px) {
  .purchased-coupon-card__header,
  .purchased-coupon-card__code-box,
  .purchased-coupon-card__footer {
    flex-direction: column;
    align-items: stretch;
  }
}
```

- [ ] **Step 4: Use the card in `ProfilePage`**

In `ProfilePage.tsx`, remove `PurchasedCoupon` type import if it is used only for inline map typing. Add:

```ts
import PurchasedCouponCard from '../components/profile/PurchasedCouponCard';
```

Replace the whole inline purchased coupon card block inside `coupons.map` with:

```tsx
coupons.map((coupon) => (
  <PurchasedCouponCard key={coupon.id} coupon={coupon} />
))
```

Keep the existing tabs and empty state.

- [ ] **Step 5: Run frontend build**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

---

### Task 6: Update Docs

**Files:**
- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`

- [ ] **Step 1: Update purchased coupon response contract**

In `docs/API_CONTRACT.md`, update `GET /api/v1/orders/my-coupons` response example to include:

```json
{
  "id": 501,
  "couponOfferId": 1,
  "couponOptionId": 10,
  "couponTitle": "Скидка 50% на SPA массаж",
  "optionTitle": "Премиум (90 мин)",
  "couponCode": "TDSP-AB12CD",
  "qrToken": "uuid-unique-qr-token",
  "status": "ACTIVE",
  "merchantId": 77,
  "merchantName": "SPA Oasis",
  "merchantAddress": "Ташкент, ул. Амира Темура, 10",
  "merchantPhone": "+998901234567",
  "merchantWorkingHours": "10:00-22:00",
  "purchasedAt": "2026-03-25T01:05:00",
  "expiresAt": "2026-06-30T23:59:59",
  "usedAt": null
}
```

Add note:

```md
Merchant usage fields are purchase-time snapshots. They are shown in the user's profile even if the original public coupon offer later becomes `SOLD_OUT` or `ARCHIVED`.
```

- [ ] **Step 2: Update PRD purchased coupon UX**

In `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`, update section `9.11. Purchased coupons` to say MVP purchased coupon cards must show:

```md
- PIN/code and QR-token availability note;
- merchant name;
- merchant address if available;
- merchant phone if available;
- merchant working hours if available;
- expiration or used date;
- help text for the case where the partner does not accept the coupon.
```

Add:

```md
Purchased coupon profile UX uses purchase-time merchant snapshots and must not depend on public coupon detail route availability.
```

- [ ] **Step 3: Run docs grep**

Run:

```bash
rg -n "merchantName|merchantAddress|merchantPhone|merchantWorkingHours|purchase-time merchant" docs/API_CONTRACT.md docs/PRODUCT_REQUIREMENTS_DOCUMENT.md
```

Expected: matches in API contract and PRD.

---

### Task 7: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run coupon-service focused tests**

```bash
./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.controller.InternalCouponPurchaseControllerTest" --tests "uz.topdim.coupon.service.CouponOfferServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 2: Run order-service focused tests**

```bash
./gradlew :services:order-service:test --tests "uz.topdim.order.service.OrderServiceTest" -x jacocoTestReport -x jacocoTestCoverageVerification
```

Expected: PASS.

- [ ] **Step 3: Run storefront build**

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

- [ ] **Step 4: Run storefront lint**

```bash
cd frontend/web-app
npm run lint
```

Expected: PASS if the separate frontend lint cleanup task has already been completed. If lint still fails on unrelated pre-existing files, capture exact output and do not hide it.

- [ ] **Step 5: Run contract grep checks**

```bash
rg -n "merchantName|merchantAddress|merchantPhone|merchantWorkingHours" services/coupon-service/src/main/java services/order-service/src/main/java frontend/web-app/src docs/API_CONTRACT.md docs/PRODUCT_REQUIREMENTS_DOCUMENT.md
```

Expected: matches in coupon snapshot DTO/service, order snapshot/entity/response/service, frontend type/card, and docs.

```bash
rg -n "public coupon detail|/coupons/\\$\\{|getById\\(" frontend/web-app/src/pages/ProfilePage.tsx frontend/web-app/src/components/profile
```

Expected: no profile dependency on public coupon detail data for usage context.

---

## Self-Review

- Spec coverage: covers where to use, what to show, validity, missing merchant data behavior, and partner refusal help text.
- Scope check: does not implement notification center, refund automation, complaint workflow, merchant page, or QR rendering dependency.
- Data consistency: usage context is snapshotted at purchase/order time so later public offer status changes do not break user profile.
- Test coverage: includes coupon-service snapshot response, order-service persistence/response mapping, frontend build, lint, and grep checks.
