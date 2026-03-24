-- Partitioning orders table for scalability
-- Партиционирование по created_at (квартальное)
-- Запускать ПОСЛЕ миграции данных из существующей таблицы

-- Step 1: Create partitioned table
CREATE TABLE IF NOT EXISTS orders_partitioned (
    id              BIGSERIAL,
    order_number    VARCHAR(50) NOT NULL,
    user_id         BIGINT NOT NULL,
    user_email      VARCHAR(255),
    user_phone      VARCHAR(50),
    total_amount    DECIMAL(12, 2) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    paid_at         TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT NOW(),
    deleted         BOOLEAN DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Step 2: Create partitions (2026 quarterly)
CREATE TABLE IF NOT EXISTS orders_2026_q1 PARTITION OF orders_partitioned
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE IF NOT EXISTS orders_2026_q2 PARTITION OF orders_partitioned
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE IF NOT EXISTS orders_2026_q3 PARTITION OF orders_partitioned
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS orders_2026_q4 PARTITION OF orders_partitioned
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');
CREATE TABLE IF NOT EXISTS orders_2027_q1 PARTITION OF orders_partitioned
    FOR VALUES FROM ('2027-01-01') TO ('2027-04-01');

-- Default partition for future dates
CREATE TABLE IF NOT EXISTS orders_default PARTITION OF orders_partitioned DEFAULT;

-- Indexes on partitioned table
CREATE INDEX IF NOT EXISTS idx_orders_part_user ON orders_partitioned(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_part_status ON orders_partitioned(status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_part_number ON orders_partitioned(order_number, created_at);

-- NOTE: To switch to partitioned table in production:
-- 1. Stop writes
-- 2. INSERT INTO orders_partitioned SELECT * FROM orders;
-- 3. ALTER TABLE orders RENAME TO orders_old;
-- 4. ALTER TABLE orders_partitioned RENAME TO orders;
-- 5. Resume writes
-- This is done outside of Flyway as a DBA operation
