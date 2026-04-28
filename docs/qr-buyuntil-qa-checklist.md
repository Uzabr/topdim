# Real QR & BuyUntil Visibility Checklist

Дополнение к основному QA тест-плану.

## Real QR Checks

- [ ] Buyer profile shows real QR-code image for ACTIVE purchased coupon.
- [ ] Buyer profile does not show raw qrToken text.
- [ ] QR-код содержит payload в формате `TOPDIM-QR:${qrToken}`.
- [ ] Partner cashier can scan QR with camera and redeem coupon.
- [ ] Invalid QR payload shows "Это не QR-код TopDim".
- [ ] Camera permission failure tells cashier to use PIN fallback.
- [ ] PIN-код fallback по-прежнему работает в partner app.

## BuyUntil Public Visibility Checks

- [ ] ACTIVE coupon with future buyUntil appears in catalog.
- [ ] ACTIVE coupon with past buyUntil does not appear in catalog.
- [ ] ACTIVE coupon with past buyUntil returns 404 on public detail page.
- [ ] ACTIVE coupon with past buyUntil does not appear in top-selling.
- [ ] ACTIVE coupon with null buyUntil appears normally (no deadline).
- [ ] Admin can still open/manage the coupon internally.
- [ ] Search results do not include coupons with past buyUntil.
