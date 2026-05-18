---
description: Design production-ready SQL/NoSQL schemas. Use when modeling entities, designing tables, planning migrations, or optimizing data structures.
---

# Database Schema Designer

$ARGUMENTS

## Process

1. **Analyze** — identify entities, relationships, access patterns (read vs write heavy)
2. **Design** — normalize to 3NF, define PKs/FKs, choose data types, add constraints
3. **Optimize** — index strategy, timestamps, denormalization for read-heavy queries
4. **Migrate** — reversible UP/DOWN scripts, backward compatible, zero-downtime

## Quick Reference

| Task | Approach |
|------|----------|
| New schema | Normalize to 3NF first |
| SQL vs NoSQL | Access patterns decide |
| Primary keys | BIGINT or UUID (UUID for distributed) |
| Foreign keys | Always constrain with ON DELETE strategy |
| Indexes | Every FK + WHERE/ORDER BY columns |
| Migrations | Always reversible |

## Anti-Patterns

| Avoid | Why | Instead |
|-------|-----|---------|
| `VARCHAR(255)` everywhere | Hides intent | Size appropriately |
| `FLOAT` for money | Rounding errors | `DECIMAL(10,2)` |
| No FK constraints | Orphaned data | Always define FKs |
| No indexes on FKs | Slow JOINs | Index every FK |
| Dates as strings | Can't compare | `DATE`, `TIMESTAMP` |
| `NOT NULL` without default | Breaks existing rows | Add nullable → backfill → constrain |

## Index Strategy

```sql
-- FK index (always)
CREATE INDEX idx_orders_user ON orders(user_id);

-- Composite (most selective first)
CREATE INDEX idx_orders_user_status ON orders(user_id, status);

-- Partial (subset of rows)
CREATE INDEX idx_active_users ON users(email) WHERE status = 'active';
```

## Migration Template

```sql
-- UP
BEGIN;
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
CREATE INDEX idx_users_phone ON users(phone);
COMMIT;

-- DOWN
BEGIN;
DROP INDEX idx_users_phone;
ALTER TABLE users DROP COLUMN phone;
COMMIT;
```

## Zero-Downtime: Add NOT NULL Column

```sql
-- Step 1: Add nullable
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
-- Step 2: Deploy code writing to new column
-- Step 3: Backfill
UPDATE users SET phone = '' WHERE phone IS NULL;
-- Step 4: Constrain
ALTER TABLE users ALTER COLUMN phone SET NOT NULL;
```

## Verification Checklist

- [ ] Every table has primary key
- [ ] All FKs have constraints + ON DELETE strategy
- [ ] Indexes on all FKs
- [ ] Appropriate data types (DECIMAL for money)
- [ ] NOT NULL on required fields
- [ ] created_at + updated_at timestamps
- [ ] Migrations are reversible
- [ ] Tested on staging with production-like data
