# Step 0: Legacy Audit & Safety Net

## 1. Gateway Routing Findings

**Finding:** `/api/v1/directory/**` has NO explicit route in `api-gateway/application.yml`.

Frontend calls `/api/v1/directory/*` (in `bazaars.ts`) but this path is **not routed** through the gateway.
This means:
- In development, frontend directly hits coupon-service (port 8083) — works
- In production through gateway — these requests **would not be routed** to any service
- The gateway already has bazaar-service routes: `/api/v1/bazaars/**`, `/api/v1/shops/**`

**Conclusion:** Gateway is already prepared for bazaar-service. The `/api/v1/directory/*` frontend code is a dev-only path that needs to be switched to bazaar-service routes.

---

## 2. Tables & Columns to Drop (Phase C/D)

### From `coupon_offers`
| Column | Type | Status |
|---|---|---|
| `short_description` | TEXT | Legacy — replaced by `offer_description` |
| `full_description` | TEXT | Legacy — replaced by `offer_description` |
| `terms` | TEXT | Legacy — merged into `offer_description` |
| `usage_rules` | TEXT | Legacy — merged into `offer_description` |
| `how_to_use` | TEXT | Legacy — merged into `offer_description` |
| `address` | VARCHAR | Legacy — lives in `merchant_locations` |
| `contact_phone` | VARCHAR | Legacy — lives in `merchant_locations` |
| `working_hours` | VARCHAR | Legacy — lives in `merchant_locations` |

### From `merchants`
| Column | Type | Status |
|---|---|---|
| `address` | VARCHAR | Legacy — lives in `merchant_locations` |
| `phone` | VARCHAR | Legacy — lives in `merchant_locations` |
| `working_hours` | VARCHAR | Legacy — lives in `merchant_locations` |

### From `coupon-service` entirely (Step 6)
| Table | Status |
|---|---|
| `bazaars` | Lives in bazaar-service DB |
| `shops` | Lives in bazaar-service DB |

---

## 3. Remaining Legacy Usage — Backend

### Write Flows (dual-write legacy)
| File | Method | Legacy Fields Written |
|---|---|---|
| `CouponOfferService.java:161-174` | `create()` | `shortDescription`, `fullDescription`, `terms`, `usageRules`, `howToUse` |
| `CouponOfferService.java:435-446` | `update()` | `shortDescription`, `fullDescription`, `terms`, `usageRules`, `howToUse` |
| `CouponOfferService.java:757-760` | `createLeadFromBot()` | `fullDescription`, `contactPhone` |
| `PartnerCouponService.java:103-116` | `createCouponOffer()` | `shortDescription`, `fullDescription`, `terms`, `usageRules`, `howToUse` |
| `PartnerCouponService.java:158-170` | `updateMyCoupon()` | `shortDescription`, `fullDescription`, `terms`, `usageRules`, `howToUse` |
| `MerchantService.java:78-79` | `createMerchant()` | `address`, `phone` to merchant entity |
| `MerchantService.java:110-111` | `updateMerchant()` | `address`, `phone`, `workingHours` to merchant entity |

### Read Flows (expose legacy in response)
| File | Method | Legacy Fields Exposed |
|---|---|---|
| `CouponOfferService.java:623-628` | `mapToResponse()` | `shortDescription`, `fullDescription`, `terms`, `usageRules`, `howToUse` |
| `MerchantService.java:292-297` | `mapMerchant()` | `address`, `phone`, `workingHours` |
| `PartnerCouponService.java:187` | `mapToResponse()` | `shortDescription` |

### Elasticsearch (disabled, deferred)
| File | Issue |
|---|---|
| `CouponSearchDocument.java` | Has `shortDescription`, `fullDescription`, `address` — no `offerDescription` |
| `CouponSearchService.java:96` | Searches `shortDescription`, `fullDescription` — not `offerDescription` |

### Directory/Bazaar code in coupon-service
| File | Size |
|---|---|
| `DirectoryController.java` | 85 lines |
| `AdminDirectoryController.java` | ~50 lines |
| `DirectoryService.java` | 344 lines (12.4KB) |
| `Bazaar.java` | entity |
| `Shop.java` | entity |
| `BazaarRepository.java` | repository |
| `ShopRepository.java` | repository |
| `BazaarResponse.java`, `ShopResponse.java`, `CreateBazaarRequest.java`, `CreateShopRequest.java`, `AreaSearchResponse.java` | DTOs |

---

## 4. Remaining Legacy Usage — Frontend

### web-app
| File | Legacy Field | Context |
|---|---|---|
| `CouponCard.tsx:11,123-124` | `shortDescription` | Type def + render |
| `CouponDetailPage.tsx:28,136,180` | `shortDescription` | Mock data + mapping + render |
| `CouponCatalogPage.tsx:33-38,53` | `shortDescription` | Mock data + mapping |
| `HomePage.tsx:64,84,107` | `shortDescription` | Mapping + local search |
| `FavoritesPage.tsx:28,47` | `shortDescription` | Mapping |
| `coupons.ts:20-21` | `shortDescription`, `fullDescription` | Type def |
| `bazaars.ts:55-101` | ALL `/api/v1/directory/*` | API calls to coupon-service |
| `DealCard.tsx:46` | `shortDescription` | Render |

### admin-app
| File | Legacy Field | Context |
|---|---|---|
| `MerchantReviewPage.tsx:41-55` | `shortDescription`, `fullDescription`, `contactPhone` | Type def |
| `MerchantReviewPage.tsx:345-383` | `shortDescription`, `fullDescription`, `address`, `contactPhone`, `workingHours` | Render |
| `CouponFormPage.tsx:42-43` | `shortDescription`, `fullDescription` | Form data mapping |
| `CouponFormPage.tsx:137-150` | `address`, `phone`, `workingHours` | Merchant legacy payload |

---

## 5. Data Audit Queries

```sql
-- Merchants without primary location
SELECT m.id, m.name, m.active
FROM merchants m
LEFT JOIN merchant_locations ml ON ml.merchant_id = m.id AND ml.is_primary = true
WHERE ml.id IS NULL
ORDER BY m.id;

-- Active coupons without offer_description
SELECT co.id, co.title, co.status, co.offer_description
FROM coupon_offers co
WHERE co.status = 'ACTIVE'
  AND (co.offer_description IS NULL OR co.offer_description = '')
ORDER BY co.id;

-- Active coupons still relying on legacy contact fields (have data in legacy but not in merchant_locations)
SELECT co.id, co.title, co.address, co.contact_phone, co.working_hours,
       m.id as merchant_id, m.name as merchant_name
FROM coupon_offers co
JOIN merchants m ON m.id = co.merchant_id
WHERE co.status = 'ACTIVE'
  AND (co.address IS NOT NULL AND co.address != ''
    OR co.contact_phone IS NOT NULL AND co.contact_phone != ''
    OR co.working_hours IS NOT NULL AND co.working_hours != '')
ORDER BY co.id;

-- Merchants with legacy fields but no locations
SELECT m.id, m.name, m.address, m.phone, m.working_hours
FROM merchants m
LEFT JOIN merchant_locations ml ON ml.merchant_id = m.id
WHERE ml.id IS NULL
  AND (m.address IS NOT NULL AND m.address != ''
    OR m.phone IS NOT NULL AND m.phone != ''
    OR m.working_hours IS NOT NULL AND m.working_hours != '')
ORDER BY m.id;

-- Counts of bazaar/shop entities in coupon-service
SELECT 'bazaars' as entity, COUNT(*) as count FROM bazaars
UNION ALL
SELECT 'shops', COUNT(*) FROM shops;
```

---

## 6. Regression QA Checklist

| # | Scenario | What to verify |
|---|---|---|
| 1 | Admin create merchant | Merchant created with primaryLocation |
| 2 | Admin edit merchant | Locations updated correctly |
| 3 | Admin create coupon | `offerDescription` populated, no legacy contact in coupon |
| 4 | Admin edit coupon | Same as above |
| 5 | Partner create coupon | LEAD created, `offerDescription` set |
| 6 | Partner edit coupon | Updated correctly |
| 7 | Bot lead creation | Merchant found by chatId/phone/name, location created |
| 8 | Public catalog list | Cards render with correct preview text |
| 9 | Public catalog search | Finds coupons by `offerDescription` text |
| 10 | Coupon detail page | Merchant block from `primaryLocation`, offer from `offerDescription` |
| 11 | Bazaar list | Renders correctly from bazaar-service |
| 12 | Bazaar detail | Shows shops |
| 13 | Shop detail | Shows info |
| 14 | Bazaar map/search | Area search works |

---

## 7. Rollback Notes

### Step 1 (read-model changes)
- Frontend-only changes, can be reverted by reverting git commits
- No DB changes

### Step 2 (write-flow changes)
- Backend service changes, revertible by git
- No DB migration — legacy columns still exist

### Step 3 (contract deprecation)
- DTO annotation changes, revertible by git
- Frontend type changes, revertible by git

### Step 4 (schema cleanup)
- **DESTRUCTIVE** — Flyway migration drops columns
- Rollback: add columns back with new migration
- Pre-drop backup recommended

### Step 5 (bazaar parity)
- New bazaar-service endpoints + frontend switch
- Rollback: revert frontend to `/api/v1/directory/*`

### Step 6 (remove directory from coupon-service)
- **DESTRUCTIVE** — deletes code files
- Rollback: git revert
- Must verify bazaar-service works first

### Step 7 (contract cleanup)
- DTO field removal
- Rollback: git revert

---

## 8. Test Commands

```bash
# After every step
./gradlew :services:coupon-service:test
./gradlew :services:bazaar-service:test

# If frontend changed
cd frontend/web-app && npm run build
cd frontend/admin-app && npm run build
```
