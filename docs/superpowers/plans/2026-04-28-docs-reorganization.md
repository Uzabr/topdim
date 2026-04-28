# Docs Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize `docs` into a clear role-based documentation hub for product, frontend, backend, and QA while moving completed plans/prompts/status files into an archive.

**Architecture:** Keep current source-of-truth documentation visible at the top level through `docs/README.md` and role folders. Preserve historical content by moving old plans and prompts into `docs/archive` instead of deleting it.

**Tech Stack:** Markdown documentation, existing repository layout, local `.agent/skills` guidance for frontend and services.

---

### Task 1: Create target folder structure

**Files:**
- Create directories under `docs/product`, `docs/frontend`, `docs/backend`, `docs/qa`, and `docs/archive`.

- [ ] Create these folders:

```bash
mkdir -p docs/product/flows docs/frontend docs/backend docs/qa docs/archive/implemented docs/archive/ai-prompts docs/archive/legacy-plans docs/archive/references docs/archive/superpowers/plans docs/archive/superpowers/specs
```

### Task 2: Move current documentation into role folders

**Files:**
- Move product docs to `docs/product`.
- Move backend docs to `docs/backend`.
- Move frontend docs to `docs/frontend`.
- Move QA docs to `docs/qa`.

- [ ] Move files without changing content:

```bash
mv docs/PRODUCT_REQUIREMENTS_DOCUMENT.md docs/product/prd.md
mv docs/ROLES.md docs/product/roles.md
mv docs/COUPON_FLOW.md docs/product/flows/coupon-flow.md
mv docs/COUPON_CREATION_FLOW.md docs/product/flows/coupon-creation-flow.md
mv docs/API_CONTRACT.md docs/backend/api-contract.md
mv docs/BACKEND.md docs/backend/services-overview.md
mv docs/DATABASE.md docs/backend/database.md
mv docs/FRONTEND.md docs/frontend/web-app.md
mv docs/TESTING.md docs/qa/test-strategy.md
mv docs/QA_TEST_PLAN.md docs/qa/manual-test-plan.md
mv docs/purchase-flow-qa-checklist.md docs/qa/purchase-flow-checklist.md
mv docs/purchase-flow-walkthrough.md docs/qa/purchase-flow-walkthrough.md
```

### Task 3: Archive implemented/old planning files

**Files:**
- Move completed plans, backlogs, prompts, and status documents to `docs/archive`.

- [ ] Move AI prompts:

```bash
mv docs/coupon-detail-page-restructure-ai-prompt.md docs/archive/ai-prompts/
mv docs/coupon-merchant-ai-execution-brief.md docs/archive/ai-prompts/
```

- [ ] Move implemented plans/backlogs/status:

```bash
mv docs/ADMIN_KANBAN_ANSWERS.md docs/archive/implemented/
mv docs/PROGRESS.md docs/archive/implemented/
mv docs/TASKS.md docs/archive/implemented/
mv docs/coopon-merchant-db-refactor-plan.md docs/archive/implemented/
mv docs/coupon-merchant-full-separation-plan.md docs/archive/implemented/
mv docs/coupon-merchant-phase1-completion-backlog.md docs/archive/implemented/
mv docs/demo-payment-mode-backlog.md docs/archive/implemented/
mv docs/demo-payment-mode-implementation-plan.md docs/archive/implemented/
mv docs/dev-demo-seed-data-plan.md docs/archive/implemented/
mv docs/dev-demo-seed.md docs/archive/implemented/
mv docs/execution-status.md docs/archive/implemented/
mv docs/implementation_plan.md docs/archive/implemented/
mv docs/open-questions.md docs/archive/implemented/
mv docs/step0-legacy-audit.md docs/archive/implemented/
mv docs/user-purchase-flow-backlog.md docs/archive/implemented/
mv docs/user-purchase-flow-implementation.md docs/archive/implemented/
mv docs/user-purchase-flow-status.md docs/archive/implemented/
```

- [ ] Move reference/planning artifacts:

```bash
mv docs/topdim_merchant_dashboard_structure.md docs/archive/references/
mv docs/plans/task.md docs/archive/legacy-plans/
mv docs/plans/2026-04-28-update-documentation-plan.md docs/archive/legacy-plans/
```

- [ ] Move old superpowers plans/specs except this active reorganization plan:

```bash
mv docs/superpowers/specs/*.md docs/archive/superpowers/specs/
find docs/superpowers/plans -maxdepth 1 -type f -name '*.md' ! -name '2026-04-28-docs-reorganization.md' -exec mv {} docs/archive/superpowers/plans/ \;
```

### Task 4: Write clean entrypoint documentation

**Files:**
- Create/modify `docs/README.md`
- Create `docs/product/README.md`
- Create `docs/frontend/README.md`
- Create `docs/backend/README.md`
- Create `docs/qa/README.md`
- Create `docs/archive/README.md`

- [ ] Write docs that explain:
  - which docs are current source of truth;
  - where frontend/backend/QA developers should start;
  - which local skills developers should use before changing code;
  - how to treat archived files.

### Task 5: Verify final docs structure

**Files:**
- All moved and newly created docs.

- [ ] Run:

```bash
find docs -maxdepth 3 -type f | sort
```

- [ ] Run:

```bash
git status --short
```

- [ ] Run:

```bash
rg -n "TODO|TBD|file://|PRODUCT_REQUIREMENTS_DOCUMENT.md|FRONTEND.md|BACKEND.md|QA_TEST_PLAN.md|TESTING.md" docs/README.md docs/product docs/frontend docs/backend docs/qa docs/archive/README.md
```

Expected: no unresolved placeholders or old active-doc links in the new current documentation. References inside archived historical docs are acceptable.
