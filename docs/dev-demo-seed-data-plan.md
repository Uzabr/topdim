# Dev Demo Seed Data Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Use project skills from `.agent/skills/` and service skills from `services/.agent/skills/`.

**Goal:** Add a safe local/dev demo seed mechanism that creates realistic merchants, staff, branches, and coupons for manual QA.

**Architecture:** Do not add demo data as normal production Flyway migrations. Implement an explicit local/dev-only seed/reset mechanism that can be run on demand and is idempotent. Demo data must look like real business data and respect current domain rules: merchants need valid locations, cashiers need independent login users, cashiers are bound to branches, and active coupons must be publication-ready.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Flyway, PostgreSQL, Gradle, existing microservices (`identity-service`, `coupon-service`).

---

## Required Skills

- [ ] Read `.agent/skills/using-superpowers`.
- [ ] Use `.agent/skills/test-driven-development` for behavior that can be tested.
- [ ] Use `.agent/skills/systematic-debugging` for failing tests or seed execution issues.
- [ ] Use `.agent/skills/verification-before-completion` before reporting completion.
- [ ] Use `services/.agent/skills/database-schema-designer` before touching database structure.
- [ ] Use `services/.agent/skills/spring-boot-crud-patterns` and `services/.agent/skills/spring-boot-test-patterns` for service changes.
- [ ] Use `services/.agent/skills/sql-optimization-patterns` if implementing SQL scripts with bulk inserts.

## Non-Negotiable Rules

- [ ] Do not seed production automatically.
- [ ] Do not add 50 merchants and 500 coupons to standard production migrations.
- [ ] Seed must run only when explicitly enabled by local/dev/demo profile, command, or script.
- [ ] Seed must be idempotent: repeated runs must not create duplicates.
- [ ] Reset must delete only demo data created by this seed, using stable demo markers such as email domain `demo.topdim.uz`, name prefix `Demo`, or metadata comments.
- [ ] Do not wipe the whole database.
- [ ] Do not create cashiers without independent login users.
- [ ] Do not create cashiers without `merchantId` and `merchantLocationId`.
- [ ] Do not create active coupons for merchants without active primary location.
- [ ] Do not let demo seed bypass business constraints silently.

## Recommended Implementation Approach

Prefer one of these two approaches after inspecting the current codebase:

- [ ] **Option A, preferred:** Add explicit scripts under `scripts/dev/` that call service APIs or run service-specific seed commands.
- [ ] **Option B:** Add Spring `CommandLineRunner` seeders guarded by a profile such as `dev-seed` and config flag such as `topdim.demo.seed.enabled=true`.

Do not choose a hidden always-on seeder. If adding profile-based seeders, they must not run under normal `dev`, `test`, or production profiles unless the explicit seed flag is enabled.

## Data Volume

- [ ] 50 merchant owner users.
- [ ] 50 merchants.
- [ ] At least 50 active merchant locations.
- [ ] Some merchants should have 2-3 branches.
- [ ] 50 manager users or staff records if manager is supported by current implementation.
- [ ] 100 cashier users, two per merchant.
- [ ] 100 staff rows for cashiers, each linked to owner, merchant, and branch.
- [ ] 500 coupon offers, 10 per merchant.
- [ ] At least 350 active coupons, seven per merchant.
- [ ] Remaining coupons can be `DRAFT`, `WAITING_FOR_MERCHANT`, or `ARCHIVED`.

## Demo Credentials

Use stable credentials so manual QA is easy:

- [ ] Admin if needed: `admin@topdim.uz` / existing password from current seeder.
- [ ] Buyers: `buyer001@demo.topdim.uz` through `buyer005@demo.topdim.uz`.
- [ ] Owners: `owner001@demo.topdim.uz` through `owner050@demo.topdim.uz`.
- [ ] Managers: `manager001@demo.topdim.uz` through `manager050@demo.topdim.uz` if implemented.
- [ ] Cashiers: `cashier001a@demo.topdim.uz`, `cashier001b@demo.topdim.uz` through merchant 50.
- [ ] Password for all demo-created users: `Demo123!`.
- [ ] All demo-created partner/cashier login users must have global auth role `PARTNER`.

## Identity-Service Data

Create or seed these in `identity-service`:

- [ ] Buyer users with role `USER`.
- [ ] Owner users with role `PARTNER`.
- [ ] Cashier users with role `PARTNER`.
- [ ] Manager users with role `PARTNER` only if manager access is already supported.
- [ ] Staff rows:
  - `userId` = owner user id.
  - `loginUserId` = cashier or manager user id.
  - `merchantId` = matching merchant id from coupon-service.
  - `merchantLocationId` = required for `CASHIER`.
  - `role` = `CASHIER` or `MANAGER`.
  - `name`, `phone`, `active` filled.

## Coupon-Service Data

Create or seed these in `coupon-service`:

- [ ] Categories if missing:
  - `restaurants`
  - `coffee`
  - `beauty`
  - `spa`
  - `fitness`
  - `entertainment`
  - `education`
  - `services`
  - `shops`
- [ ] Merchants:
  - `name`
  - `description`
  - `logoUrl`
  - `coverUrl`
  - `email`
  - `website`
  - `contactPerson`
  - `active=true`
  - `userId` linked to owner user id.
  - `telegramChatId` filled with stable demo value or omitted if optional.
- [ ] Merchant locations:
  - `title`
  - `address`
  - `phone`
  - `workingHours`
  - `latitude`
  - `longitude`
  - one `primary=true` active location per merchant.
  - all cashier-linked locations must be active.
- [ ] Coupon offers:
  - 10 per merchant.
  - `title`
  - `offerDescription`
  - `merchant`
  - `category`
  - `oldPrice`
  - `fromPrice`
  - `discountPercent`
  - `coverImageUrl`
  - `buyUntil` in future for active coupons.
  - `useUntil` in future for active coupons.
  - `status`
  - `giftAvailable`
  - counters initialized consistently.
- [ ] Coupon options:
  - 1-3 options per offer.
  - `title`
  - `regularPrice`
  - `couponPrice`
  - `quantityLimit`
  - `quantitySold`
  - `status=ACTIVE` for active offers.
- [ ] Coupon images:
  - 1-3 images per offer.
  - use stable placeholder image URLs, not local absolute paths.

## Cross-Service Mapping

Because identity and coupon data live in different service databases, define a reliable mapping strategy:

- [ ] Owners must be created first in identity-service.
- [ ] Merchants must be created with `Merchant.userId = ownerUser.id`.
- [ ] Staff rows must be created after merchants and locations exist.
- [ ] Cashier staff must use `merchantId` and `merchantLocationId` from coupon-service.
- [ ] Document how the seed discovers ids across service databases or APIs.

## Reset Behavior

Add a reset command or script:

- [ ] It deletes demo staff where user emails end with `@demo.topdim.uz`.
- [ ] It deletes demo users where email ends with `@demo.topdim.uz`.
- [ ] It deletes demo coupon images, options, offers, locations, merchants created by the seed.
- [ ] It does not delete `admin@topdim.uz` unless explicitly documented and safe.
- [ ] It does not delete non-demo data.
- [ ] It is safe to run multiple times.

## Verification

After implementation, verify with commands and record exact output:

- [ ] `./gradlew :services:identity-service:test`
- [ ] `./gradlew :services:coupon-service:test`
- [ ] Run the seed command on local dev DB.
- [ ] Run the seed command twice and prove counts do not duplicate.
- [ ] Run count checks:
  - 50 demo merchants.
  - 500 demo coupon offers.
  - At least 350 active demo coupon offers.
  - At least 50 active merchant locations.
  - 50 owner users.
  - 100 cashier users.
  - 100 cashier staff rows with non-null `merchantId` and `merchantLocationId`.
- [ ] Verify at least one owner can log into partner app.
- [ ] Verify at least one cashier can log into partner app and has fixed branch context.
- [ ] Verify public coupon catalog can show active demo coupons.

## Documentation

Create or update `docs/dev-demo-seed.md` with:

- [ ] What the seed creates.
- [ ] How to start required local services.
- [ ] How to run migrations.
- [ ] How to run seed.
- [ ] How to reset only demo data.
- [ ] Demo login list and password.
- [ ] Manual QA scenarios supported by the seed:
  - buyer browsing coupons;
  - buyer purchase flow;
  - owner dashboard;
  - cashier redemption by PIN/QR;
  - wrong merchant/cashier negative checks.

## Final Report Required From AI

- [ ] Files created/changed.
- [ ] Chosen seed approach and why.
- [ ] Exact seed/reset commands.
- [ ] Exact verification commands and results.
- [ ] Demo counts after seed.
- [ ] Demo credentials summary.
- [ ] Any known limitations or follow-up tasks.
