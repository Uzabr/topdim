# Storefront Truthfulness And Contract Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the public coupon storefront from silently showing demo coupons/categories in production flows, and align docs/contracts with the current redeem and purchased-coupon behavior.

**Architecture:** Keep backend untouched unless a frontend build exposes a type mismatch. Add one small storefront mapper for API coupon cards, then migrate catalog/home/favorites/search/detail related deals to real API states: loading, empty, unavailable, or real data. Update docs to match the trusted `X-Merchant-Id` redemption contract and the current purchased coupon statuses.

**Tech Stack:** React 19, TypeScript, TanStack Query, Zustand, Spring Boot docs/contracts, Vite build.

---

## Scope

Implement only storefront truthfulness and contract sync:

- Remove production coupon UI dependency on `DEMO_COUPONS`, `DEMO_CATEGORIES`, and `topdimDeals`.
- Keep legitimate image placeholders inside `CouponCard` and `ImageSlider`; those are visual fallbacks, not fake business data.
- Keep bazaar demo/fallback work out of scope unless a coupon page imports it directly.
- Update docs for redeem contract: `merchantId` comes from trusted `X-Merchant-Id` header, not request body.
- Update docs for purchased coupon statuses to match code: `ACTIVE`, `USED`, `EXPIRED`, `CANCELLED`.

Do not implement:

- Bazaar/directory migration.
- Redis cache restoration.
- New backend endpoints.
- New search service behavior beyond using existing `couponsApi.getCatalog({ search })`.
- New test framework installation.

## Required Local Skills

- Root: `.agent/skills/systematic-debugging/SKILL.md`
- Root: `.agent/skills/verification-before-completion/SKILL.md`
- Frontend: `frontend/.agent/skills/react/SKILL.md`
- Frontend: `frontend/.agent/skills/frontend-design/SKILL.md`
- Services: `services/.agent/skills/api-design-principles/SKILL.md` for docs/contract wording

This task is mostly frontend/docs cleanup. There is no frontend unit test runner in `frontend/web-app/package.json`, so verification relies on TypeScript build, Vite build, lint where practical, and targeted `rg` checks.

## Business Rules

- Production coupon pages must never show demo coupons as if they are live offers.
- API loading, empty, and error states must be explicit.
- User favorites must not display favorite IDs from localStorage by matching them against demo coupons.
- Search coupon results must come from `/api/v1/coupons?search=<term>`, not from `DEMO_COUPONS`.
- Related deals on coupon detail must come from real catalog data or be hidden.
- Docs must not tell clients to send untrusted `merchantId` in redeem body.

## Files

- Create: `frontend/web-app/src/utils/couponCardMapper.ts`
- Modify: `frontend/web-app/src/pages/CouponCatalogPage.tsx`
- Modify: `frontend/web-app/src/pages/HomePage.tsx`
- Modify: `frontend/web-app/src/pages/FavoritesPage.tsx`
- Modify: `frontend/web-app/src/pages/SearchPage.tsx`
- Modify: `frontend/web-app/src/pages/CouponDetailPage.tsx`
- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/COUPON_FLOW.md`
- Modify: `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`
- Modify: `services/order-service/README.md`

---

### Task 1: Add Shared Coupon Card Mapper

**Files:**
- Create: `frontend/web-app/src/utils/couponCardMapper.ts`
- Modify later tasks to consume it.

- [ ] **Step 1: Create the mapper**

Create `frontend/web-app/src/utils/couponCardMapper.ts`:

```ts
import type { CouponOffer } from '../api/coupons';
import type { CouponCardData } from '../components/coupon/CouponCard';
import { deriveCouponPreview } from './couponPreview';

export function mapCouponOfferToCardData(coupon: CouponOffer): CouponCardData {
  const discount =
    coupon.discountPercent ||
    (coupon.oldPrice
      ? Math.round((1 - coupon.fromPrice / coupon.oldPrice) * 100)
      : 0);

  return {
    id: coupon.id,
    title: coupon.title,
    offerDescription: coupon.offerDescription,
    shortDescription: deriveCouponPreview(coupon.offerDescription),
    merchant: coupon.merchant,
    category: coupon.category,
    oldPrice: coupon.oldPrice,
    fromPrice: coupon.fromPrice,
    discountPercent: coupon.discountPercent,
    coverImageUrl: coupon.coverImageUrl,
    totalSold: coupon.totalSold,
    rating: coupon.averageRating,
    reviewCount: coupon.reviewCount,
    address: coupon.merchant?.primaryLocation?.address,
    location: coupon.merchant?.primaryLocation?.address,
    giftAvailable: coupon.giftAvailable,
    isHot: discount >= 50,
  };
}
```

- [ ] **Step 2: Run TypeScript build**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: PASS. If TypeScript reports a wrong import path or property name, fix only that import/property mismatch and rerun this same command before moving to pages.

---

### Task 2: Make Coupon Catalog Real-Data Only

**Files:**
- Modify: `frontend/web-app/src/pages/CouponCatalogPage.tsx`

- [ ] **Step 1: Remove demo data declarations and imports**

Remove the static declarations named `DEMO_CATEGORIES`, `DEMO_COUPONS`, and `mapToCardData`.

Also remove these now-unused imports from `CouponCatalogPage.tsx`:

```ts
import type { Category, CouponOffer } from '../api/coupons';
import type { CouponCardData } from '../components/coupon/CouponCard';
import { deriveCouponPreview } from '../utils/couponPreview';
```

Add:

```ts
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
```

- [ ] **Step 2: Track loading and error states explicitly**

Change category query:

```ts
const {
  data: categoriesData,
  isLoading: isCategoriesLoading,
  isError: isCategoriesError,
} = useQuery({
  queryKey: ['categories'],
  queryFn: () => couponsApi.getCategories(),
  select: (res) => res.data.data,
});
```

Change catalog query:

```ts
const {
  data: couponsData,
  isLoading,
  isError,
} = useQuery({
  queryKey: ['coupons-catalog', activeCategory, search, sortBy, page],
  queryFn: () => couponsApi.getCatalog({
    categoryId: activeCategory ?? undefined,
    search: search || undefined,
    sortBy,
    page,
    size: 20,
  }),
  select: (res) => res.data.data,
});
```

Set real-only data:

```ts
const categories = categoriesData ?? [];
const coupons = couponsData?.content ?? [];
const totalPages = couponsData?.totalPages ?? 1;
const totalElements = couponsData?.totalElements ?? 0;
```

- [ ] **Step 3: Render category and catalog states**

For categories, keep the "All" chip and show API categories only when present:

```tsx
<div className="catalog-categories container">
  <button
    className={`filter-chip ${activeCategory === null ? 'filter-chip--active' : ''}`}
    onClick={() => { setActiveCategory(null); setPage(0); }}
  >
    Все
  </button>
  {!isCategoriesLoading && !isCategoriesError && categories.map((cat) => (
    <button
      key={cat.id}
      className={`filter-chip ${activeCategory === cat.id ? 'filter-chip--active' : ''}`}
      onClick={() => { setActiveCategory(cat.id); setPage(0); }}
    >
      <CategoryIcon slug={cat.slug} /> {cat.name}
    </button>
  ))}
</div>
```

For results:

```tsx
{isLoading ? (
  <div className="catalog-loading">
    {Array.from({ length: 6 }).map((_, i) => (
      <div key={i} className="skeleton" style={{ height: 280, borderRadius: 16 }} />
    ))}
  </div>
) : isError ? (
  <div className="catalog-empty">
    <span className="catalog-empty__icon">!</span>
    <h3>Не удалось загрузить купоны</h3>
    <p>Попробуйте обновить страницу чуть позже</p>
  </div>
) : coupons.length === 0 ? (
  <div className="catalog-empty">
    <span className="catalog-empty__icon">🔍</span>
    <h3>Купоны не найдены</h3>
    <p>Попробуйте изменить фильтры</p>
  </div>
) : (
  <div className={`coupon-grid ${viewMode === 'list' ? 'coupon-grid--list' : ''}`}>
    {coupons.map((coupon) => (
      <CouponCard key={coupon.id} coupon={mapCouponOfferToCardData(coupon)} layout="card" />
    ))}
  </div>
)}
```

- [ ] **Step 4: Run build**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

---

### Task 3: Make Home And Favorites Real-Data Only

**Files:**
- Modify: `frontend/web-app/src/pages/HomePage.tsx`
- Modify: `frontend/web-app/src/pages/FavoritesPage.tsx`

- [ ] **Step 1: Update `HomePage` imports**

Remove:

```ts
import { topdimCategories, topdimDeals } from '../data/topdim';
import { deriveCouponPreview } from '../utils/couponPreview';
```

Add:

```ts
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
```

- [ ] **Step 2: Update `HomePage` data flow**

Change categories:

```ts
const categories: Category[] = categoriesData ?? [];
```

Change deals mapping:

```ts
const mappedDeals: CouponCardData[] = useMemo(() => {
  return apiDeals.map(mapCouponOfferToCardData);
}, [apiDeals]);
```

Remove the entire fallback branch that maps `topdimDeals`.

Replace the search haystack preview logic so `deriveCouponPreview` is no longer needed:

```ts
const filteredDeals = useMemo(() => {
  const normalizedSearch = search.trim().toLowerCase();
  return mappedDeals.filter((deal) => {
    const matchesCategory = activeCategory === null || deal.category?.id === activeCategory;
    const haystack = `${deal.title} ${deal.shortDescription || ''} ${deal.offerDescription || ''} ${deal.category?.name || ''} ${deal.merchant?.name || ''}`.toLowerCase();
    const matchesSearch = normalizedSearch.length === 0 || haystack.includes(normalizedSearch);
    return matchesCategory && matchesSearch;
  });
}, [mappedDeals, activeCategory, search]);
```

Keep:

```ts
const topDeals = filteredDeals.filter((d) => d.isHot || (d.totalSold && d.totalSold > 200)).slice(0, 10);
const newDeals = filteredDeals.slice(0, 8);
```

Render existing empty sections only when arrays are empty. If the page currently assumes at least one deal, add simple empty copy in the relevant section:

```tsx
{topDeals.length === 0 ? (
  <div className="catalog-empty">
    <h3>Пока нет горячих предложений</h3>
    <p>Новые купоны появятся после публикации партнёрами</p>
  </div>
) : (
  // existing top deals rendering
)}
```

- [ ] **Step 3: Update `FavoritesPage` imports and mapping**

Remove:

```ts
import { topdimDeals } from '../data/topdim';
import { deriveCouponPreview } from '../utils/couponPreview';
```

Add:

```ts
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
```

Replace mapping:

```ts
const mappedDeals: CouponCardData[] = apiDeals.map(mapCouponOfferToCardData);
```

Remove the fallback branch that maps `topdimDeals`.

- [ ] **Step 4: Run build**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

---

### Task 4: Make Search Use Real Coupon Search

**Files:**
- Modify: `frontend/web-app/src/pages/SearchPage.tsx`

- [ ] **Step 1: Replace demo coupon search**

Remove:

```ts
import { DEMO_COUPONS } from './CouponCatalogPage';
import { topdimCategories } from '../data/topdim';
```

Add:

```ts
import { couponsApi } from '../api/coupons';
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
```

Replace the mock search block with:

```ts
const { data: categories = [] } = useQuery({
  queryKey: ['search-categories'],
  queryFn: () => couponsApi.getCategories(),
  select: (res) => res.data.data,
});

const { data: coupons = [], isLoading: isLoadingCoupons } = useQuery({
  queryKey: ['coupon-search', searchTerm],
  queryFn: () => couponsApi.getCatalog({ search: searchTerm, size: 20 }),
  select: (res) => res.data.data.content,
  enabled: searchTerm.length >= 2,
});
```

Then:

```ts
const filteredCoupons = coupons.map(mapCouponOfferToCardData);
const isLoading = (isLoadingShops || isLoadingCoupons) && searchTerm.length >= 2;
```

Replace popular category rendering:

```tsx
{categories.map((c) => (
  <div key={c.id} className="category-card" onClick={() => applyPopular(c.name)}>
    <span className="category-card__icon">{c.iconUrl || '🔎'}</span>
    <span className="category-card__name">{c.name}</span>
  </div>
))}
```

- [ ] **Step 2: Render mapped coupon cards**

Replace:

```tsx
{filteredCoupons.map((coupon: any) => (
  <CouponCard key={coupon.id} coupon={coupon} layout="card" />
))}
```

With:

```tsx
{filteredCoupons.map((coupon) => (
  <CouponCard key={coupon.id} coupon={coupon} layout="card" />
))}
```

- [ ] **Step 3: Run build**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

---

### Task 5: Make Related Deals Real-Data Only

**Files:**
- Modify: `frontend/web-app/src/pages/CouponDetailPage.tsx`

- [ ] **Step 1: Remove static related deals import**

Remove only the static deals import:

```ts
import { topdimDeals } from '../data/topdim';
```

Keep this import because the page still uses it for the current coupon preview in the title block:

```ts
import { deriveCouponPreview } from '../utils/couponPreview';
```

Add:

```ts
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
```

- [ ] **Step 2: Fetch related deals from catalog**

Immediately after the main coupon query and before any early `return`, add the related query. This keeps React hook order stable while the query stays disabled until the main coupon has a category.

```ts
const { data: relatedCoupons = [] } = useQuery({
  queryKey: ['related-coupons', coupon?.category?.id, coupon?.id],
  queryFn: () => couponsApi.getCatalog({
    categoryId: coupon?.category?.id,
    size: 6,
  }),
  select: (res) => res.data.data.content,
  enabled: !!coupon?.category?.id,
});
```

Replace the static related mapping with:

```ts
const relatedDeals: CouponCardData[] = relatedCoupons
  .filter((deal) => deal.id !== c.id)
  .slice(0, 6)
  .map(mapCouponOfferToCardData);
```

- [ ] **Step 3: Hide related section when empty**

Find the section that renders related cards. Wrap it:

```tsx
{relatedDeals.length > 0 && (
  // existing related deals section
)}
```

- [ ] **Step 4: Run build**

Run:

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

---

### Task 6: Sync Docs With Current Contracts

**Files:**
- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/COUPON_FLOW.md`
- Modify: `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`
- Modify: `services/order-service/README.md`

- [ ] **Step 1: Update redeem contract in API docs**

In `docs/API_CONTRACT.md`, change redeem request section from body merchant ownership to trusted header:

````md
### POST `/api/v1/orders/redeem` — Погашение купона

**Auth:** ✅ Bearer Token

**Headers:**

| Header | Required | Source |
| --- | --- | --- |
| `X-Merchant-Id` | yes | API Gateway / authenticated partner context |

**Request:**
```json
{
  "couponCode": "TDSP-AB12CD",
  "staffName": "Анна"
}
```

`merchantId` must not be accepted from request body for ownership decisions.
````

Update purchased coupon statuses in the same file:

```md
ACTIVE → купон куплен, можно использовать
USED → купон погашен
EXPIRED → срок истёк
CANCELLED → отменён
```

- [ ] **Step 2: Update coupon lifecycle doc**

In `docs/COUPON_FLOW.md`, replace:

```md
Body: { "couponCode": "CP-A1B2C3D4", "merchantId": 5, "staffName": "Иван" }
```

With:

```md
Headers: X-Merchant-Id: 5
Body: { "couponCode": "CP-A1B2C3D4", "staffName": "Иван" }
```

Update status table to use `CANCELLED` instead of `REFUNDED` if the table describes `PurchasedCouponStatus`.

- [ ] **Step 3: Update PRD truthfulness notes**

In `docs/PRODUCT_REQUIREMENTS_DOCUMENT.md`, keep the existing rule that API failures must not be masked by demo data and add a concrete note:

```md
Public coupon storefront production pages must use real API data only. Demo coupon fixtures are allowed for design/dev references, but not as runtime fallback for catalog, home, favorites, search, or related coupon sections.
```

Also update redeem wording to say merchant context comes from trusted gateway headers.

- [ ] **Step 4: Update order-service README**

Replace:

```md
POST /orders/redeem { couponCode, merchantId, staffName }
```

With:

```md
POST /orders/redeem
Header: X-Merchant-Id
Body: { couponCode, staffName }
```

- [ ] **Step 5: Run docs grep check**

Run:

```bash
rg -n --glob '!docs/superpowers/plans/**' "redeem.*merchantId|merchantId.*redeem|REFUNDED|DEMO_COUPONS|Mock coupon search|until backend is ready" docs services/order-service/README.md frontend/web-app/src/pages
```

Expected:

- No docs claim that redeem ownership comes from body `merchantId`.
- No coupon production page imports `DEMO_COUPONS`.
- `REFUNDED` may remain only where it describes order/refund status, not `PurchasedCouponStatus`.

---

### Task 7: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run storefront build**

```bash
cd frontend/web-app
npm run build
```

Expected: PASS.

- [ ] **Step 2: Run storefront lint**

```bash
cd frontend/web-app
npm run lint
```

Expected: PASS. If lint has pre-existing unrelated failures, capture exact output and do not hide it.

- [ ] **Step 3: Run targeted grep checks**

```bash
rg -n "DEMO_COUPONS|DEMO_CATEGORIES|Mock coupon search|until backend is ready|topdimDeals" frontend/web-app/src/pages
```

Expected: no matches in coupon production pages. If `topdimDeals` remains in non-coupon demo-only pages, document why.

```bash
rg -n --glob '!docs/superpowers/plans/**' "Body:.*merchantId|merchantId.*request body|redeem.*merchantId" docs/API_CONTRACT.md docs/COUPON_FLOW.md services/order-service/README.md
```

Expected: no outdated redeem body ownership docs.

- [ ] **Step 4: Manual smoke checklist**

- Catalog with API success shows real coupons.
- Catalog with zero coupons shows empty state, not demo coupons.
- Search for a real coupon uses backend search result.
- Search with no result shows empty state.
- Favorites with local IDs not present in backend shows empty state.
- Coupon detail related deals disappear when backend returns none.
- Redeem docs mention `X-Merchant-Id` header and no body `merchantId` ownership.

---

## Self-Review

- Spec coverage: covers all known coupon storefront demo fallbacks and stale redeem docs.
- Placeholder scan: passed; no forbidden placeholder phrases remain.
- Type consistency: all pages use `mapCouponOfferToCardData(coupon: CouponOffer): CouponCardData`.
- Scope check: no backend behavior changes, no bazaar migration, no Redis work.
