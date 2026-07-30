# Role map and user journeys

## Product roles

| Plain-language role | Technical context | Primary goal |
|---|---|---|
| Guest | no JWT / `GUEST` session | Explore offers and begin registration |
| Buyer | `USER` | Buy, use, and receive support for a coupon |
| Partner owner | `PARTNER`, owner context | Manage offers, staff, and results |
| Cashier | `PARTNER`, staff `CASHIER` | Redeem a coupon quickly and safely |
| Moderator | `MODERATOR` | Prepare content and handle user submissions |
| Administrator | `ADMIN` | Manage operational processes |
| Super administrator | `SUPER_ADMIN` | Manage roles, access, and audit |
| Telegram user | scenario-dependent | Log in or use concierge/approval |

A cashier is not a separate global JWT role. Merchant and location membership
define their access.

## End-to-end lifecycle

```text
Partnership application
→ merchant creation/linking
→ offer request
→ moderator preparation
→ partner approval
→ publication
→ buyer discovery
→ order and demo payment
→ purchased coupon
→ cashier redemption
→ statistics / review / support / refund
```

## Responsibility

- partners ensure that terms are truthful and fulfilled;
- moderators prepare content and maintain valid statuses;
- buyers follow published usage conditions;
- cashiers redeem only within their merchant/location context;
- administrators make authorized operational decisions;
- super administrators control privileged access;
- the system stores state, enforces core constraints, and audits critical admin
  actions.

## Detailed journeys

- [Guest and buyer](07-guest-and-buyer-flow.md)
- [Merchant and staff](08-merchant-and-staff-flow.md)
- [Moderator and administrators](09-operations-role-flows.md)
- [Telegram](10-telegram-flows.md)

Lifecycle diagram:
[journey-cross-role-lifecycle.puml](../diagrams/journey-cross-role-lifecycle.puml).
