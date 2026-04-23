# Coupon-Merchant Remainder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** finish the remaining `coupon + merchant` work by hardening publication safety around a publication-ready merchant, completing the canonical read-model, and cleaning up coupon/merchant compatibility layers without touching bazaar work.

**Architecture:** keep `offer_description` and `merchant_locations` as the only real sources of truth, add explicit publication validation in `CouponOfferService`, move admin/storefront rendering to canonical fields, and treat deprecated DTO fields as a temporary compatibility shell until consumers are verified. Publication means the merchant already exists and is publication-ready, not that publication tries to fill merchant data on the fly. The destructive DB cleanup already happened, so this plan focuses on runtime and contract reconciliation rather than schema migration.

**Tech Stack:** Spring Boot, JPA, JUnit 5, Mockito, React 19, TypeScript, Vite

---

### Task 1: Add publication safety for `ACTIVE` transitions

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- Test: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceBusinessLogicTest.java`

- [ ] **Step 1: Write the failing lifecycle tests**

```java
@Test
@DisplayName("approveByMerchant: WAITING_FOR_MERCHANT без publication-ready primary location -> IllegalStateException")
void approveByMerchant_withoutPublicationReadyPrimaryLocation_throws() {
    Merchant merchant = Merchant.builder().id(10L).name("SPA Oasis").build();
    CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);

    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
    when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> couponOfferService.approveByMerchant(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("primary location");
}

@Test
@DisplayName("updateStatus: WAITING_FOR_MERCHANT -> ACTIVE без publication-ready primary location -> IllegalStateException")
void updateStatus_waitingToActiveWithoutPublicationReadyPrimaryLocation_throws() {
    CouponOffer offer = createOffer(CouponStatus.WAITING_FOR_MERCHANT);
    Merchant merchant = Merchant.builder().id(10L).name("SPA Oasis").build();
    offer.setMerchant(merchant);

    when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(offer));
    when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> couponOfferService.updateStatus(10L, CouponStatus.ACTIVE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("primary location");
}
```

- [ ] **Step 2: Run the targeted tests and verify they fail for the right reason**

Run: `./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.CouponOfferServiceBusinessLogicTest"`

Expected: FAIL because `approveByMerchant` and `updateStatus` currently publish to `ACTIVE` without checking publication readiness of the merchant.

- [ ] **Step 3: Implement one publication guard and reuse it in both ACTIVE paths**

```java
private void ensureMerchantReadyForPublication(CouponOffer offer) {
    Merchant merchant = offer.getMerchant();
    if (merchant == null) {
        throw new IllegalStateException("Нельзя публиковать купон без мерчанта");
    }

    MerchantLocation primaryLocation = merchantLocationRepository.findByMerchantIdAndPrimaryTrue(merchant.getId())
            .filter(MerchantLocation::isActive)
            .orElseThrow(() -> new IllegalStateException(
                    "Нельзя публиковать купон без active primary location у мерчанта"));

    if (primaryLocation.getAddress() == null || primaryLocation.getAddress().isBlank()) {
        throw new IllegalStateException(
                "Нельзя публиковать купон без адреса в primary location мерчанта");
    }
}
```

Use it here:

```java
public CouponOfferResponse approveByMerchant(Long id) {
    CouponOffer offer = couponOfferRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Купон не найден"));

    if (offer.getStatus() != CouponStatus.WAITING_FOR_MERCHANT) {
        throw new IllegalStateException(
                "Нельзя одобрить купон из статуса " + offer.getStatus()
                + ". Допустимый: WAITING_FOR_MERCHANT");
    }

    ensureMerchantReadyForPublication(offer);
    offer.setStatus(CouponStatus.ACTIVE);
    couponOfferRepository.save(offer);
    return mapToResponse(offer);
}
```

and here:

```java
if (newStatus == CouponStatus.ACTIVE) {
    ensureMerchantReadyForPublication(offer);
}
offer.setStatus(newStatus);
```

- [ ] **Step 4: Re-run the targeted tests and verify they pass**

Run: `./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.CouponOfferServiceBusinessLogicTest"`

Expected: PASS. The new tests are green and existing lifecycle tests stay green.

- [ ] **Step 5: Add the positive regression proving ACTIVE still works when primary location exists**

```java
@Test
@DisplayName("approveByMerchant: WAITING_FOR_MERCHANT с publication-ready primary location -> ACTIVE")
void approveByMerchant_withPublicationReadyPrimaryLocation_setsActive() {
    Merchant merchant = Merchant.builder().id(10L).name("SPA Oasis").build();
    CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);
    MerchantLocation location = MerchantLocation.builder()
            .id(77L)
            .merchant(merchant)
            .address("Ташкент, ул. Амира Темура, 10")
            .primary(true)
            .active(true)
            .build();

    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
    when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(10L)).thenReturn(Optional.of(location));
    when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(inv -> inv.getArgument(0));

    CouponOfferResponse result = couponOfferService.approveByMerchant(1L);

    assertThat(result.getStatus()).isEqualTo("ACTIVE");
}
```

- [ ] **Step 5a: Add the negative regression for empty address**

```java
@Test
@DisplayName("approveByMerchant: active primary location без адреса -> IllegalStateException")
void approveByMerchant_withPrimaryLocationButBlankAddress_throws() {
    Merchant merchant = Merchant.builder().id(10L).name("SPA Oasis").build();
    CouponOffer offer = createOffer(merchant, CouponStatus.WAITING_FOR_MERCHANT);
    MerchantLocation location = MerchantLocation.builder()
            .id(77L)
            .merchant(merchant)
            .address(" ")
            .primary(true)
            .active(true)
            .build();

    when(couponOfferRepository.findById(1L)).thenReturn(Optional.of(offer));
    when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(10L)).thenReturn(Optional.of(location));

    assertThatThrownBy(() -> couponOfferService.approveByMerchant(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("адрес");
}
```

- [ ] **Step 6: Run the coupon-service business-logic suite once more**

Run: `./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.CouponOfferServiceBusinessLogicTest"`

Expected: PASS with no lifecycle regressions.

- [ ] **Step 7: Commit**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/service/CouponOfferService.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceBusinessLogicTest.java
git commit -m "fix: block coupon publication without primary merchant location"
```

### Task 2: Finish canonical read-model in admin and storefront coupon flows

**Files:**
- Modify: `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx`
- Create: `frontend/web-app/src/utils/couponPreview.ts`
- Modify: `frontend/web-app/src/pages/HomePage.tsx`
- Modify: `frontend/web-app/src/pages/FavoritesPage.tsx`
- Modify: `frontend/web-app/src/pages/CouponCatalogPage.tsx`

- [ ] **Step 1: Remove legacy read fallbacks from the admin review screen**

```tsx
{previewCoupon.offerDescription ? (
  <>
    <Divider orientation="left">Описание предложения</Divider>
    <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{previewCoupon.offerDescription}</Paragraph>
  </>
) : (
  <Alert
    type="warning"
    showIcon
    message="У купона не заполнено canonical описание оффера"
  />
)}

<Divider orientation="left">Контакты</Divider>
<Descriptions bordered column={1} size="small">
  <Descriptions.Item label="Адрес">
    {previewCoupon.merchant?.primaryLocation?.address ?? 'Не указан'}
  </Descriptions.Item>
  <Descriptions.Item label="Телефон">
    {previewCoupon.merchant?.primaryLocation?.phone ?? 'Не указан'}
  </Descriptions.Item>
  <Descriptions.Item label="Время работы">
    {previewCoupon.merchant?.primaryLocation?.workingHours ?? 'Не указано'}
  </Descriptions.Item>
</Descriptions>
```

- [ ] **Step 2: Extract one storefront preview helper**

Create `frontend/web-app/src/utils/couponPreview.ts`:

```ts
export function deriveCouponPreview(offerDescription?: string, maxLength: number = 150): string | undefined {
  const text = offerDescription?.trim();
  if (!text) return undefined;

  const line = text
    .split('\n')
    .map((part) => part.trim())
    .find((part) => part && !part.startsWith('##'));

  if (!line) return undefined;
  return line.length <= maxLength ? line : `${line.slice(0, maxLength - 1)}…`;
}
```

- [ ] **Step 3: Switch Home, Favorites, and Catalog pages to the shared helper**

```tsx
import { deriveCouponPreview } from '../utils/couponPreview';

shortDescription: coupon.shortDescription || deriveCouponPreview(coupon.offerDescription),
```

Apply the same replacement in:
- `frontend/web-app/src/pages/HomePage.tsx`
- `frontend/web-app/src/pages/FavoritesPage.tsx`
- `frontend/web-app/src/pages/CouponCatalogPage.tsx`

- [ ] **Step 4: Build the admin app to verify the review-page refactor**

Run: `npm --prefix frontend/admin-app run build`

Expected: PASS with no TypeScript or Vite errors.

- [ ] **Step 5: Build the web app to verify the shared preview helper**

Run: `npm --prefix frontend/web-app run build`

Expected: PASS with no TypeScript or Vite errors.

- [ ] **Step 6: Do the manual smoke that replaces missing frontend tests**

Run these checks manually:
- open admin coupon review page for a coupon with `offerDescription` and `merchant.primaryLocation`;
- confirm the page no longer relies on legacy coupon contact fields;
- open storefront home, favorites, and catalog;
- confirm teaser text is still human-readable and stable.
- try to publish a coupon whose merchant has no primary address and confirm publication is blocked with an explicit message.

Expected: admin and storefront render canonical data correctly; no blank crashes.

- [ ] **Step 7: Commit**

```bash
git add frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx \
        frontend/web-app/src/utils/couponPreview.ts \
        frontend/web-app/src/pages/HomePage.tsx \
        frontend/web-app/src/pages/FavoritesPage.tsx \
        frontend/web-app/src/pages/CouponCatalogPage.tsx
git commit -m "refactor: finish canonical coupon read model in admin and storefront"
```

### Task 3: Lock coupon and merchant flows to canonical-first behavior with regression tests

**Files:**
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java`
- Modify: `frontend/web-app/src/api/coupons.ts`

- [ ] **Step 1: Write the failing test proving admin coupon create prefers `offerDescription` over legacy text**

```java
@Test
@DisplayName("Создание: offerDescription имеет приоритет над legacy text fields")
void create_prefersCanonicalOfferDescription() {
    CreateCouponOfferRequest request = new CreateCouponOfferRequest();
    request.setTitle("SPA массаж");
    request.setOfferDescription("Canonical text");
    request.setShortDescription("Legacy short");
    request.setFullDescription("Legacy full");
    request.setMerchantId(1L);
    request.setCategoryId(1L);
    request.setFromPrice(BigDecimal.valueOf(120000));
    request.setBuyUntil(LocalDateTime.now().plusDays(30));
    request.setUseUntil(LocalDateTime.now().plusDays(60));
    request.setCoverImageUrl("/cover.jpg");

    when(merchantRepository.findById(1L)).thenReturn(Optional.of(createMerchant()));
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
    when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(inv -> inv.getArgument(0));

    couponOfferService.create(request, 99L, "moderator@test");

    ArgumentCaptor<CouponOffer> captor = ArgumentCaptor.forClass(CouponOffer.class);
    verify(couponOfferRepository).save(captor.capture());
    assertThat(captor.getValue().getOfferDescription()).isEqualTo("Canonical text");
}
```

- [ ] **Step 2: Write the failing partner-flow regression proving the same precedence**

```java
@Test
@DisplayName("createCouponOffer: offerDescription имеет приоритет над legacy text fields")
void createCouponOffer_prefersCanonicalOfferDescription() {
    Merchant merchant = createMerchant();
    when(merchantRepository.findByUserId(10L)).thenReturn(Optional.of(merchant));
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
    when(couponOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CreateCouponOfferRequest request = createRequest();
    request.setOfferDescription("Canonical text");
    request.setShortDescription("Legacy short");
    request.setFullDescription("Legacy full");

    partnerCouponService.createCouponOffer(10L, request);

    ArgumentCaptor<CouponOffer> captor = ArgumentCaptor.forClass(CouponOffer.class);
    verify(couponOfferRepository).save(captor.capture());
    assertThat(captor.getValue().getOfferDescription()).isEqualTo("Canonical text");
}
```

- [ ] **Step 3: Add the merchant regression for `locations[]` primary normalization**

```java
@Test
@DisplayName("Создание: locations без primary -> первый location становится primary")
void createMerchant_locationsWithoutPrimary_promotesFirst() {
    CreateMerchantRequest request = new CreateMerchantRequest();
    request.setName("SPA Oasis");

    CreateMerchantRequest.LocationRequest first = new CreateMerchantRequest.LocationRequest();
    first.setAddress("Tashkent");
    first.setPhone("+998 90 123 45 67");

    CreateMerchantRequest.LocationRequest second = new CreateMerchantRequest.LocationRequest();
    second.setAddress("Samarkand");

    request.setLocations(List.of(first, second));

    when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> {
        Merchant merchant = inv.getArgument(0);
        merchant.setId(30L);
        return merchant;
    });
    when(merchantRepository.findById(30L)).thenReturn(Optional.of(
            Merchant.builder().id(30L).name("SPA Oasis").active(true).build()));

    merchantService.createMerchant(request);

    ArgumentCaptor<MerchantLocation> captor = ArgumentCaptor.forClass(MerchantLocation.class);
    verify(merchantLocationRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues().get(0).isPrimary()).isTrue();
    assertThat(captor.getAllValues().get(1).isPrimary()).isFalse();
}
```

- [ ] **Step 4: Run the targeted backend regression suite and verify the failures**

Run: `./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.PartnerCouponServiceTest" --tests "uz.topdim.coupon.service.MerchantServiceTest"`

Expected: FAIL only where coverage is missing or assertions expose canonical-precedence gaps.

- [ ] **Step 5: Implement only the minimal fixes needed for the new regression tests**

Likely code outcomes:

```java
String offerDesc = request.getOfferDescription();
if (offerDesc == null || offerDesc.isBlank()) {
    offerDesc = buildOfferDescription(
            request.getShortDescription(),
            request.getFullDescription(),
            request.getTerms(),
            request.getUsageRules(),
            request.getHowToUse());
}
```

and, if needed in merchant service:

```java
if (primaryCount == 0) {
    normalizedLocations.get(0).setPrimary(true);
}
```

If these behaviors already exist, keep production code unchanged and land the tests only.

- [ ] **Step 6: Make frontend coupon types explicitly canonical-first**

Update `frontend/web-app/src/api/coupons.ts` comments and ordering so canonical fields are the primary shape and deprecated fields are clearly marked as compatibility-only:

```ts
export interface CouponOffer {
  id: number;
  title: string;
  offerDescription?: string;
  merchant: {
    id: number;
    name: string;
    logoUrl?: string;
    description?: string;
    primaryLocation?: MerchantPrimaryLocation;
  };
  // compatibility-only, remove after final cleanup
  shortDescription?: string;
  fullDescription?: string;
  terms?: string;
  usageRules?: string;
  howToUse?: string;
  category: { id: number; name: string; slug: string; iconUrl?: string };
  oldPrice?: number;
  fromPrice: number;
  discountPercent?: number;
  coverImageUrl?: string;
  buyUntil: string;
  useUntil: string;
  giftAvailable: boolean;
  status: string;
  totalSold: number;
  viewCount: number;
  averageRating?: number;
  reviewCount?: number;
  options: CouponOption[];
  images: string[];
  createdAt: string;
}
```

- [ ] **Step 7: Re-run the targeted regression suite**

Run: `./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.PartnerCouponServiceTest" --tests "uz.topdim.coupon.service.MerchantServiceTest"`

Expected: PASS. New regression coverage is green and canonical-first behavior is locked in.

- [ ] **Step 8: Commit**

```bash
git add services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java \
        frontend/web-app/src/api/coupons.ts
git commit -m "test: lock coupon and merchant flows to canonical-first behavior"
```

### Task 4: Remove deprecated coupon and merchant contract fields after consumer verification

**Files:**
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponOfferResponse.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantResponse.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java`
- Modify: `services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateMerchantRequest.java`
- Modify: `frontend/web-app/src/api/coupons.ts`
- Modify: `frontend/admin-app/src/features/coupons/CouponFormPage.tsx`
- Modify: `frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java`
- Modify: `services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java`

- [ ] **Step 1: Search the workspace for remaining deprecated coupon/merchant field usage**

Run: `rg -n "shortDescription|fullDescription|terms|usageRules|howToUse|address\\?|phone\\?|workingHours\\?" services/coupon-service/src/main frontend/admin-app/src frontend/web-app/src`

Expected: the remaining hits are either compatibility DTO definitions or consciously preserved non-coupon directory flows.

- [ ] **Step 2: Write the failing compile pass by removing deprecated fields from one backend DTO**

Start with `CouponOfferResponse`:

```java
public class CouponOfferResponse {
    private Long id;
    private String title;
    private String offerDescription;
    private MerchantSummary merchant;
    private CategorySummary category;
    private BigDecimal oldPrice;
    private BigDecimal fromPrice;
    private Integer discountPercent;
    private String coverImageUrl;
    private LocalDateTime buyUntil;
    private LocalDateTime useUntil;
    private boolean giftAvailable;
    private String status;
    private Long assignedModeratorId;
    private String assignedModeratorName;
    private String revisionComment;
    private int totalSold;
    private int redeemedCount;
    private BigDecimal totalTurnover;
    private int viewCount;
    private Double averageRating;
    private int reviewCount;
    private List<CouponOptionResponse> options;
    private List<String> images;
    private LocalDateTime createdAt;
}
```

Expected failure: callers, tests, or frontend types that still rely on deprecated fields will now break at compile/build time.

- [ ] **Step 3: Remove deprecated request/response fields from coupon and merchant DTOs**

Apply the same cleanup to:

```java
// CreateCouponOfferRequest
private String title;
private String offerDescription;
private Long merchantId;
private Long categoryId;
private BigDecimal oldPrice;
private BigDecimal fromPrice;
private Integer discountPercent;
private String coverImageUrl;
private LocalDateTime buyUntil;
private LocalDateTime useUntil;
private boolean giftAvailable;
private List<CreateCouponOptionRequest> options;
private List<String> images;

// MerchantResponse
private Long id;
private String name;
private String description;
private String logoUrl;
private String coverUrl;
private String email;
private String website;
private String contactPerson;
private boolean active;
private MerchantLocationResponse primaryLocation;
private List<MerchantLocationResponse> locations;
```

- [ ] **Step 4: Remove deprecated fields from frontend coupon types and admin assumptions**

```ts
export interface CouponOffer {
  id: number;
  title: string;
  offerDescription?: string;
  merchant: {
    id: number;
    name: string;
    logoUrl?: string;
    description?: string;
    primaryLocation?: MerchantPrimaryLocation;
  };
  category: { id: number; name: string; slug: string; iconUrl?: string };
  oldPrice?: number;
  fromPrice: number;
  discountPercent?: number;
  coverImageUrl?: string;
  buyUntil: string;
  useUntil: string;
  giftAvailable: boolean;
  status: string;
  totalSold: number;
  viewCount: number;
  averageRating?: number;
  reviewCount?: number;
  options: CouponOption[];
  images: string[];
  createdAt: string;
}
```

Also delete any admin rendering branches that still mention removed coupon/merchant legacy fields.

- [ ] **Step 5: Run backend tests to catch contract fallout**

Run: `./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.PartnerCouponServiceTest" --tests "uz.topdim.coupon.service.MerchantServiceTest" --tests "uz.topdim.coupon.service.CouponOfferServiceBusinessLogicTest"`

Expected: FAIL only where removed DTO fields still leak into service tests or mappers.

- [ ] **Step 6: Fix the remaining call sites and rerun backend tests**

Run: `./gradlew :services:coupon-service:test --tests "uz.topdim.coupon.service.CouponOfferServiceTest" --tests "uz.topdim.coupon.service.PartnerCouponServiceTest" --tests "uz.topdim.coupon.service.MerchantServiceTest" --tests "uz.topdim.coupon.service.CouponOfferServiceBusinessLogicTest"`

Expected: PASS.

- [ ] **Step 7: Rebuild both frontends**

Run:
- `npm --prefix frontend/admin-app run build`
- `npm --prefix frontend/web-app run build`

Expected: PASS. No TypeScript references to removed coupon/merchant fields remain.

- [ ] **Step 8: Commit**

```bash
git add services/coupon-service/src/main/java/uz/topdim/coupon/dto/CouponOfferResponse.java \
        services/coupon-service/src/main/java/uz/topdim/coupon/dto/MerchantResponse.java \
        services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateCouponOfferRequest.java \
        services/coupon-service/src/main/java/uz/topdim/coupon/dto/CreateMerchantRequest.java \
        frontend/web-app/src/api/coupons.ts \
        frontend/admin-app/src/features/coupons/CouponFormPage.tsx \
        frontend/admin-app/src/features/coupons/MerchantReviewPage.tsx \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/CouponOfferServiceTest.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/PartnerCouponServiceTest.java \
        services/coupon-service/src/test/java/uz/topdim/coupon/service/MerchantServiceTest.java
git commit -m "refactor: remove deprecated coupon and merchant contract fields"
```
