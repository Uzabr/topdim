---
description: Create a detailed implementation plan before touching code. Bite-sized tasks with exact file paths, test commands, and expected output.
---

# Writing Plans

$ARGUMENTS

**Announce:** "I'm using writing-plans to create the implementation plan."

**Save to:** `docs/plans/YYYY-MM-DD-<feature-name>.md`

## Plan Header (Required)

```markdown
# [Feature Name] Implementation Plan

**Goal:** [One sentence]
**Architecture:** [2-3 sentences]
**Tech Stack:** [Key technologies]
```

## Task Structure

Each task = one TDD cycle (2-5 minutes per step):

```markdown
### Task N: [Name]

**Files:**
- Create: `exact/path/to/file.java`
- Modify: `exact/path/to/existing.java`
- Test: `src/test/path/to/Test.java`

**Step 1: Write the failing test**
[Exact test code]

**Step 2: Run test — verify it fails**
Run: `./mvnw test -pl services/... -Dtest=ClassName#methodName`
Expected: FAIL with "method not found" or similar

**Step 3: Implement minimal code**
[Exact implementation]

**Step 4: Run test — verify it passes**
Run: same command
Expected: PASS

**Step 5: Commit**
`git add ... && git commit -m "feat: ..."`
```

## Rules
- Exact file paths always
- Complete code in plan (not "add validation")
- Exact commands with expected output
- DRY, YAGNI, TDD, frequent commits
- Each step is one action

## After the Plan
"Plan saved to `docs/plans/<filename>.md`. Use `/brainstorming` → review → execute task by task."
