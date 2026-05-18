---
description: SQL query optimization, indexing strategies, EXPLAIN analysis. Use when debugging slow queries or improving database performance.
---

# SQL Optimization Patterns

$ARGUMENTS

## Step 1: Analyze with EXPLAIN

```sql
-- PostgreSQL
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 123 AND status = 'pending';
```

**What to look for:**
| Output | Meaning |
|--------|---------|
| `Seq Scan` | Full table scan — bad for large tables |
| `Index Scan` | Using index — good |
| `Index Only Scan` | Index covers query — best |
| `Nested Loop` | OK for small datasets |
| `Hash Join` | Good for larger datasets |
| High `rows` estimate | May need index |

## Step 2: Add the Right Index

```sql
-- Equality + range: B-Tree (default)
CREATE INDEX idx_orders_status ON orders(status);

-- Composite (order matters — most selective first)
CREATE INDEX idx_orders_user_status ON orders(user_id, status);

-- Partial (subset of rows — smaller, faster)
CREATE INDEX idx_pending_orders ON orders(created_at) WHERE status = 'pending';

-- Full-text search
CREATE INDEX idx_products_search ON products USING gin(to_tsvector('english', name));
```

**Composite index rule:** `(user_id, status)` is used by:
- `WHERE user_id = ?` ✅
- `WHERE user_id = ? AND status = ?` ✅
- `WHERE status = ?` ❌ (can't use — missing leading column)

## Common Problems

### N+1 Queries

```java
// ❌ N+1
orders.forEach(o -> o.getUser().getName()); // 1 + N queries

// ✅ Eager join
@Query("SELECT o FROM Order o JOIN FETCH o.user WHERE o.status = :status")
List<Order> findByStatusWithUser(String status);
```

### Missing Index on FK

```sql
-- Always index FK columns
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

### SELECT *

```sql
-- ❌ Fetches unnecessary data
SELECT * FROM products;

-- ✅ Only what you need
SELECT id, name, price FROM products;
```

## Optimization Priority

1. **Add missing index** (biggest wins, usually)
2. **Fix N+1** (application level)
3. **Rewrite query** (use JOIN instead of subquery)
4. **Denormalize** (only for read-heavy analytics)
5. **Partitioning** (very large tables 100M+ rows)
6. **Read replicas** (read-heavy load)

## PostgreSQL Specifics

```sql
-- Update statistics (after large imports)
ANALYZE orders;

-- Find slow queries
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC LIMIT 10;
```
