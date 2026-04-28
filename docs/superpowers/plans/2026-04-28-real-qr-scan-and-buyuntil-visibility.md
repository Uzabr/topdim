# Real QR Scan And BuyUntil Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace visible QR token fallback with real QR-code display/scanning, and hide coupons whose purchase deadline (`buyUntil`) has already passed from public storefront surfaces.

**Architecture:** Keep backend redemption API based on `qrToken`, but make the token an internal QR payload instead of visible user-facing text. Public coupon visibility must be enforced in `coupon-service`, not only in frontend, so expired-by-purchase coupons cannot appear in catalog, search, top-selling, or public detail page.

**Tech Stack:** React 19 + Vite frontend apps, Ant Design partner app, Spring Boot coupon-service, JUnit 5/Mockito.

---

## Required Context And Skills

Before changing code, read:

- `AGENTS.md`
- `docs/README.md`
- `docs/product/flows/coupon-flow.md`
- `docs/frontend/README.md`
- `docs/backend/README.md`
- `docs/qa/README.md`

Use relevant skills:

- `superpowers:test-driven-development`
- `superpowers:systematic-debugging`
- `superpowers:verification-before-completion`
- `frontend/.agent/skills/react/SKILL.md`
- `frontend/.agent/skills/frontend-design/SKILL.md`
- `services/.agent/skills/spring-boot-test-patterns/SKILL.md`
- `services/.agent/skills/api-design-principles/SKILL.md`

---

## Business Decisions

- Покупателю нельзя показывать `qrToken` как текст.
- Покупателю нужно показывать настоящий QR-код.
- QR payload может содержать `qrToken`, но только внутри QR.
- Формат payload: `TOPDIM-QR:${coupon.qrToken}`.
- У кассира должен быть camera QR scan flow.
- Ручной fallback оставляем через PIN-код.
- Купоны с `buyUntil < now` не должны быть публично видимыми на сайте.
- `buyUntil` означает “до какого времени можно купить”.
- `useUntil` / `expiresAt` означает “до какого времени уже купленный купон можно использовать”.
- Admin/internal endpoints не должны терять доступ к купонам с прошедшим `buyUntil`.

---

## Current Files

Frontend web app:

- `frontend/web-app/package.json`
- `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- `frontend/web-app/src/components/profile/PurchasedCouponCard.css`

Frontend partner app:

- `frontend/partner/package.json`
- `frontend/partner/src/pages/RedeemPage.tsx`

Backend coupon-service:

- `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferRepository.java`
- `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- `services/coupon-service/src/test/java/uz/topdim/coupon/controller/CouponPublicVisibilityTest.java`

---

### Task 1: Render Real QR Code In Buyer Profile

**Files:**
- Modify: `frontend/web-app/package.json`
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.tsx`
- Modify: `frontend/web-app/src/components/profile/PurchasedCouponCard.css`

- [ ] **Step 1: Add QR rendering dependency**

Add one dependency to `frontend/web-app`:

```bash
cd frontend/web-app
npm install qrcode.react
```

If the team prefers another package, `react-qr-code` is acceptable, but document the chosen package in the final report.

- [ ] **Step 2: Replace visible token with QR code**

In `PurchasedCouponCard.tsx`:

1. Import QR renderer:

```tsx
import { QRCodeSVG } from 'qrcode.react';
```

2. Add payload helper near the component:

```tsx
function buildQrPayload(qrToken?: string): string {
  return qrToken ? `TOPDIM-QR:${qrToken}` : '';
}
```

3. Keep PIN-code UI and copy PIN button.

4. Remove visible `<code>{coupon.qrToken}</code>` and “Скопировать QR” button from the buyer UI.

5. For ACTIVE coupons with `qrToken`, render:

```tsx
{isActive && coupon.qrToken ? (
  <div className="purchased-coupon-card__qr-box">
    <div className="purchased-coupon-card__qr-frame" aria-label="QR-код купона TopDim">
      <QRCodeSVG
        value={buildQrPayload(coupon.qrToken)}
        size={164}
        level="M"
        includeMargin
      />
    </div>
    <div className="purchased-coupon-card__qr-copy">
      <span className="purchased-coupon-card__label">QR-код купона</span>
      <p>Покажите этот QR-код кассиру партнёра для погашения.</p>
    </div>
  </div>
) : null}
```

6. Change QR footer note to:

```tsx
QR-код доступен для проверки партнёром
```

- [ ] **Step 3: Update QR styles**

In `PurchasedCouponCard.css`, replace the token-style `.purchased-coupon-card__qr-box code` rules with QR visual rules:

```css
.purchased-coupon-card__qr-box {
  margin-top: 0.75rem;
  padding: 1rem;
  border: 1px solid rgba(34, 197, 94, 0.24);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.08), rgba(255, 255, 255, 0.72));
  display: flex;
  align-items: center;
  gap: 1rem;
}

.purchased-coupon-card__qr-frame {
  width: 184px;
  height: 184px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
}

.purchased-coupon-card__qr-copy p {
  margin: 0.35rem 0 0;
  color: var(--text-secondary);
  line-height: 1.5;
}

@media (max-width: 640px) {
  .purchased-coupon-card__qr-box {
    align-items: center;
    flex-direction: column;
    text-align: center;
  }
}
```

- [ ] **Step 4: Build web app**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: build passes.

---

### Task 2: Add Camera QR Scan Flow In Partner App

**Files:**
- Modify: `frontend/partner/package.json`
- Modify: `frontend/partner/src/pages/RedeemPage.tsx`

- [ ] **Step 1: Add QR scanner dependency**

Use a browser camera scanner package:

```bash
cd frontend/partner
npm install html5-qrcode
```

If using `@zxing/browser` instead, keep the same behavior and mention the package in final report.

- [ ] **Step 2: Replace “QR-token” UX with “Scan QR” UX**

In `RedeemPage.tsx`:

1. Keep PIN tab unchanged as fallback.

2. Rename QR tab label from:

```tsx
QR-токен
```

to:

```tsx
Сканировать QR
```

3. Do not show an input whose main purpose is manual QR-token entry.

4. Add state:

```tsx
const [isScanning, setIsScanning] = useState(false);
const [scannerError, setScannerError] = useState('');
```

5. Add parser:

```tsx
function parseTopDimQrPayload(value: string): string | null {
  const prefix = 'TOPDIM-QR:';
  if (!value.startsWith(prefix)) {
    return null;
  }
  const token = value.slice(prefix.length).trim();
  return token || null;
}
```

6. Add redeem helper:

```tsx
const redeemQrToken = async (token: string) => {
  setLoading(true);
  setErrorText('');
  setScannerError('');
  try {
    const res = await api.post('/api/v1/partner/redemptions/qr', { qrToken: token });
    setResult(res.data.data);
    message.success('Купон погашен по QR!');
  } catch (err: any) {
    const msg = err.response?.data?.message || 'Ошибка погашения';
    setErrorText(msg);
    message.error(msg);
  } finally {
    setLoading(false);
  }
};
```

- [ ] **Step 3: Implement scanner lifecycle safely**

Use `html5-qrcode` with a dedicated container:

```tsx
<div id="topdim-qr-reader" style={{ width: '100%', minHeight: 280 }} />
```

Implement start/stop behavior with cleanup on unmount:

```tsx
useEffect(() => {
  return () => {
    stopScannerRef.current?.();
  };
}, []);
```

Recommended internal pattern:

```tsx
const stopScannerRef = useRef<(() => Promise<void>) | null>(null);
```

When scan succeeds:

1. Stop scanner.
2. Parse payload.
3. If invalid, show `Это не QR-код TopDim`.
4. If valid, call `redeemQrToken(token)`.

If camera permission fails, show:

```text
Не удалось открыть камеру. Проверьте разрешение браузера или используйте PIN-код.
```

- [ ] **Step 4: Keep PIN-only manual fallback**

Do not add a visible “manual QR token” input as a primary fallback. The fallback is PIN.

Acceptable hidden/dev-only fallback is not needed for MVP.

- [ ] **Step 5: Build partner app**

Run:

```bash
cd frontend/partner
npm run build
```

Expected: build passes.

---

### Task 3: Enforce Public Coupon Visibility By BuyUntil

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/repository/CouponOfferRepository.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`

- [ ] **Step 1: Add repository methods for active and purchasable offers**

In `CouponOfferRepository.java`, add query methods that include:

```sql
c.status = :status AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
```

Required methods:

```java
@Query("""
        SELECT c FROM CouponOffer c
         WHERE c.status = :status
           AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
        """)
Page<CouponOffer> findPublicByStatus(@Param("status") CouponStatus status,
                                      @Param("now") java.time.LocalDateTime now,
                                      Pageable pageable);

@Query("""
        SELECT c FROM CouponOffer c
         WHERE c.status = :status
           AND c.category.id = :categoryId
           AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
        """)
Page<CouponOffer> findPublicByStatusAndCategoryId(@Param("status") CouponStatus status,
                                                   @Param("categoryId") Long categoryId,
                                                   @Param("now") java.time.LocalDateTime now,
                                                   Pageable pageable);

@Query("""
        SELECT c FROM CouponOffer c
         WHERE c.status = :status
           AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
           AND (
                LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.offerDescription) LIKE LOWER(CONCAT('%', :search, '%'))
           )
        """)
Page<CouponOffer> searchPublicByTitleOrDescription(@Param("status") CouponStatus status,
                                                    @Param("search") String search,
                                                    @Param("now") java.time.LocalDateTime now,
                                                    Pageable pageable);

@Query("""
        SELECT c FROM CouponOffer c
         WHERE c.status = 'ACTIVE'
           AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
         ORDER BY c.totalSold DESC
        """)
List<CouponOffer> findPublicTopSelling(@Param("now") java.time.LocalDateTime now,
                                        Pageable pageable);
```

- [ ] **Step 2: Update public service methods**

In `CouponOfferService.java`:

1. In `getCatalog`, compute:

```java
LocalDateTime now = LocalDateTime.now();
```

2. Replace public repository calls:

```java
searchByTitleOrDescription(...)
findByStatusAndCategoryId(...)
findByStatus(...)
```

with:

```java
searchPublicByTitleOrDescription(...)
findPublicByStatusAndCategoryId(...)
findPublicByStatus(...)
```

3. In `getById`, after `status` check add:

```java
if (offer.getBuyUntil() != null && offer.getBuyUntil().isBefore(LocalDateTime.now())) {
    throw new ResourceNotFoundException("Купон не найден");
}
```

4. In `getTopSelling`, replace:

```java
couponOfferRepository.findTopSelling(PageRequest.of(0, limit))
```

with:

```java
couponOfferRepository.findPublicTopSelling(LocalDateTime.now(), PageRequest.of(0, limit))
```

Do not change admin/internal methods.

- [ ] **Step 3: Add service tests**

In `CouponOfferServiceTest.java`, add tests:

```java
@Test
@DisplayName("Каталог: публичный список запрашивает только ACTIVE купоны с buyUntil в будущем")
void getCatalog_noFilter_usesPublicVisibilityQuery() {
    CouponOffer offer = createTestOffer();
    Page<CouponOffer> page = new PageImpl<>(List.of(offer));
    when(couponOfferRepository.findPublicByStatus(eq(CouponStatus.ACTIVE), any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(page);

    Page<CouponOfferResponse> result = couponOfferService.getCatalog(null, null, "popular", 0, 20);

    assertThat(result.getContent()).hasSize(1);
    verify(couponOfferRepository).findPublicByStatus(eq(CouponStatus.ACTIVE), any(LocalDateTime.class), any(Pageable.class));
    verify(couponOfferRepository, never()).findByStatus(eq(CouponStatus.ACTIVE), any(Pageable.class));
}

@Test
@DisplayName("Public getById: ACTIVE coupon with expired buyUntil returns not found")
void getById_activeButBuyUntilExpired_throwsNotFound() {
    CouponOffer offer = createTestOffer();
    offer.setStatus(CouponStatus.ACTIVE);
    offer.setBuyUntil(LocalDateTime.now().minusMinutes(1));
    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));

    assertThatThrownBy(() -> couponOfferService.getById(1L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("не найден");

    verify(couponOfferRepository, never()).incrementViewCount(anyLong());
}

@Test
@DisplayName("Top selling: excludes ACTIVE coupons with expired buyUntil")
void getTopSelling_usesPublicTopSellingQuery() {
    CouponOffer offer = createTestOffer();
    when(couponOfferRepository.findPublicTopSelling(any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(List.of(offer));

    List<CouponOfferResponse> result = couponOfferService.getTopSelling(10);

    assertThat(result).hasSize(1);
    verify(couponOfferRepository).findPublicTopSelling(any(LocalDateTime.class), any(Pageable.class));
    verify(couponOfferRepository, never()).findTopSelling(any(Pageable.class));
}
```

Ensure imports include:

```java
import java.time.LocalDateTime;
```

- [ ] **Step 4: Run coupon-service tests**

Run:

```bash
./gradlew :services:coupon-service:test
```

Expected: tests pass.

---

### Task 4: Update QA Checklist

**Files:**
- Modify: `docs/qa/buyer-purchase-redemption-checklist.md` if it exists
- Otherwise modify: `docs/qa/README.md`

- [ ] Add manual checks:

```markdown
## Real QR Checks

- [ ] Buyer profile shows real QR-code image for ACTIVE purchased coupon.
- [ ] Buyer profile does not show raw qrToken text.
- [ ] Partner cashier can scan QR with camera and redeem coupon.
- [ ] Invalid QR payload shows “Это не QR-код TopDim”.
- [ ] Camera permission failure tells cashier to use PIN fallback.

## BuyUntil Public Visibility Checks

- [ ] ACTIVE coupon with future buyUntil appears in catalog.
- [ ] ACTIVE coupon with past buyUntil does not appear in catalog.
- [ ] ACTIVE coupon with past buyUntil returns 404 on public detail page.
- [ ] ACTIVE coupon with past buyUntil does not appear in top-selling.
- [ ] Admin can still open/manage the coupon internally.
```

---

### Task 5: Final Verification

- [ ] Run backend:

```bash
./gradlew :services:coupon-service:test
```

- [ ] Run frontend:

```bash
cd frontend/web-app
npm run build
```

```bash
cd frontend/partner
npm run build
```

- [ ] Final search:

```bash
rg -n "QR-токен|qrToken}</code>|Скопировать QR|Введите QR-токен" frontend/web-app/src frontend/partner/src
```

Expected:

- No buyer-facing raw QR token text in `frontend/web-app`.
- Partner app may contain internal variable names like `qrToken`, but visible labels should say QR scan, not QR token input.

---

## Final Report Required

Report:

- selected QR render package;
- selected QR scan package;
- changed files;
- backend tests added;
- frontend behavior changed;
- verification commands and results.

