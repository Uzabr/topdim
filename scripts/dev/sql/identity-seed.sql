-- =============================================================================
-- Identity Service Demo Seed Data (v3 — Realistic Tashkent Data)
-- Marker: all demo users have email ending with @demo.topdim.uz
-- Idempotent: uses ON CONFLICT DO NOTHING
-- =============================================================================

-- BCrypt hash for 'Demo123!' (cost=10)
-- If login fails, regenerate: new BCryptPasswordEncoder(10).encode("Demo123!")
-- and update this constant.

-- =============================================
-- 1. Buyer users (5) — реальные узбекские имена
-- =============================================
INSERT INTO users (email, phone, password, first_name, last_name, role, email_verified, enabled) VALUES
    ('buyer001@demo.topdim.uz', '+998901000001', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Азиз', 'Каримов', 'USER', TRUE, TRUE),
    ('buyer002@demo.topdim.uz', '+998901000002', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Диёра', 'Рахимова', 'USER', TRUE, TRUE),
    ('buyer003@demo.topdim.uz', '+998901000003', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Санжар', 'Усманов', 'USER', TRUE, TRUE),
    ('buyer004@demo.topdim.uz', '+998901000004', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Нигора', 'Ташпулатова', 'USER', TRUE, TRUE),
    ('buyer005@demo.topdim.uz', '+998901000005', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Бехруз', 'Мирзаев', 'USER', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

-- =============================================
-- 2. Owner users (50) — role PARTNER
-- =============================================
INSERT INTO users (email, phone, password, first_name, last_name, role, email_verified, enabled) VALUES
    -- Рестораны (1-6)
    ('owner001@demo.topdim.uz', '+998902000001', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Рустам', 'Ахмедов', 'PARTNER', TRUE, TRUE),
    ('owner002@demo.topdim.uz', '+998902000002', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Фарход', 'Саидов', 'PARTNER', TRUE, TRUE),
    ('owner003@demo.topdim.uz', '+998902000003', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Алишер', 'Назаров', 'PARTNER', TRUE, TRUE),
    ('owner004@demo.topdim.uz', '+998902000004', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Бахтиёр', 'Турсунов', 'PARTNER', TRUE, TRUE),
    ('owner005@demo.topdim.uz', '+998902000005', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Шухрат', 'Хакимов', 'PARTNER', TRUE, TRUE),
    ('owner006@demo.topdim.uz', '+998902000006', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Джамшид', 'Абдуллаев', 'PARTNER', TRUE, TRUE),
    -- Кофейни (7-12)
    ('owner007@demo.topdim.uz', '+998902000007', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Тимур', 'Юлдашев', 'PARTNER', TRUE, TRUE),
    ('owner008@demo.topdim.uz', '+998902000008', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Лола', 'Исмаилова', 'PARTNER', TRUE, TRUE),
    ('owner009@demo.topdim.uz', '+998902000009', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Давронбек', 'Шавкатов', 'PARTNER', TRUE, TRUE),
    ('owner010@demo.topdim.uz', '+998902000010', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Камола', 'Нурматова', 'PARTNER', TRUE, TRUE),
    ('owner011@demo.topdim.uz', '+998902000011', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Отабек', 'Кадыров', 'PARTNER', TRUE, TRUE),
    ('owner012@demo.topdim.uz', '+998902000012', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Мадина', 'Файзиева', 'PARTNER', TRUE, TRUE),
    -- Красота (13-18)
    ('owner013@demo.topdim.uz', '+998902000013', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Гулнора', 'Рашидова', 'PARTNER', TRUE, TRUE),
    ('owner014@demo.topdim.uz', '+998902000014', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Зафар', 'Мухаммедов', 'PARTNER', TRUE, TRUE),
    ('owner015@demo.topdim.uz', '+998902000015', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Шахло', 'Бабаджанова', 'PARTNER', TRUE, TRUE),
    ('owner016@demo.topdim.uz', '+998902000016', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Нодир', 'Хайдаров', 'PARTNER', TRUE, TRUE),
    ('owner017@demo.topdim.uz', '+998902000017', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Малика', 'Султанова', 'PARTNER', TRUE, TRUE),
    ('owner018@demo.topdim.uz', '+998902000018', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Ботир', 'Рустамов', 'PARTNER', TRUE, TRUE),
    -- SPA (19-23)
    ('owner019@demo.topdim.uz', '+998902000019', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Дилноза', 'Ташмухамедова', 'PARTNER', TRUE, TRUE),
    ('owner020@demo.topdim.uz', '+998902000020', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Улугбек', 'Норматов', 'PARTNER', TRUE, TRUE),
    ('owner021@demo.topdim.uz', '+998902000021', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Зульфия', 'Каюмова', 'PARTNER', TRUE, TRUE),
    ('owner022@demo.topdim.uz', '+998902000022', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Хуршид', 'Азимов', 'PARTNER', TRUE, TRUE),
    ('owner023@demo.topdim.uz', '+998902000023', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Севара', 'Юнусова', 'PARTNER', TRUE, TRUE),
    -- Фитнес (24-29)
    ('owner024@demo.topdim.uz', '+998902000024', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Дониёр', 'Жураев', 'PARTNER', TRUE, TRUE),
    ('owner025@demo.topdim.uz', '+998902000025', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Анвар', 'Сабиров', 'PARTNER', TRUE, TRUE),
    ('owner026@demo.topdim.uz', '+998902000026', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Гулчехра', 'Тураева', 'PARTNER', TRUE, TRUE),
    ('owner027@demo.topdim.uz', '+998902000027', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Элмурод', 'Бахтияров', 'PARTNER', TRUE, TRUE),
    ('owner028@demo.topdim.uz', '+998902000028', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Ферузбек', 'Олимов', 'PARTNER', TRUE, TRUE),
    ('owner029@demo.topdim.uz', '+998902000029', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Наргиза', 'Ходжаева', 'PARTNER', TRUE, TRUE),
    -- Развлечения (30-34)
    ('owner030@demo.topdim.uz', '+998902000030', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Ислом', 'Маматов', 'PARTNER', TRUE, TRUE),
    ('owner031@demo.topdim.uz', '+998902000031', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Сардор', 'Халилов', 'PARTNER', TRUE, TRUE),
    ('owner032@demo.topdim.uz', '+998902000032', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Жасур', 'Абдурахманов', 'PARTNER', TRUE, TRUE),
    ('owner033@demo.topdim.uz', '+998902000033', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Бобур', 'Шарипов', 'PARTNER', TRUE, TRUE),
    ('owner034@demo.topdim.uz', '+998902000034', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Хасан', 'Отажонов', 'PARTNER', TRUE, TRUE),
    -- Образование (35-40)
    ('owner035@demo.topdim.uz', '+998902000035', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Комилжон', 'Нематов', 'PARTNER', TRUE, TRUE),
    ('owner036@demo.topdim.uz', '+998902000036', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Озода', 'Кобилова', 'PARTNER', TRUE, TRUE),
    ('owner037@demo.topdim.uz', '+998902000037', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Абдулла', 'Файзуллаев', 'PARTNER', TRUE, TRUE),
    ('owner038@demo.topdim.uz', '+998902000038', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Хилола', 'Гафурова', 'PARTNER', TRUE, TRUE),
    ('owner039@demo.topdim.uz', '+998902000039', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Мухаммад', 'Содиков', 'PARTNER', TRUE, TRUE),
    ('owner040@demo.topdim.uz', '+998902000040', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Нафиса', 'Рахматуллаева', 'PARTNER', TRUE, TRUE),
    -- Услуги (41-45)
    ('owner041@demo.topdim.uz', '+998902000041', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Равшан', 'Мирсалимов', 'PARTNER', TRUE, TRUE),
    ('owner042@demo.topdim.uz', '+998902000042', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Фируза', 'Латипова', 'PARTNER', TRUE, TRUE),
    ('owner043@demo.topdim.uz', '+998902000043', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Ойбек', 'Нурмухамедов', 'PARTNER', TRUE, TRUE),
    ('owner044@demo.topdim.uz', '+998902000044', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Зухра', 'Ибрагимова', 'PARTNER', TRUE, TRUE),
    ('owner045@demo.topdim.uz', '+998902000045', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Нурбек', 'Холматов', 'PARTNER', TRUE, TRUE),
    -- Магазины (46-50)
    ('owner046@demo.topdim.uz', '+998902000046', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Сарвиноз', 'Мухитдинова', 'PARTNER', TRUE, TRUE),
    ('owner047@demo.topdim.uz', '+998902000047', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Лазиз', 'Ташматов', 'PARTNER', TRUE, TRUE),
    ('owner048@demo.topdim.uz', '+998902000048', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Муштарий', 'Валиева', 'PARTNER', TRUE, TRUE),
    ('owner049@demo.topdim.uz', '+998902000049', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Шерзод', 'Мирзоев', 'PARTNER', TRUE, TRUE),
    ('owner050@demo.topdim.uz', '+998902000050', '$2a$10$jWnlIbikLAhYLBgz5j0q7.zfFaDjs0WdRaT8O35xwW7mmU8zclXkS', 'Барно', 'Хамраева', 'PARTNER', TRUE, TRUE)
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

SELECT 'Identity seed v3: users created' AS status;
SELECT COUNT(*) AS demo_users FROM users WHERE email LIKE '%@demo.topdim.uz';
