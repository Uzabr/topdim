# sizbiz brand and terminology guide

## Name

- Public product name and brand: **sizbiz**.
- Lowercase `sizbiz` is valid at the beginning of a sentence and is part of the
  wordmark.
- Domain: `sizbiz.uz`.
- Do not use publicly: `TopDim`, `Topdim`, or `Top Dim`.

## Logo

The wordmark confirmed by the current interface:

- text: `sizbiz`;
- black text;
- yellow background;
- rounded pill shape;
- preserve clear space;
- do not stretch, recolor, shadow, or respell it without approval.

The repository implements the logo as a text UI component, not a separate
approved SVG brand master. Print and external materials require an exported
source approved by the brand owner.

## Internal engineering name

`Topdim`/`topdim` is a historical name in code and infrastructure. Preserve:

- Java packages `uz.topdim.*`;
- databases such as `topdim_identity` and `topdim_order`;
- Docker/Swarm/GHCR resources `topdim_*`;
- QR payload `TOPDIM-QR:${qrToken}`;
- existing paths, classes, test data, and commands.

These identifiers require a separate technical migration and must not be
renamed through documentation edits.

## Known implementation mismatch

As of 2026-07-30, some partner/admin UI, Telegram bot, email/SMS templates, and
backend messages still display `TopDim`. This documentation task intentionally
does not modify code. Complete public rebranding needs a separate inventory of
strings, translations, page metadata, and regression tests.

## Preferred language

| Avoid | Use |
|---|---|
| “TopDim discount service” | “sizbiz local-offer platform” |
| “our seller” | “partner” or “merchant” |
| “the campaign guarantees sales” | “the offer adds an acquisition channel” |
| “discount without conditions” | state validity, location, and restrictions |
| “real online payment is connected” | “payment currently operates in demo mode” |

## Terms

| Term | Plain explanation |
|---|---|
| Coupon offer | Campaign terms before purchase |
| Purchased coupon | A buyer's personal right to use an offer |
| Merchant | Business that fulfills the offer |
| Partner | User/organization managing the merchant |
| Redemption | Confirmation that a coupon was used |
| QR/PIN | Ways to locate and verify a purchased coupon |
| Lead | Initial request, not a published offer |
| Moderation | Review and preparation of content or a case |
| Staff context | Employee access limited to a business and location |

## Source of truth

This document governs the public name in documentation. For actual behavior,
code, configuration, and [technical documentation](../README.md) take
precedence.
