# Merchant and partner-staff flow

## 1. Business onboarding

1. A representative submits a partnership application.
2. An administrator reviews it.
3. Approval creates or links the partner user and merchant.
4. The owner gains partner-cabinet access.
5. The business and at least one publication-ready primary location are
   completed.

Partner approval does not automatically publish every campaign.

## 2. Offer creation

1. Partner submits a lightweight request — `LEAD`.
2. Moderator takes it — `DRAFT`.
3. Team prepares copy, images, options, and restrictions.
4. Moderator sends it for approval — `WAITING_FOR_MERCHANT`.
5. Owner either approves → `ACTIVE`, or requests changes →
   `REVISION_REQUESTED`.
6. The preparation cycle repeats when needed.

Publication requires a ready merchant and active primary location. Active and
sold-out offers cannot be freely edited as drafts.

## 3. Staff management

The owner views access context and adds/removes staff. A cashier receives the
merchant context and, when configured, a location constraint.

Never use another business's account or grant platform-admin privileges to a
cashier.

## 4. Cashier redemption

1. Cashier signs into the partner app.
2. Scans QR or enters PIN/code.
3. System finds the purchased coupon.
4. Verifies `ACTIVE`.
5. Verifies merchant and allowed location.
6. Atomically changes it to `USED`.
7. Records who, where, when, and how.
8. Shows the result; repeat use is rejected.

A screenshot is not proof of redemption without a successful system response.

## 5. Owner result

The partner can view supported sales, redemption, and revenue aggregates and
redemption history. These are platform operating statistics, not accounting or
tax reports.

## Conflicts

| Situation | Correct action |
|---|---|
| Another merchant's coupon | Do not redeem; explain and escalate |
| Already used | Do not repeat; check history |
| Wrong location | Do not bypass; contact the owner |
| Unclear terms | Do not invent rules; check the published card |
| Change active campaign | Use allowed status process |
| Refund demanded at checkout | Route into the supported refund flow |

Diagrams:

- [journey-merchant.puml](../diagrams/journey-merchant.puml)
- [journey-cashier-redemption.puml](../diagrams/journey-cashier-redemption.puml)
