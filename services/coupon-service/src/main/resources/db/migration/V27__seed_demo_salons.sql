-- V27: ДЕМО-витрина «салоны красоты / спа для женщин» (Ташкент) для показа партнёрам.
--
-- Назначение: наполнить публичную витрину реалистичными карточками, НЕ создавая
-- покупаемых фейков. Все офферы ACTIVE и попадают в каталог, но каждая опция имеет
-- quantity_limit = quantity_sold = 1 → «распродано»: покупка заблокирована и на
-- сервере (order-service assertQuantityAvailable → remaining=0), и на фронте (кнопка
-- «В корзину» скрыта). Продажа невозможна → статус не улетит в SOLD_OUT, листинг
-- остаётся видимым. Фото — лицензионные Unsplash (commercial use), проверены на 200
-- и визуально сверены под услугу. Названия салонов вымышленные (не копии реальных).
--
-- ОБРАТИМОСТЬ (удалить всю демо-витрину одним блоком):
--   DELETE FROM coupon_options o USING coupon_offers c JOIN merchants m ON c.merchant_id=m.id
--     WHERE o.coupon_offer_id=c.id AND m.website='https://demo.sizbiz.uz';
--   DELETE FROM coupon_images  i USING coupon_offers c JOIN merchants m ON c.merchant_id=m.id
--     WHERE i.coupon_offer_id=c.id AND m.website='https://demo.sizbiz.uz';
--   DELETE FROM coupon_offers  c USING merchants m
--     WHERE c.merchant_id=m.id AND m.website='https://demo.sizbiz.uz';
--   DELETE FROM merchant_locations l USING merchants m
--     WHERE l.merchant_id=m.id AND m.website='https://demo.sizbiz.uz';
--   DELETE FROM merchants WHERE website='https://demo.sizbiz.uz';
--
-- Идемпотентность: Flyway применяет V27 один раз. Повторно вставит дубли только при
-- ручном ре-ране — не делать.

-- 1) Glow Beauty Studio — Маникюр + гель-лак — Юнусабад
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('Glow Beauty Studio',
            'Студия ногтевого сервиса в Юнусабаде: аккуратный маникюр, стойкое покрытие и уютная атмосфера.',
            'https://images.unsplash.com/photo-1604654894610-df63bc536371?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1604654894610-df63bc536371?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Юнусабад, ул. Амира Темура, 108', '+998 90 111-22-01', '09:00–21:00, без выходных', 41.3630, 69.2890, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'Маникюр + гель-лак',
           'Классический или аппаратный маникюр с покрытием гель-лак любого цвета. Снятие старого покрытия и уход за кутикулой включены. Держится до 3 недель.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           250000, 150000, 40,
           'https://images.unsplash.com/photo-1604654894610-df63bc536371?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', FALSE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'Маникюр + гель-лак', 250000, 150000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1610992015732-2449b76344bc?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1519014816548-bf5fe059798b?auto=format&fit=crop&w=1200&q=80', 2 FROM o;

-- 2) SILK Hair & Color — Стрижка + окрашивание — Мирзо-Улугбек
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('SILK Hair & Color',
            'Салон парикмахерского искусства: женские стрижки, сложное окрашивание и уходовые процедуры для волос.',
            'https://images.unsplash.com/photo-1562322140-8baeececf3df?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1562322140-8baeececf3df?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Мирзо-Улугбек, ул. Мустакиллик, 45', '+998 90 111-22-02', '10:00–20:00, без выходных', 41.3250, 69.3340, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'Женская стрижка + окрашивание',
           'Женская стрижка любой длины, мытьё, окрашивание в один тон и укладка от топ-стилиста. Профессиональная косметика для волос.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           600000, 390000, 35,
           'https://images.unsplash.com/photo-1562322140-8baeececf3df?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', FALSE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'Стрижка + окрашивание + укладка', 600000, 390000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1580618672591-eb180b1a973f?auto=format&fit=crop&w=1200&q=80', 2 FROM o;

-- 3) Serenity SPA & Hammam — СПА-день — Яккасарай
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('Serenity SPA & Hammam',
            'СПА-центр с восточным хаммамом, пилингом и релакс-программами для женщин.',
            'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Яккасарай, ул. Шота Руставели, 12', '+998 90 111-22-03', '10:00–22:00, без выходных', 41.2870, 69.2400, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'СПА-день: хаммам + пилинг + массаж',
           'Восточный хаммам с мыльным пеной-массажем, пилинг тела кесе и расслабляющий массаж 30 минут. Чай и халат включены.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           550000, 330000, 40,
           'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', TRUE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'СПА-программа «Хаммам»', 550000, 330000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1600334129128-685c5582fd35?auto=format&fit=crop&w=1200&q=80', 2 FROM o;

-- 4) Lumière Cosmetology — Чистка лица — Чиланзар
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('Lumière Cosmetology',
            'Кабинет эстетической косметологии: чистки, уходовые программы и аппаратные процедуры для лица.',
            'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Чиланзар, ул. Бунёдкор, 30', '+998 90 111-22-04', '09:00–20:00, вых. вс', 41.2750, 69.2040, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'Чистка лица + уходовая программа',
           'Комбинированная чистка лица, тонизирование, альгинатная маска по типу кожи и увлажняющий уход. Консультация косметолога включена.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           400000, 240000, 40,
           'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', FALSE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'Чистка + уход', 400000, 240000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1519823551278-64ac92734fb1?auto=format&fit=crop&w=1200&q=80', 2 FROM o;

-- 5) Velvet Nails Bar — Маникюр + педикюр — Шайхантахур
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('Velvet Nails Bar',
            'Ногтевой бар: маникюр, педикюр и дизайн ногтей в центре города.',
            'https://images.unsplash.com/photo-1519014816548-bf5fe059798b?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1519014816548-bf5fe059798b?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Шайхантахур, ул. Навои, 22', '+998 90 111-22-05', '10:00–21:00, без выходных', 41.3260, 69.2360, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'Маникюр + педикюр комбо',
           'Комплекс: аппаратный маникюр и педикюр с покрытием гель-лак. Питательный уход и парафинотерапия рук в подарок.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           350000, 210000, 40,
           'https://images.unsplash.com/photo-1519014816548-bf5fe059798b?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', FALSE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'Маникюр + педикюр', 350000, 210000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1604654894610-df63bc536371?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1610992015732-2449b76344bc?auto=format&fit=crop&w=1200&q=80', 2 FROM o;

-- 6) Aura Massage & Relax — Массаж — Мирабад
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('Aura Massage & Relax',
            'Студия массажа и релакса: расслабляющие и уходовые программы для тела.',
            'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Мирабад, ул. Ойбека, 18', '+998 90 111-22-06', '10:00–21:00, без выходных', 41.2990, 69.2790, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'Расслабляющий массаж 60 минут',
           'Классический расслабляющий массаж всего тела 60 минут с ароматическими маслами. Тёплая атмосфера и травяной чай после сеанса.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           450000, 270000, 40,
           'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', FALSE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'Массаж тела 60 мин', 450000, 270000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1519823551278-64ac92734fb1?auto=format&fit=crop&w=1200&q=80', 2 FROM o;

-- 7) BrowBar Tashkent — Брови + ресницы — Яшнабад
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('BrowBar Tashkent',
            'Бровинг-бар: архитектура и окрашивание бровей, ламинирование ресниц.',
            'https://images.unsplash.com/photo-1487412947147-5cebf100ffc2?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1487412947147-5cebf100ffc2?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Яшнабад, ул. Фаргона йули, 55', '+998 90 111-22-07', '10:00–20:00, без выходных', 41.2830, 69.3520, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'Брови + ламинирование ресниц',
           'Коррекция формы и окрашивание бровей хной или краской плюс ламинирование ресниц с составом для укрепления. Идеальный взгляд на 4–6 недель.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           320000, 192000, 40,
           'https://images.unsplash.com/photo-1487412947147-5cebf100ffc2?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', FALSE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'Брови + ресницы', 320000, 192000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1596704017254-9b121068fb31?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1200&q=80', 2 FROM o;

-- 8) Makeup Room — Макияж + укладка — Мирзо-Улугбек
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('Makeup Room',
            'Студия макияжа и укладок для торжеств, фотосессий и вечерних выходов.',
            'https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Мирзо-Улугбек, ул. Буюк Ипак Йули, 77', '+998 90 111-22-08', '09:00–21:00, без выходных', 41.3310, 69.3410, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'Вечерний макияж + укладка',
           'Профессиональный вечерний макияж стойкими средствами и укладка волос. Идеально для торжеств и фотосессий. Пробный тест-макияж по желанию.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           500000, 350000, 30,
           'https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', TRUE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'Макияж + укладка', 500000, 350000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1487412947147-5cebf100ffc2?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1596704017254-9b121068fb31?auto=format&fit=crop&w=1200&q=80', 2 FROM o;

-- 9) Blossom Hair Lounge — Уход + укладка — Учтепа
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('Blossom Hair Lounge',
            'Салон по уходу за волосами: восстановление, кератин и укладки.',
            'https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Учтепа, ул. Чупонота, 9', '+998 90 111-22-09', '10:00–20:00, вых. вс', 41.2960, 69.1830, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'Восстановление волос + укладка',
           'Глубокое восстановление волос профессиональным составом, питательная маска и укладка. Волосы становятся гладкими и блестящими.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           280000, 168000, 40,
           'https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', FALSE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'Уход + укладка', 280000, 168000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1595476108010-b4d1f102b1b1?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1562322140-8baeececf3df?auto=format&fit=crop&w=1200&q=80', 2 FROM o;

-- 10) Pure Skin Clinic — Косметология / пилинг — Сергели
WITH m AS (
    INSERT INTO merchants (name, description, logo_url, cover_url, email, website, contact_person, active)
    VALUES ('Pure Skin Clinic',
            'Клиника эстетической косметологии: пилинги, аппаратный уход и массаж лица.',
            'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=1200&q=80',
            'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=1200&q=80',
            'info@demo.sizbiz.uz', 'https://demo.sizbiz.uz', 'DEMO', TRUE)
    RETURNING id
), l AS (
    INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
    SELECT id, 'Основной салон', 'Сергели, ул. Янги Сергели, 3', '+998 90 111-22-10', '09:00–20:00, без выходных', 41.2230, 69.2200, TRUE, TRUE FROM m
), o AS (
    INSERT INTO coupon_offers (title, offer_description, merchant_id, category_id, old_price, from_price, discount_percent, cover_image_url, buy_until, use_until, is_gift_available, status)
    SELECT 'Косметология: пилинг + массаж лица',
           'Химический пилинг по типу кожи для обновления и сияния, успокаивающая маска и лимфодренажный массаж лица. Заметный результат после первой процедуры.',
           m.id, (SELECT id FROM categories WHERE slug = 'beauty'),
           600000, 360000, 40,
           'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=1200&q=80',
           TIMESTAMP '2026-12-31 23:59:00', TIMESTAMP '2027-03-31 23:59:00', FALSE, 'ACTIVE'
    FROM m RETURNING id
), opt AS (
    INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
    SELECT id, 'Пилинг + массаж лица', 600000, 360000, 1, 1, 'ACTIVE' FROM o
)
INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
SELECT id, 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1200&q=80', 1 FROM o
UNION ALL SELECT id, 'https://images.unsplash.com/photo-1600334129128-685c5582fd35?auto=format&fit=crop&w=1200&q=80', 2 FROM o;
