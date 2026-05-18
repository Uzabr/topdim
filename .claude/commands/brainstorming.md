---
description: Design-first workflow before any feature or change. Explores requirements, proposes approaches, gets approval before any code.
---

# Brainstorming

$ARGUMENTS

**STOP: Do NOT write any code until design is approved.**

## Process

### Step 1: Explore Context
- Read relevant files, docs, recent commits
- Understand what already exists

### Step 2: Ask Clarifying Questions
- One question at a time
- Prefer multiple choice when possible
- Focus on: purpose, constraints, success criteria

### Step 3: Propose 2-3 Approaches
- Present with trade-offs
- Lead with your recommendation and reasoning
- Apply YAGNI ruthlessly — remove unnecessary features

### Step 4: Present Design
- Scale to complexity (a few sentences for simple things)
- Cover: architecture, components, data flow, error handling, testing
- Get approval after each section
- Revise if needed

### Step 5: Write Design Doc
Save to `docs/plans/YYYY-MM-DD-<topic>-design.md` and commit.

### Step 6: Create Implementation Plan
Use `/writing-plans` to create the plan. That is the ONLY next step.

## Anti-Pattern: "This Is Too Simple"
Every change goes through this process. Simple projects are where unexamined assumptions cause the most wasted work.
