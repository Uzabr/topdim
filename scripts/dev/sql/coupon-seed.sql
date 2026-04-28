-- =============================================================================
-- Coupon Service Demo Seed Data (v2)
-- Even distribution: ~6 merchants per category, ~55 coupons per category
-- Real photos via picsum.photos (deterministic seeds)
-- Marker: demo merchants have email ending with @demo.topdim.uz
-- Idempotent: uses WHERE NOT EXISTS
-- =============================================================================

-- =============================================
-- 1. Categories (ensure all 9 exist)
-- =============================================
INSERT INTO categories (name, name_uz, slug, icon_url, sort_order, active) VALUES
    ('Рестораны',      'Restoranlar',    'restaurants',   NULL, 10, TRUE),
    ('Кофейни',        'Qahvaxonalar',   'coffee',        NULL, 11, TRUE),
    ('Красота',        'Go''zallik',     'beauty',        NULL, 12, TRUE),
    ('SPA',            'SPA',            'spa',           NULL, 13, TRUE),
    ('Фитнес',         'Fitnes',         'fitness',       NULL, 14, TRUE),
    ('Развлечения',    'Ko''ngilochar',  'entertainment', NULL, 15, TRUE),
    ('Образование',    'Ta''lim',        'education',     NULL, 16, TRUE),
    ('Услуги',         'Xizmatlar',      'services',      NULL, 17, TRUE),
    ('Магазины',       'Do''konlar',     'shops',         NULL, 18, TRUE)
ON CONFLICT (name) DO NOTHING;

-- =============================================
-- 2. Merchants (50) — evenly distributed across 9 categories
--    6 merchants × 5 categories + 5 merchants × 4 categories = 50
--    Category assignment: merchant i → category ((i-1) % 9) + 1
-- =============================================
DO $$
DECLARE
    -- Category slugs for round-robin assignment
    cat_slugs TEXT[] := ARRAY[
        'restaurants', 'coffee', 'beauty', 'spa', 'fitness',
        'entertainment', 'education', 'services', 'shops'
    ];

    merchant_names TEXT[] := ARRAY[
        -- 1-6: Restaurants
        'Demo Plov Center', 'Demo Sushi Master', 'Demo Pizza Italia',
        'Demo Burger Lab', 'Demo Kebab House', 'Demo Paella Club',
        -- 7-12: Coffee
        'Demo Coffee Room', 'Demo Barista Club', 'Demo Brew Point',
        'Demo Espresso Bar', 'Demo Chai Lounge', 'Demo Mokko',
        -- 13-18: Beauty
        'Demo Beauty Studio', 'Demo Nail Art', 'Demo Hair Design',
        'Demo Lash & Brow', 'Demo Glow Salon', 'Demo Chic Style',
        -- 19-23: SPA
        'Demo SPA Oasis', 'Demo Relax Center', 'Demo Hammam Palace',
        'Demo Thai Massage', 'Demo Wellness Hub',
        -- 24-29: Fitness
        'Demo Gym Pro', 'Demo Yoga Space', 'Demo CrossFit Box',
        'Demo Pool & Sauna', 'Demo Fight Club', 'Demo Run Club',
        -- 30-34: Entertainment
        'Demo Bowling Park', 'Demo Karting Arena', 'Demo Quest Room',
        'Demo Laser Tag', 'Demo VR World',
        -- 35-40: Education
        'Demo Language Lab', 'Demo IT Academy', 'Demo Art School',
        'Demo Music Studio', 'Demo Kids Academy', 'Demo Brain Hub',
        -- 41-45: Services
        'Demo Auto Service', 'Demo Photo Studio', 'Demo Print House',
        'Demo Laundry Express', 'Demo Pet Clinic',
        -- 46-50: Shops
        'Demo Gadget Shop', 'Demo Gift Store', 'Demo Flower Market',
        'Demo Book Corner', 'Demo Sport Outlet'
    ];

    merchant_descriptions TEXT[] := ARRAY[
        'Лучший плов в городе! Семейные рецепты с 1985 года.',
        'Свежие роллы и суши от шеф-повара из Японии.',
        'Настоящая итальянская пицца из дровяной печи.',
        'Бургеры из мраморной говядины с крафтовыми соусами.',
        'Традиционный кебаб на мангале — вкус Востока.',
        'Испанская кухня и авторские паэльи.',
        'Specialty coffee и авторские десерты.',
        'Кофе от обжарщика и свежая выпечка.',
        'Third wave coffee в центре города.',
        'Итальянский эспрессо и круассаны.',
        'Уютная чайхана с восточным колоритом.',
        'Какао, латте-арт и уникальные напитки.',
        'Полный спектр beauty-услуг для современных женщин.',
        'Маникюр, педикюр, дизайн ногтей.',
        'Стрижки, окрашивание, укладки.',
        'Ламинирование, наращивание ресниц и бровей.',
        'Уход за кожей лица и тела.',
        'Стильный образ для любого повода.',
        'Расслабляющий SPA с бассейном.',
        'Массаж, сауна, зона отдыха.',
        'Традиционный хаммам с парилкой.',
        'Тайский массаж от мастеров из Таиланда.',
        'Комплексные программы оздоровления.',
        'Современный тренажёрный зал 24/7.',
        'Йога и медитация для всех уровней.',
        'Функциональный тренинг CrossFit.',
        'Бассейн олимпийского размера.',
        'Бокс, MMA, кикбоксинг.',
        'Беговой клуб и подготовка к марафонам.',
        'Боулинг для всей семьи.',
        'Картинг для детей и взрослых.',
        'Квест-комнаты с захватывающими сценариями.',
        'Лазертаг и командные игры.',
        'Виртуальная реальность нового поколения.',
        'Изучение иностранных языков с носителями.',
        'IT-курсы: Python, Java, React.',
        'Живопись, скульптура, графический дизайн.',
        'Уроки музыки для всех возрастов.',
        'Развивающие занятия для детей от 3 лет.',
        'Курсы скорочтения и развития памяти.',
        'Автосервис полного цикла.',
        'Профессиональная фотостудия.',
        'Полиграфия и широкоформатная печать.',
        'Химчистка и прачечная за 1 час.',
        'Ветеринарная клиника 24/7.',
        'Гаджеты и аксессуары по лучшим ценам.',
        'Подарки и сувениры на любой случай.',
        'Свежие цветы и букеты с доставкой.',
        'Книги и канцтовары.',
        'Спортивная одежда и обувь.'
    ];

    -- Category-specific coupon offer titles (10 per category)
    -- Used as: coupon_titles[cat_index][j]
    coupon_titles_flat TEXT[] := ARRAY[
        -- restaurants (cat 1, offsets 0-9)
        'Обед на двоих', 'Семейный ужин', 'Бизнес-ланч', 'Банкет на 10 человек', 'Завтрак с кофе',
        'Романтический ужин', 'Доставка на дом', 'Шеф-стол на 4', 'Дегустация блюд', 'Праздничный сет',
        -- coffee (cat 2, offsets 10-19)
        'Кофе + десерт', 'Абонемент на кофе (месяц)', 'Кофе-брейк на 5', 'Авторский напиток', 'Завтрак в кофейне',
        'Мастер-класс бариста', 'Кофейный сет', 'Холодные напитки', 'Бранч на двоих', 'Чайная церемония',
        -- beauty (cat 3, offsets 20-29)
        'Маникюр + педикюр', 'Стрижка и укладка', 'SPA-уход за лицом', 'Наращивание ресниц', 'Окрашивание волос',
        'Комплекс «Красотка»', 'Ламинирование бровей', 'Депиляция', 'Массаж лица', 'Свадебный образ',
        -- spa (cat 4, offsets 30-39)
        'Массаж 60 мин', 'Хаммам + пилинг', 'SPA-день', 'Расслабляющий массаж', 'Горячие камни',
        'Антистресс-программа', 'SPA для двоих', 'Обёртывание', 'Ароматерапия', 'Тайский массаж 90 мин',
        -- fitness (cat 5, offsets 40-49)
        'Абонемент на месяц', 'Персональная тренировка', 'Групповые занятия (10)', 'Бассейн безлимит', 'Йога-курс 8 занятий',
        'Пробная тренировка', 'Фитнес + SPA', 'Бокс 10 тренировок', 'Кроссфит-курс', 'Растяжка 5 занятий',
        -- entertainment (cat 6, offsets 50-59)
        'Боулинг на 4', 'Картинг 30 мин', 'Квест для компании', 'Лазертаг 1 час', 'VR-сеанс 45 мин',
        'Детский праздник', 'Корпоратив', 'Батуты безлимит', 'Кино на двоих', 'Караоке на 2 часа',
        -- education (cat 7, offsets 60-69)
        'Английский (месяц)', 'Python-курс', 'Рисование 8 уроков', 'Гитара для начинающих', 'Подготовка к экзаменам',
        'Мастер-класс фото', 'Робототехника (дети)', 'Шахматы 12 занятий', 'Ораторское мастерство', 'IT-интенсив выходного дня',
        -- services (cat 8, offsets 70-79)
        'ТО автомобиля', 'Фотосессия 1 час', 'Печать 100 визиток', 'Химчистка пальто', 'Осмотр питомца',
        'Шиномонтаж', 'Видеосъёмка события', 'Ремонт телефона', 'Уборка квартиры', 'Доставка цветов',
        -- shops (cat 9, offsets 80-89)
        'Смартфон со скидкой', 'Подарочный набор', 'Букет роз', 'Книжный набор', 'Кроссовки Nike',
        'Сертификат на покупки', 'Аксессуар в подарок', 'Набор для школьника', 'Органик-набор', 'Текстиль для дома'
    ];

    -- Unsplash image IDs per category (6 cover + 6 gallery each)
    -- Using picsum.photos with deterministic seeds for real photos
    -- Format: https://picsum.photos/seed/{seed}/{w}/{h}

    addresses TEXT[] := ARRAY[
        'ул. Амира Темура 10', 'ул. Навои 25', 'пр. Мустакиллик 33', 'ул. Чиланзар 7', 'ул. Юнусабад 12',
        'ул. Бобура 15', 'ул. Фараби 8', 'пр. Бунёдкор 44', 'ул. Шота Руставели 3', 'ул. Мирабад 21',
        'ул. Дружбы Народов 5', 'пр. Узбекистанский 55', 'ул. Лабзак 17', 'ул. Сергели 9', 'ул. Олмазор 28',
        'ул. Яккасарай 11', 'ул. Мирзо Улугбек 6', 'ул. Чилонзор 88', 'ул. Бешкайрагач 14', 'ул. Тошкент 40',
        'ул. Нукус 22', 'ул. Катартал 31', 'пр. Космонавтов 19', 'ул. Ангрен 7', 'ул. Ахмад Дониш 54',
        'ул. Беруни 13', 'ул. Чехова 42', 'ул. Шахрисабз 29', 'ул. Истиклол 16', 'ул. Максим Горький 37',
        'ул. Пушкина 8', 'ул. Глинки 23', 'ул. Бабура 45', 'пр. Навои 67', 'ул. Амира Темура 89',
        'ул. Мукими 10', 'ул. Куйлюк 34', 'ул. Ойбек 56', 'ул. Тараса Шевченко 71', 'ул. Алишера Навои 12',
        'ул. Чулпон 18', 'ул. Фуркат 27', 'ул. Хамза 39', 'пр. Мирзо Улугбек 50', 'ул. Юсуф Хос Хожиб 63',
        'ул. Кашгарская 11', 'ул. Фидокор 25', 'ул. Ислама Каримова 33', 'ул. Буюк Ипак Йули 7', 'ул. Махтумкули 44'
    ];
    phones TEXT[] := ARRAY[
        '+998901110001', '+998901110002', '+998901110003', '+998901110004', '+998901110005',
        '+998901110006', '+998901110007', '+998901110008', '+998901110009', '+998901110010',
        '+998901110011', '+998901110012', '+998901110013', '+998901110014', '+998901110015',
        '+998901110016', '+998901110017', '+998901110018', '+998901110019', '+998901110020',
        '+998901110021', '+998901110022', '+998901110023', '+998901110024', '+998901110025',
        '+998901110026', '+998901110027', '+998901110028', '+998901110029', '+998901110030',
        '+998901110031', '+998901110032', '+998901110033', '+998901110034', '+998901110035',
        '+998901110036', '+998901110037', '+998901110038', '+998901110039', '+998901110040',
        '+998901110041', '+998901110042', '+998901110043', '+998901110044', '+998901110045',
        '+998901110046', '+998901110047', '+998901110048', '+998901110049', '+998901110050'
    ];

    -- Merchant → category mapping (evenly distributed)
    -- 1-6: restaurants, 7-12: coffee, 13-18: beauty, 19-23: spa,
    -- 24-29: fitness, 30-34: entertainment, 35-40: education,
    -- 41-45: services, 46-50: shops
    i INT;
    j INT;
    m_id BIGINT;
    cat_id BIGINT;
    cat_idx INT;  -- 1-based category index
    offer_status TEXT;
    offer_title TEXT;
    offer_desc TEXT;
    old_p DECIMAL;
    from_p DECIMAL;
    disc INT;
    buy_dt TIMESTAMP;
    use_dt TIMESTAMP;
    offer_id BIGINT;
    cover_url TEXT;
    img1_url TEXT;
    img2_url TEXT;
    title_offset INT;
BEGIN
    FOR i IN 1..50 LOOP
        -- Determine category index (1-9)
        cat_idx := CASE
            WHEN i <= 6  THEN 1  -- restaurants
            WHEN i <= 12 THEN 2  -- coffee
            WHEN i <= 18 THEN 3  -- beauty
            WHEN i <= 23 THEN 4  -- spa
            WHEN i <= 29 THEN 5  -- fitness
            WHEN i <= 34 THEN 6  -- entertainment
            WHEN i <= 40 THEN 7  -- education
            WHEN i <= 45 THEN 8  -- services
            ELSE              9  -- shops
        END;

        -- Get category id by slug
        SELECT id INTO cat_id FROM categories
            WHERE slug = cat_slugs[cat_idx]
            LIMIT 1;

        IF cat_id IS NULL THEN
            SELECT id INTO cat_id FROM categories WHERE active = TRUE ORDER BY id LIMIT 1;
        END IF;

        -- Insert merchant
        INSERT INTO merchants (name, description, email, contact_person, active, user_id)
        SELECT
            merchant_names[i],
            merchant_descriptions[i],
            'merchant' || LPAD(i::TEXT, 3, '0') || '@demo.topdim.uz',
            'Владелец ' || i,
            TRUE,
            NULL
        WHERE NOT EXISTS (
            SELECT 1 FROM merchants WHERE email = 'merchant' || LPAD(i::TEXT, 3, '0') || '@demo.topdim.uz'
        );

        SELECT id INTO m_id FROM merchants
            WHERE email = 'merchant' || LPAD(i::TEXT, 3, '0') || '@demo.topdim.uz';

        IF m_id IS NULL THEN
            CONTINUE;
        END IF;

        -- Primary location
        INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
        SELECT m_id, 'Основной филиал', addresses[i], phones[i], 'Пн-Вс: 09:00–22:00',
               41.2995 + (random() * 0.05 - 0.025), 69.2401 + (random() * 0.05 - 0.025),
               TRUE, TRUE
        WHERE NOT EXISTS (
            SELECT 1 FROM merchant_locations WHERE merchant_id = m_id AND is_primary = TRUE
        );

        -- Extra branches for ~40% of merchants
        IF i <= 20 THEN
            INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
            SELECT m_id, 'Филиал №2', 'ул. Навои ' || (i * 3)::TEXT, phones[i], 'Пн-Сб: 10:00–21:00',
                   41.3100 + (random() * 0.03), 69.2600 + (random() * 0.03),
                   FALSE, TRUE
            WHERE NOT EXISTS (
                SELECT 1 FROM merchant_locations WHERE merchant_id = m_id AND title = 'Филиал №2'
            );
        END IF;

        IF i <= 10 THEN
            INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
            SELECT m_id, 'Филиал №3', 'пр. Мустакиллик ' || (i * 5)::TEXT, phones[i], 'Пн-Пт: 08:00–20:00',
                   41.2800 + (random() * 0.02), 69.2200 + (random() * 0.02),
                   FALSE, TRUE
            WHERE NOT EXISTS (
                SELECT 1 FROM merchant_locations WHERE merchant_id = m_id AND title = 'Филиал №3'
            );
        END IF;

        -- Title offset for this category (0-based: cat_idx 1 → offset 0, cat_idx 2 → offset 10, etc.)
        title_offset := (cat_idx - 1) * 10;

        -- 10 coupon offers per merchant
        FOR j IN 1..10 LOOP
            -- Status distribution: 7 ACTIVE, 1 DRAFT, 1 PAUSED, 1 ARCHIVED
            offer_status := CASE
                WHEN j <= 7 THEN 'ACTIVE'
                WHEN j = 8 THEN 'DRAFT'
                WHEN j = 9 THEN 'PAUSED'
                ELSE 'ARCHIVED'
            END;

            offer_title := merchant_names[i] || ' — ' || coupon_titles_flat[title_offset + j];

            offer_desc := '## ' || coupon_titles_flat[title_offset + j] || E'\n\n'
                || 'Воспользуйтесь скидкой от **' || merchant_names[i] || '**!' || E'\n\n'
                || '### Условия' || E'\n'
                || '- 1 купон = 1 визит' || E'\n'
                || '- Действует каждый день' || E'\n'
                || '- Необходимо предъявить купон до заказа' || E'\n\n'
                || '### Как использовать' || E'\n'
                || '1. Покажите купон сотруднику' || E'\n'
                || '2. Получите услугу или товар' || E'\n'
                || '3. Наслаждайтесь!';

            old_p := (50000 + j * 10000 + (cat_idx * 5000))::DECIMAL;
            disc := 20 + (j * 5);
            IF disc > 70 THEN disc := 70; END IF;
            from_p := old_p * (100 - disc) / 100;

            IF offer_status = 'ACTIVE' THEN
                buy_dt := NOW() + INTERVAL '30 days' + (j * INTERVAL '7 days');
                use_dt := NOW() + INTERVAL '90 days' + (j * INTERVAL '7 days');
            ELSE
                buy_dt := NOW() - INTERVAL '1 day';
                use_dt := NOW() + INTERVAL '30 days';
            END IF;

            -- Real photos via picsum.photos (deterministic by seed string)
            cover_url := 'https://picsum.photos/seed/topdim-c' || cat_idx || 'm' || i || 'j' || j || '/600/400';
            img1_url  := 'https://picsum.photos/seed/topdim-c' || cat_idx || 'm' || i || 'j' || j || 'a/800/600';
            img2_url  := 'https://picsum.photos/seed/topdim-c' || cat_idx || 'm' || i || 'j' || j || 'b/800/600';

            -- Insert coupon offer
            INSERT INTO coupon_offers (
                title, offer_description, merchant_id, category_id,
                old_price, from_price, discount_percent, cover_image_url,
                buy_until, use_until, is_gift_available, status, total_sold, view_count
            )
            SELECT
                offer_title, offer_desc, m_id, cat_id,
                old_p, from_p, disc, cover_url,
                buy_dt, use_dt,
                (j % 3 = 0),
                offer_status, 0, 0
            WHERE NOT EXISTS (
                SELECT 1 FROM coupon_offers WHERE merchant_id = m_id AND title = offer_title
            );

            SELECT id INTO offer_id FROM coupon_offers WHERE merchant_id = m_id AND title = offer_title;

            IF offer_id IS NULL THEN
                CONTINUE;
            END IF;

            -- Options (1-3 per offer) — options always use CouponOptionStatus (ACTIVE/SOLD_OUT/DISABLED)
            INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
            SELECT offer_id, 'Базовый', old_p, from_p, 100, 0, 'ACTIVE'
            WHERE NOT EXISTS (
                SELECT 1 FROM coupon_options WHERE coupon_offer_id = offer_id AND title = 'Базовый'
            );

            IF j <= 7 THEN
                INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
                SELECT offer_id, 'VIP', old_p * 1.5, from_p * 1.3, 50, 0, 'ACTIVE'
                WHERE NOT EXISTS (
                    SELECT 1 FROM coupon_options WHERE coupon_offer_id = offer_id AND title = 'VIP'
                );
            END IF;

            IF j <= 3 THEN
                INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
                SELECT offer_id, 'Премиум', old_p * 2, from_p * 1.6, 20, 0, 'ACTIVE'
                WHERE NOT EXISTS (
                    SELECT 1 FROM coupon_options WHERE coupon_offer_id = offer_id AND title = 'Премиум'
                );
            END IF;

            -- Images (real photos, 2-3 per offer)
            INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
            SELECT offer_id, img1_url, 1
            WHERE NOT EXISTS (
                SELECT 1 FROM coupon_images WHERE coupon_offer_id = offer_id AND sort_order = 1
            );

            INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
            SELECT offer_id, img2_url, 2
            WHERE NOT EXISTS (
                SELECT 1 FROM coupon_images WHERE coupon_offer_id = offer_id AND sort_order = 2
            );

            IF j <= 5 THEN
                INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
                SELECT offer_id, 'https://picsum.photos/seed/topdim-c' || cat_idx || 'm' || i || 'j' || j || 'c/800/600', 3
                WHERE NOT EXISTS (
                    SELECT 1 FROM coupon_images WHERE coupon_offer_id = offer_id AND sort_order = 3
                );
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- =============================================
-- 3. Verification queries
-- =============================================
SELECT 'Coupon seed v2 done' AS status;
SELECT COUNT(*) AS demo_merchants FROM merchants WHERE email LIKE '%@demo.topdim.uz';
SELECT COUNT(*) AS demo_locations FROM merchant_locations ml JOIN merchants m ON ml.merchant_id = m.id WHERE m.email LIKE '%@demo.topdim.uz';
SELECT COUNT(*) AS demo_coupons FROM coupon_offers co JOIN merchants m ON co.merchant_id = m.id WHERE m.email LIKE '%@demo.topdim.uz';
SELECT co.status, COUNT(*)
FROM coupon_offers co JOIN merchants m ON co.merchant_id = m.id
WHERE m.email LIKE '%@demo.topdim.uz'
GROUP BY co.status ORDER BY co.status;

-- Per-category breakdown
SELECT c.name AS category, COUNT(*) AS coupons
FROM coupon_offers co
JOIN merchants m ON co.merchant_id = m.id
JOIN categories c ON co.category_id = c.id
WHERE m.email LIKE '%@demo.topdim.uz'
GROUP BY c.name
ORDER BY coupons DESC;
