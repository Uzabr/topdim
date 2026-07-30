# Guest and buyer flow

## Goal

Move a person from interest to successful offer use without hiding price,
validity, or restrictions.

## Guest

1. Opens the public catalog.
2. Searches or filters offers.
3. Reads the card, public reviews, and questions.
4. Registers or logs in for a personal action.
5. Uses password or supported Telegram Login.

## Authenticated buyer

1. Manages profile and favorites.
2. Chooses an offer option and adds it to the cart.
3. Checks quantity and total.
4. Creates an order with contact information.
5. Completes the current demo payment.
6. Receives a purchased coupon with a code and QR token.
7. Opens it in the profile and presents it to staff.
8. After successful validation, it becomes `USED`.
9. Leaves a review when eligible.
10. Creates a complaint or refund request when needed.

## Important states

| Object | Key employee-facing states |
|---|---|
| Order | payment pending, paid, cancelled/refunded as applicable |
| Purchased coupon | `ACTIVE`, `USED`, `EXPIRED`, `REFUND_PENDING`, `REFUNDED`, `CANCELLED` |
| Refund | `PENDING`, approved/processing, `REFUNDED`, or `REJECTED` |

## Negative scenarios

- unavailable or sold-out offer → purchase must stop;
- unauthenticated user → login required;
- changed option or price → checkout uses snapshots and server validation;
- unconfirmed payment → no usable purchased coupon;
- used, expired, or refund-pending coupon → no second redemption;
- another user's coupon → no personal data disclosure;
- ineligible review → no publication;
- duplicate pending complaint → constrained.

## Trust points

Before purchase, users need to understand what they receive, the price and
benefit, where and when it is valid, restrictions, and how to get help.

## Current limitations

- payment-provider production integration is not confirmed;
- email/SMS may operate as stubs;
- expiry is partly lazy rather than a confirmed dedicated scheduler;
- part of the bazaar directory is not fully available in production.

Diagram: [journey-buyer.puml](../diagrams/journey-buyer.puml).
