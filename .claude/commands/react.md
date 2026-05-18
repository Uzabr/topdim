---
description: React 19 patterns — Server Components, Actions, use(), useOptimistic, useFormStatus, concurrent features, TypeScript. Use for any React development or component architecture.
---

# React 19 Patterns

$ARGUMENTS

## Core Rules

- **Immutability**: Never mutate state directly — `setItems([...items, newItem])`
- **Keys**: Use stable IDs, never array indices in lists
- **Effects**: Only for external system synchronization
- **Derived state**: Compute during render, not in useEffect
- **Dependencies**: Always specify correct deps array in useEffect

## Server vs Client

```tsx
// Server Component — data fetching, no hooks, no browser APIs
async function Page() {
  const data = await fetchData();
  return <ClientComponent data={data} />;
}

// Client Component — interactivity, hooks, browser APIs
'use client';
function ClientComponent({ data }) {
  const [selected, setSelected] = useState(null);
  return <div onClick={() => setSelected(data.id)}>{data.name}</div>;
}
```

**Rule**: Mark `'use client'` only when necessary. Default to Server Components.

## React 19 Hooks

```tsx
// use() — read Promise or Context conditionally
const data = use(promise); // only in render

// useOptimistic — optimistic UI
const [optimistic, addOptimistic] = useOptimistic(state, (s, val) => [...s, val]);

// useFormStatus — form submission state (inside form)
const { pending } = useFormStatus();

// useTransition — non-urgent updates
const [isPending, startTransition] = useTransition();
startTransition(() => setFilter(value));
```

## Server Actions

```typescript
'use server'; // must be at top

export async function submitForm(formData: FormData) {
  const data = schema.parse(Object.fromEntries(formData));
  await db.save(data);
  revalidatePath('/');
}
```

## Performance

- React Compiler handles memoization — remove manual `useMemo`/`useCallback`/`memo` when using it
- Use `useDeferredValue` for expensive UI rendering
- `useTransition` for non-urgent state updates

## Common Pitfalls

```tsx
// ❌ Effect for derived state
useEffect(() => setVisible(todos.filter(...)), [todos]);

// ✅ Compute during render
const visible = todos.filter(...);

// ❌ Missing deps
useEffect(() => { console.log(count); }, []); // stale closure

// ❌ use() outside render
function handleClick() { const data = use(promise); } // error
```
