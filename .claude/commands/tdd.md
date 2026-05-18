---
description: Test-Driven Development reference. Write failing test first, watch it fail, write minimal code to pass.
---

# Test-Driven Development

$ARGUMENTS

```
NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST
```

**If you wrote code before the test: delete it. Start over. No exceptions.**

## Red-Green-Refactor

### RED — Write Failing Test

One behavior, clear name, real code (no mocks unless unavoidable):

```typescript
// Good
test('rejects empty email', async () => {
  const result = await submitForm({ email: '' });
  expect(result.error).toBe('Email required');
});
```

**Run it. Confirm it FAILS for the right reason** (feature missing, not typo).

### GREEN — Minimal Code

Write simplest code to pass. Don't add features, don't refactor:

```typescript
// Just enough to pass
function submitForm(data) {
  if (!data.email?.trim()) return { error: 'Email required' };
}
```

**Run it. Confirm it PASSES. Confirm other tests still pass.**

### REFACTOR — Clean Up

Remove duplication, improve names. Keep tests green. Don't add behavior.

## Verification Checklist

- [ ] Watched each test fail before implementing
- [ ] Test failed for the RIGHT reason (feature missing)
- [ ] Wrote minimal code to pass each test
- [ ] All tests pass, output pristine
- [ ] Edge cases and errors covered

## Common Rationalizations

| Excuse | Reality |
|--------|---------|
| "Too simple to test" | Simple code breaks. Test takes 30 seconds. |
| "I'll test after" | Tests passing immediately prove nothing. |
| "Already manually tested" | Ad-hoc ≠ systematic. Can't re-run. |
| "Deleting X hours is wasteful" | Sunk cost. Untested code = technical debt. |
| "TDD will slow me down" | TDD faster than debugging. Always. |

**All red flags mean: Delete code. Start over with TDD.**
