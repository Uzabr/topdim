-- =============================================================================
-- Identity Service Demo Seed Data
-- Marker: all demo users have email ending with @demo.topdim.uz
-- Idempotent: uses ON CONFLICT DO NOTHING
-- =============================================================================

-- BCrypt hash for 'Demo123!' (cost=10)
-- If login fails, regenerate: new BCryptPasswordEncoder(10).encode("Demo123!")
-- and update this constant.

-- =============================================
-- 1. Buyer users (5)
-- =============================================
INSERT INTO users (email, phone, password, first_name, last_name, role, email_verified, enabled)
SELECT
    'buyer' || LPAD(n::TEXT, 3, '0') || '@demo.topdim.uz',
    '+99890100' || LPAD(n::TEXT, 4, '0'),
    '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS',
    'Покупатель',
    n::TEXT,
    'USER',
    TRUE,
    TRUE
FROM generate_series(1, 5) AS n
ON CONFLICT (email) DO NOTHING;

-- =============================================
-- 2. Owner users (50) — role PARTNER
-- =============================================
INSERT INTO users (email, phone, password, first_name, last_name, role, email_verified, enabled)
SELECT
    'owner' || LPAD(n::TEXT, 3, '0') || '@demo.topdim.uz',
    '+99890200' || LPAD(n::TEXT, 4, '0'),
    '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS',
    'Владелец',
    n::TEXT,
    'PARTNER',
    TRUE,
    TRUE
FROM generate_series(1, 50) AS n
ON CONFLICT (email) DO NOTHING;

-- =============================================
-- 3. Cashier users (100) — role PARTNER
--    Two per merchant: cashierNNNa, cashierNNNb
-- =============================================
INSERT INTO users (email, phone, password, first_name, last_name, role, email_verified, enabled)
SELECT
    'cashier' || LPAD(m::TEXT, 3, '0') || suffix || '@demo.topdim.uz',
    '+99890300' || LPAD((m * 2 + CASE WHEN suffix = 'a' THEN 0 ELSE 1 END)::TEXT, 4, '0'),
    '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS',
    'Кассир',
    m::TEXT || suffix,
    'PARTNER',
    TRUE,
    TRUE
FROM generate_series(1, 50) AS m,
     (VALUES ('a'), ('b')) AS s(suffix)
ON CONFLICT (email) DO NOTHING;

SELECT 'Identity seed: users created' AS status;
SELECT COUNT(*) AS demo_users FROM users WHERE email LIKE '%@demo.topdim.uz';
