# Employee product guide

## What sizbiz is

sizbiz is the public brand of the coupon and local-offer platform. A buyer finds
an offer at `sizbiz.uz`, completes a purchase, and receives a coupon. A partner
or cashier verifies it by QR/PIN. The sizbiz team helps prepare the offer and
maintains process quality.

`Topdim` and `topdim` may appear in code, database names, and internal
resources. They are historical engineering names, not customer-facing names.

## Participants

- **Guest:** explores public content.
- **Buyer:** purchases and uses coupons.
- **Partner owner:** manages offers, staff, and results.
- **Cashier:** redeems coupons for an authorized business.
- **Moderator:** prepares offers and moderates user content.
- **Administrator:** manages operational processes and exceptions.
- **Super administrator:** manages access and audit.

See the [role map](06-role-map-and-journeys.md).

## Core cycle

```text
Partner proposes an offer
→ sizbiz team reviews and prepares it
→ partner approves it
→ buyer discovers and purchases it
→ cashier redeems it
→ both sides see the result and can provide feedback
```

## Why moderation matters

Publication affects money, buyer expectations, and partner reputation.
Moderation checks readiness and status. It does not replace legal review and
does not authorize staff to invent partner terms.

## Current limitations

- payment operates in demo mode;
- email/SMS may not use a real provider;
- part of the bazaar directory is not connected to production routing and CD;
- some admin screens and future financial capabilities are incomplete.

A future feature must not be promised with a date unless an approved plan says
so.

## Escalation map

| Question | Responsible area |
|---|---|
| Offer terms, content, card | Moderation / partner operations |
| Staff access or role | Administrator / identity operations |
| Redemption, order, refund | Support / order operations |
| Error or outage | Engineering |
| Commercial terms or contract | Sales lead / business owner |
| Personal data or legal matter | Authorized leader / legal counsel |

## Communication rules

- use the name **sizbiz**;
- never expose tokens, keys, or internal technical data;
- do not promise guaranteed revenue, sales, or feature dates;
- separate verified facts from assumptions;
- record partner agreements;
- escalate disputes instead of changing data for convenience.

## New-employee baseline

An employee is ready when they can:

1. explain the product in 30 seconds;
2. name the main roles;
3. describe the journey from offer request to redemption;
4. distinguish an offer from a purchased coupon;
5. state current limitations;
6. use the brand correctly;
7. route a question or incident to the right owner.
