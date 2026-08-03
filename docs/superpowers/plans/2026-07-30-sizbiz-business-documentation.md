# SizBiz Business Documentation Implementation Plan

> **For Codex:** Follow the approved design in
> `docs/superpowers/specs/2026-07-30-sizbiz-business-documentation-design.md`.
> This is a documentation-only change.

**Goal:** Deliver mirrored Russian and English product, employee, sales, brand,
and role-flow documentation for the public brand sizbiz, then align active
documentation entry points without modifying code or technical identifiers.

**Architecture:** Add a self-contained `business/` documentation layer under
both language roots. Keep public narrative separate from technical evidence,
and cross-link the two layers. Use PlantUML for maintainable role journeys.

**Tech stack:** Markdown, PlantUML, shell-based validation, Git.

---

## Task 1: Build the Russian business-documentation set

**Create:**

- `docs/ru/business/README.md`
- `docs/ru/business/01-product-vision-and-mission.md`
- `docs/ru/business/02-product-narrative-and-positioning.md`
- `docs/ru/business/03-employee-guide.md`
- `docs/ru/business/04-sales-manager-playbook.md`
- `docs/ru/business/05-customer-one-pager.md`
- `docs/ru/business/06-role-map-and-journeys.md`
- `docs/ru/business/07-guest-and-buyer-flow.md`
- `docs/ru/business/08-merchant-and-staff-flow.md`
- `docs/ru/business/09-operations-role-flows.md`
- `docs/ru/business/10-telegram-flows.md`
- `docs/ru/business/11-brand-and-terminology-guide.md`

**Acceptance:**

- Explain the product in plain language.
- Separate current capabilities, limitations, and future vision.
- Cover every current global role and the cashier staff context.
- Include sales qualification, discovery, demo, objection handling, and
  prohibited promises.

## Task 2: Build the English mirror

**Create:** the same twelve filenames under `docs/en/business/`.

**Acceptance:**

- Preserve the meaning and scope of the Russian source.
- Maintain exact file parity.
- Use the same status and limitation labels.

## Task 3: Add maintainable journey diagrams

**Create under both `docs/ru/diagrams/` and `docs/en/diagrams/`:**

- `business-ecosystem-value.puml`
- `journey-buyer.puml`
- `journey-merchant.puml`
- `journey-cashier-redemption.puml`
- `journey-operations.puml`
- `journey-cross-role-lifecycle.puml`

**Acceptance:**

- Use role-appropriate language.
- Mark demo/partial behavior where relevant.
- Compile with PlantUML.

## Task 4: Align active documentation with the sizbiz brand

**Modify:**

- `README.md`
- `docs/README.md`
- `docs/ru/README.md`
- `docs/en/README.md`
- active product/developer/QA entry points where the platform name appears;
- bilingual overview and C4 diagram titles.

**Rules:**

- Public product references become `sizbiz`.
- Preserve literal technical names including `uz.topdim`, `topdim_*`, and
  `TOPDIM-QR:`.
- Do not rewrite archived files or historical plans.
- Add an explicit brand-versus-engineering-name note.

## Task 5: Validate

Run:

1. RU/EN filename-parity comparison.
2. Markdown relative-link checker.
3. PlantUML `-checkonly` for all bilingual `.puml` files.
4. Brand audit for remaining public `TopDim` references.
5. `git diff --check`.
6. Gitleaks scan for the new bilingual directories when available.

Expected result: all checks pass, and every remaining `topdim` occurrence is a
technical identifier, historical record, file path, or explicitly explained
legacy name.

## Task 6: Deliver

- Commit the documentation in meaningful commits.
- Push `docs/codex`.
- Merge through a pull request into the repository default branch.
- Confirm the resulting remote branch and merge state independently.
