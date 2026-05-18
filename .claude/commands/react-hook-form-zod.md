---
description: Type-safe forms with React Hook Form v7 + Zod v4. Use when building forms, multi-step wizards, or fixing uncontrolled warnings and resolver errors.
---

# React Hook Form + Zod

$ARGUMENTS

**Versions**: react-hook-form@7.71.1, zod@4.3.5, @hookform/resolvers@5.2.2

## Basic Pattern

```typescript
const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});
type FormData = z.infer<typeof schema>;

const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
  resolver: zodResolver(schema),
  defaultValues: { email: '', password: '' }, // REQUIRED — prevents uncontrolled warnings
});

<form onSubmit={handleSubmit(onSubmit)}>
  <input {...register('email')} />
  {errors.email && <span role="alert">{errors.email.message}</span>}
</form>
```

## Critical Rules

✅ Always set `defaultValues` for all fields  
✅ Validate on BOTH client and server (security!)  
✅ Use `field.id` as key in `useFieldArray` (not index)  
✅ Spread `{...field}` in Controller render  
✅ Use `z.infer<typeof schema>` for type inference  
❌ Never skip server validation — security vulnerability  
❌ Never mutate values directly — use `setValue()`  
❌ Never use index as key in `useFieldArray`  

## Controller (for third-party components)

```tsx
<Controller
  name="category"
  control={control}
  render={({ field }) => <CustomSelect {...field} />} // must spread {...field}
/>
```

Use `register` for standard HTML inputs, `Controller` for React Select, date pickers, etc.

## Cross-field Validation

```typescript
z.object({ password: z.string(), confirm: z.string() })
  .refine((d) => d.password === d.confirm, {
    message: "Passwords don't match",
    path: ['confirm'], // CRITICAL: error appears on this field
  })
```

## Dynamic Fields (useFieldArray)

```tsx
const { fields, append, remove } = useFieldArray({ control, name: 'items' });

{fields.map((field, index) => (
  <div key={field.id}> {/* field.id not index */}
    <input {...register(`items.${index}.name` as const)} />
  </div>
))}
```

## Multi-Step Forms

```typescript
const nextStep = async () => {
  const isValid = await trigger(['name', 'email']); // validate specific fields
  if (isValid) setStep(2);
};
```

## Performance (300+ fields)

Don't destructure `formState` — read inline only when needed:
```typescript
// ❌ Slow: const { isDirty, isValid } = form.formState;
// ✅ Fast: read inside handler only
if (!form.formState.isValid) return;
```

## shadcn/ui Import

```typescript
import { useForm } from "react-hook-form";
import { Form, FormField, FormItem, FormControl, FormMessage } from "@/components/ui/form"; // shadcn
// ❌ Never: import { Form } from "react-hook-form"
```
