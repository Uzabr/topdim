# 02. Business context

## Actors

| Actor | Confirmed responsibilities |
|---|---|
| Guest | Browse catalog/reviews/Q&A, create guest session, register |
| Buyer (`USER`) | Cart, order, payment, purchased coupons, refund, complaint, review, question, profile |
| Merchant owner (`PARTNER`) | Coupon requests/approval, staff, statistics, redemption |
| Cashier | Redeem within merchant/location staff context; JWT role remains `PARTNER` |
| Moderator | Coupon, review, question, and complaint moderation |
| Administrator | Merchants, users, refunds, catalogs, and moderator operations |
| Super administrator | Staff, roles, blocking, audit log, and all admin capabilities |
| Telegram | Buyer login and concierge/approval webhook integration |

## Main processes

Merchant onboarding leads to an identity application, admin approval, user/merchant linkage, a coupon `LEAD`, moderator preparation (`DRAFT`), merchant review (`WAITING_FOR_MERCHANT`), and either `ACTIVE` or `REVISION_REQUESTED`.

The purchase path creates an order from a cart snapshot, publishes `OrderCreatedEvent`, creates/completes a payment, consumes `PaymentCompletedEvent`, and materializes QR/PIN purchased coupons. Staff redemption validates merchant/location access and marks the coupon `USED`; coupon-service receives a redemption event for its ledger and statistics.

Refunds are scoped to purchased coupons and follow admin approve/reject/complete decisions. Complaints and reviews have moderation flows; review eligibility is queried from order-service. Questions are published only after moderation.

## Confirmed rules

- one payment per order is enforced by a unique index;
- sale and redemption ledgers provide targeted idempotency;
- duplicate pending complaints for the same coupon are blocked by a partial unique index;
- refresh tokens are SHA-256 hashed and security version invalidates sessions after sensitive account changes;
- public catalog reads are restricted to publication-eligible coupons.

## Assumptions

Assumption: monetary settlement/refund is performed outside this repository by a future provider integration.
Evidence: `payment.mode=demo|provider`; no production provider client exists.
Confidence: High.

Assumption: Uzbekistan is the target jurisdiction, but PCI, tax, privacy, and receipt requirements are not formalized.
Evidence: `.uz` domains, phone formats, and product docs; no compliance policy.
Confidence: Medium.

Evidence:
- `services/coupon-service/src/main/java/uz/topdim/coupon/service/PartnerCouponService.java`
- `services/order-service/src/main/java/uz/topdim/order/service/OrderService.java`
- `services/identity-service/src/main/java/uz/topdim/identity/service/PartnerApplicationService.java`
- `services/*/src/main/resources/db/migration/`
