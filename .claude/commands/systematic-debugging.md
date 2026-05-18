---
description: Systematic 4-phase debugging process. Use before proposing any fix for bugs, test failures, unexpected behavior, or build failures.
---

# Systematic Debugging

$ARGUMENTS

```
NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST
```

## Phase 1: Root Cause Investigation

**Before any fix:**

1. **Read error messages completely** — stack traces, line numbers, error codes
2. **Reproduce consistently** — exact steps, every time?
3. **Check recent changes** — `git diff`, recent commits, new dependencies
4. **Gather evidence** (multi-component systems):
   - Add diagnostic logging at each component boundary
   - Run once to see WHERE it breaks
   - THEN analyze
5. **Trace data flow** — where does the bad value originate? Trace backward.

## Phase 2: Pattern Analysis

- Find working examples in the codebase
- Compare against reference implementation (read it COMPLETELY)
- List every difference, however small

## Phase 3: Hypothesis & Testing

- State clearly: "I think X is the root cause because Y"
- Make the SMALLEST possible change
- One variable at a time
- If wrong → new hypothesis, don't add more changes

## Phase 4: Implementation

1. Write failing test reproducing the bug
2. Implement single fix at root cause
3. Verify fix + no regressions
4. **If 3+ fixes failed → STOP, question the architecture**

## Red Flags (STOP → Return to Phase 1)

- "Quick fix for now"
- "Just try changing X"
- Multiple changes at once
- "I don't fully understand but this might work"
- 3+ failed fixes → stop guessing, discuss architecture

## Common Rationalizations

| Excuse | Reality |
|--------|---------|
| "Issue is simple" | Simple bugs have root causes too |
| "Emergency, no time" | Systematic is FASTER than thrashing |
| "Just try this first" | First fix sets the pattern. Do it right. |
| "One more fix attempt" (after 2+) | 3+ failures = architecture problem |
