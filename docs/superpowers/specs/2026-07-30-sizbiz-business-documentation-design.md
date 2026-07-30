# SizBiz Business Documentation and Brand Alignment Design

Date: 2026-07-30
Status: approved through the user's instruction to complete, push, and merge

## Goal

Create a complete bilingual business-documentation layer for the public product
brand **sizbiz**. The material must help sales managers, non-technical
employees, buyers, merchants, partner staff, moderators, administrators, and
other stakeholders understand why the product exists, what value it creates,
and how each role uses it.

## Brand decision

- The public product name is **sizbiz**, written in lowercase in the wordmark.
- The canonical public domain is `sizbiz.uz`.
- The current wordmark is black `sizbiz` text on a yellow pill-shaped
  background.
- `Topdim`/`topdim` is a historical internal engineering name, not the public
  brand.
- Public, product, employee, and sales documentation uses **sizbiz**.
- Technical identifiers are preserved exactly when they describe the
  implementation: `uz.topdim`, `topdim_order`, `topdim_identity`,
  `TOPDIM-QR:`, repository paths, image names, service names, commands, and
  infrastructure resources.
- Historical plans and archived evidence are not rewritten because doing so
  would falsify the record.

## Information architecture

Create mirrored directories:

```text
docs/ru/business/
docs/en/business/
```

Each language contains:

1. Business documentation index.
2. Product vision, mission, motto, values, and social benefit.
3. Product narrative, positioning, audiences, and value propositions.
4. Plain-language employee guide.
5. Internal sales-manager playbook.
6. Short customer-facing product one-pager.
7. Role map and journey overview.
8. Guest and buyer journey.
9. Merchant owner and partner-staff journey.
10. Moderator, administrator, and super-administrator journeys.
11. Telegram channel journeys.
12. Brand and terminology guide.

Create mirrored PlantUML sources for the main non-technical journeys:

- ecosystem value;
- guest and buyer journey;
- merchant journey;
- cashier redemption;
- operations and moderation;
- cross-role lifecycle.

## Content rules

- Describe only behavior supported by the current source code and active
  documentation.
- Clearly label demo, partial, unavailable, and planned capabilities.
- Do not present demo payments as a production payment-provider integration.
- Do not present the bazaar directory as production-complete while its gateway
  route and CD deployment remain incomplete.
- Treat cashier as partner staff with `PARTNER` authentication context, not as
  a separate global JWT role.
- Avoid invented pricing, commission, financial-return, legal, market-size, or
  social-impact numbers.
- Phrase the mission as intended impact, not as an already proven measured
  outcome.
- Keep the Russian and English file sets structurally identical.

## Product narrative direction

Working purpose:

> Make useful local offers easier to discover, buy, and use while helping
> businesses build a transparent, measurable relationship with their
> customers.

Working Russian motto:

> **Выгода людям. Рост бизнесу. Польза рядом.**

Working English motto:

> **Value for people. Growth for business. Benefit nearby.**

The narrative is Uzbekistan-first and leaves future geographic scaling open
without claiming that expansion has already happened.

## Existing-document alignment

Update the current documentation entry points, product overview, active product
documents, and diagram titles to use **sizbiz** as the platform name. Add a
brand note explaining the retained internal name.

Do not mechanically replace technical identifiers or historical content.

## Validation

- Russian and English business file parity.
- No broken relative Markdown links.
- PlantUML syntax validation for every new and existing bilingual diagram.
- Search-based brand audit separating public mentions from legitimate internal
  identifiers.
- `git diff --check`.
- Secret scan of the new documentation.

## Non-goals

- No application-code, configuration, database, package, route, or deployment
  changes.
- No logo redesign.
- No fabricated commercial terms or legal guarantees.
- No deletion or rewrite of historical records.
