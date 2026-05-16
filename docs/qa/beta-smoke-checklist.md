# Beta Smoke Checklist

> Этот чеклист предназначен для быстрой проверки готовности системы к beta-тестированию с реальными пользователями.

---

## Роли

| Роль | Описание | Где логинится |
|------|----------|---------------|
| USER | Покупатель с email и phone в профиле | `frontend/web-app` → `/login` |
| PARTNER OWNER | Владелец бизнеса/мерчанта | `frontend/partner` → `/login` |
| PARTNER CASHIER | Кассир как `staff.role=CASHIER`, login-user имеет JWT роль `PARTNER` и привязан к филиалу | `frontend/partner` → `/login` (email из staff setup) |
| MODERATOR | Модератор TopDim | `frontend/admin-app` → `/login` |
| ADMIN | Администратор TopDim | `frontend/admin-app` → `/login` |

---

## Buyer Smoke
- [ ] Login works.
- [ ] Catalog shows only buyable active coupons (no expired `buyUntil`).
- [ ] Coupon detail opens.
- [ ] Coupon option can be added to cart.
- [ ] Checkout creates order.
- [ ] Demo payment completes.
- [ ] Purchased coupon appears in profile.
- [ ] PIN is visible (format `CP-XXXX1234`).
- [ ] QR is rendered as QR image, not raw token.
- [ ] QR payload format: `TOPDIM-QR:${qrToken}`.

---

## Partner Redemption Smoke
- [ ] Owner can open dashboard.
- [ ] Cashier opens directly to redemption page.
- [ ] PIN redemption succeeds for own merchant coupon.
   - Success message: "Купон погашен по PIN."
- [ ] QR redemption succeeds for own merchant coupon.
   - Success message: "Купон погашен по QR."
- [ ] Reusing same coupon returns business error.
   - Error: "Этот купон уже был использован."
- [ ] Wrong merchant coupon returns business error.
   - Error: "Этот купон относится к другому партнёру."
- [ ] Expired coupon returns business error and moves to expired.
   - Error: "Срок действия купона истёк."

---

## Admin/Moderator Smoke
- [ ] Partner application can be approved.
- [ ] Partner coupon request can be taken to work (LEAD → DRAFT).
- [ ] Coupon can be sent to merchant approval (DRAFT → WAITING_FOR_MERCHANT).
- [ ] Merchant can approve coupon (WAITING_FOR_MERCHANT → ACTIVE).
- [ ] Active coupon appears publicly before buyUntil.
- [ ] Active coupon disappears publicly after buyUntil.
- [ ] Admin can look up purchased coupon by coupon code.
- [ ] Admin lookup does NOT expose qrToken.

---

## Cashier Creation Smoke
- [ ] Creating cashier without email → error.
- [ ] Creating cashier without password → error.
- [ ] Creating cashier without branch → error.
- [ ] Creating cashier with password < 6 chars → error.
- [ ] Creating cashier with valid data → success, cashier can login.

---

## Negative Tests
- [ ] Guest cannot access `/profile` (redirect to `/login`).
- [ ] Sold-out coupon option cannot be purchased.
- [ ] Active coupon with expired `buyUntil` hidden from catalog.
- [ ] Partner cannot edit `ACTIVE` coupon directly.
- [ ] Admins cannot redeem coupons through admin-app (no redemption button).

---

## Release Blockers

| Severity | Description | Status |
|----------|-------------|--------|
| **P0** | Buyer cannot purchase coupon | ❌ Blocker |
| **P0** | Payment does not generate purchased coupons | ❌ Blocker |
| **P0** | PIN/QR redemption does not work | ❌ Blocker |
| **P1** | Cashier cannot login after creation | ❌ Critical |
| **P1** | Used coupon can be redeemed again | ❌ Critical |
| **P1** | Wrong merchant can redeem coupon | ❌ Critical |
| **P2** | Admin cannot look up purchased coupon | ⚠️ Major |
| **P3** | Notification not sent after purchase | ℹ️ Graceful degradation |

---
*Документ создан: 2026-04-29*
