-- =============================================================================
-- Coupon Service Demo Seed Data (v3 — Realistic Tashkent Businesses)
-- Real merchants, addresses, prices in UZS based on 2025 market data.
-- 50 merchants × 10 coupons = 500 coupon offers.
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
-- 2. Merchants + Locations + Coupons (50 real Tashkent businesses)
-- =============================================
DO $$
DECLARE
    cat_slugs TEXT[] := ARRAY[
        'restaurants', 'coffee', 'beauty', 'spa', 'fitness',
        'entertainment', 'education', 'services', 'shops'
    ];

    -- Real Tashkent merchant names (50)
    merchant_names TEXT[] := ARRAY[
        -- 1-6: Restaurants
        'Caravan', 'Besh Qozon', 'Afsona', 'Сыроварня', 'Kaspiyka', 'Gruzinsky Dvorik',
        -- 7-12: Coffee
        'Coffee Nation', 'Shavi Coffee Roasters', 'Tim''s Coffee & Roastery', 'Beanberry Coffee', 'Bon! Patisserie', 'Rassvet',
        -- 13-18: Beauty
        'Santander Barberia', 'Bosco Beauty Club', 'Hide Beauty', 'Express Beauty', 'Barbados', 'Level Salon',
        -- 19-23: SPA
        'Manor SPA', 'Diamond SPA', 'Sheikh SPA', 'NOMA SPA', 'Serenity SPA',
        -- 24-29: Fitness
        'BeFit PRO', 'BeFit ONE', 'Ozone Fitness', 'Beauty & More Fitness', 'Magic Galaxy', 'Life Fitness Elit',
        -- 30-34: Entertainment
        'Galaxy Bowling', 'Quest Portal', 'ENT.TER', 'Aqualand', 'Joy Bowling',
        -- 35-40: Education
        'Najot Ta''lim', 'PDP Academy', 'IT STEP Academy', 'Cambridge Learning Centre', 'King''s Academy', 'Ilmhub',
        -- 41-45: Services
        'Family Studio Photography', 'Liga Vet', 'Garant Service', 'TeleService', '2A Studio',
        -- 46-50: Shops
        'Texnoport', 'Toshkent Gullari', 'Sarmoya Books', 'DI Sport', 'Sportway'
    ];

    merchant_descriptions TEXT[] := ARRAY[
        -- Restaurants
        'Один из первых и наиболее знаковых узбекских ресторанов Ташкента. Сочетает атмосферу восточного дома, богатый интерьер и аутентичную кухню с элементами кухонь мира.',
        'Культовое место для знакомства с самым знаменитым узбекским блюдом — пловом. Готовят в огромных казанах по традиционным рецептам. Славится аутентичностью и скоростью обслуживания.',
        'Ресторан современной узбекской кухни, где древние кулинарные традиции адаптированы под современный ритм жизни. Открытая кухня позволяет наблюдать за процессом приготовления.',
        'Зелёный оазис в центре города с собственным производством сыра. Итальянская и европейская кухня, выбор экспертов Ultima Guide 2025.',
        'Рыбный ресторан с акцентом на морепродукты: сибас, устрицы, креветки. Можно выбрать способ приготовления свежего улова прямо на месте.',
        'Популярный ресторан грузинской кухни в Ташкенте. Славится большими порциями, гостеприимством и национальным колоритом. Хачапури, хинкали, шашлыки.',
        -- Coffee
        'Спешелти-кофейня с собственной обжаркой и большим выбором сортов. Brew-зона для самостоятельных экспериментов с разными методами заваривания.',
        'Сеть кофеен с собственным производством обжарки. Зёрна из Индонезии, Эфиопии, Колумбии. Один из лидеров specialty coffee в Ташкенте.',
        'Минималистичная кофейня с большим выбором напитков на зёрнах из Эфиопии, Гондураса, Перу и Колумбии. Просторное место, идеальное для работы.',
        'Кофе-бутик с широкой кофейной картой. Популярен среди ценителей как классических напитков, так и альтернативных методов заваривания.',
        'Известная сеть французских кофеен с разнообразным выбором десертов и классических кофейных напитков. Изысканная выпечка и круассаны собственного производства.',
        'Пет-френдли кофейня с завтраками до 16:00. Интересное авторское меню и уютная атмосфера в центре Ташкента.',
        -- Beauty
        'Премиальная сеть барбершопов с очень высокими рейтингами. Мужские стрижки, оформление бороды, королевское бритьё, уход за лицом.',
        'Салон красоты и ногтевая студия. Полный спектр услуг: маникюр, педикюр, уход за лицом, брови и ресницы.',
        'Специализированный салон красоты в центре: пилочный и аппаратный маникюр, педикюр, лазерная эпиляция, оформление бровей.',
        'Сеть салонов красоты для женщин. Маникюр, педикюр, укладки, окрашивание. Филиалы в крупнейших ТРЦ Ташкента.',
        'Лучший барбершоп 2025 по версии 2ГИС. Мужские стрижки, оформление бороды, мужской маникюр и уход.',
        'Салон красоты премиум-класса: макияж, укладки, окрашивание, уход за волосами. Стильный интерьер в центре города.',
        -- SPA
        'Премиальный SPA-центр: турецкий хаммам, финская сауна, бассейн, авторские массажи. Скрабирование и комплексные SPA-программы.',
        'Круглосуточный SPA: турецкая баня, пенный массаж, скрабирование, кедровая фитобочка. Три филиала по всему Ташкенту.',
        'Элитный SPA с пакетными предложениями. Сауна, бассейн, пилинг тела, классический и оздоровительный массаж.',
        'Премиальный SPA-центр с акцентом на авторских ритуалах и приватности. Индивидуальный подход к каждому гостю.',
        'Уютный SPA-центр с атмосферой спокойствия. Массаж, ароматерапия, SPA для двоих. Идеально для подарочных сертификатов.',
        -- Fitness
        'Премиум-фитнес клуб: тренажёрный зал, бассейн, йога, пилатес, сайклинг, единоборства и SPA-зона. Рядом с Humo Arena.',
        'Современный фитнес-клуб с комплексным подходом: зал, групповые программы, бассейн, SPA. Район Новомосковская.',
        'Популярная сеть фитнес-клубов с современным оснащением. SPA, джакузи, консультации нутрициологов, разные тарифы.',
        'Фитнес-центр с большим набором активностей: йога, пилатес, бассейн, SPA, танцы. Рядом с метро Айбек.',
        'Тренажёрный зал, бассейн, йога, массаж, стрип-пластика. Чиланзарский район.',
        'Услуги бассейна, йоги, тренажёрного зала и шейпинга. Шайхантахурский район.',
        -- Entertainment
        '15 дорожек, релакс-зона и полноценный ресторан. Самый крупный боулинг-клуб Ташкента на Нукусской.',
        'Широкий выбор хоррор-перформансов, детективных и командных квестов. Квесты с актёрами и спецэффектами.',
        'Хорроры, командные и соревновательные квесты в парке Anhor Lokomotiv. Новый формат развлечений для компаний.',
        'Большой аквапарк в Юнусабадском районе. Горки, бассейны, зоны отдыха для всей семьи.',
        'Боулинг-центр в центре города с рестораном и баром. Дорожки профессионального уровня.',
        -- Education
        'Ведущая IT-школа Узбекистана. Интенсивные буткемпы по программированию, дизайну и маркетингу с гарантией трудоустройства.',
        'IT-академия с фокусом на трудоустройство. Java, Python, Frontend (React), DevOps. Один из крупнейших IT-хабов Центральной Азии.',
        'Профессиональное IT-образование для взрослых и детей. Курсы по программированию, дизайну, робототехнике. Летние лагеря.',
        'Языковой центр: английский, немецкий, корейский, арабский. Подготовка к IELTS и TOEFL с носителями языка.',
        'Сеть учебных центров: английский и другие языки, подготовка к экзаменам, детские программы. Несколько филиалов.',
        'AI Bootcamp, Flutter, Python, робототехника. Современные технологические курсы для молодёжи.',
        -- Services
        'Профессиональная фотостудия с несколькими залами. Семейные, детские и портретные съёмки. Циклорамы и тематические интерьеры.',
        'Ветеринарная клиника полного цикла. Вакцинация, УЗИ, лабораторные анализы, хирургия, стационар. Круглосуточно.',
        'Сервисный центр по ремонту бытовой и цифровой техники. Смартфоны, ноутбуки, бытовая техника. Гарантия на все работы.',
        'Авторизованный сервисный центр электроники. Профессиональный ремонт телефонов, планшетов, ноутбуков.',
        'Фотостудия с несколькими залами и интерьерами. Аренда залов, услуги фотографа, ретушь, печать фотографий.',
        -- Shops
        'Крупный магазин электроники в Tashkent City Mall. Гаджеты, смартфоны, аксессуары. Лучшие цены и рассрочка.',
        'Одна из самых известных сетей цветочных магазинов Ташкента. Букеты, комнатные растения, доставка по городу.',
        'Книжный магазин с широким ассортиментом: художественная литература, учебники, канцелярия. Несколько филиалов.',
        'Магазин спортивной одежды и обуви. Бренды для профессионального и любительского спорта. Филиалы в ТРЦ.',
        'Спортивная одежда и обувь в самом центре Ташкента. Nike, Adidas, Under Armour и другие бренды.'
    ];

    -- Real Tashkent addresses
    addresses TEXT[] := ARRAY[
        -- Restaurants
        'ул. А. Каххара, 22', 'ул. Ифтихор, 1', 'ул. Мирабад, 15', 'ул. Шахрисабз, 31Б', 'ТРЦ Tashkent City Mall, ул. Б. Закирова, 7', 'ул. Навои, 48',
        -- Coffee
        'ул. Афросиаб, 14/1', 'ул. Бухара, 26', 'ул. Тараса Шевченко, 40', 'ул. Махатмы Ганди, 14', 'ул. Усмона Насыра, 63', 'ул. Чимкентская, 17',
        -- Beauty
        'ул. Фидокор, 10', 'ул. Эльбека, 15', 'ул. Амира Темура, 55', 'ул. Мирабад, 12', 'Алайский массив, 16', 'ул. Тараса Шевченко, 21',
        -- SPA
        'ул. Тараса Шевченко, 23', 'ул. Амира Темура, 122В', 'ул. Юсуф Хос Хожиб, 58', 'ул. Навои, 32', 'ул. Укчи, 1',
        -- Fitness
        'ул. Бешагач, 10Б', 'ул. Осиё, 1Б', 'ул. Мирабад, 24', 'ул. Ойбека, 18', 'ул. Заргарлик, 46А', 'ул. А. Навои, 7',
        -- Entertainment
        'ул. Нукусская, 83А', 'ул. Бабура, 73А', 'ул. Лабзак, 12/1', 'пр. Чинабад, 61А', 'ул. Ойбека, 24',
        -- Education
        'ул. Ширин, 13Б', 'ул. Каландар, 2', 'ул. Афросиаб, 8А', 'ул. Фурката, м-в Алмазар, 7', 'ул. Буюк Ипак Йули, 77', 'ул. Фурката, 2',
        -- Services
        'ул. Фирдавсий, 19', 'ул. Абдурауфа Фитрата, 59', 'ул. Бабура, 40А', 'ул. Амира Темура, 100', 'ул. Бабура, 73Б',
        -- Shops
        'ТРЦ Tashkent City Mall', 'ул. Бухара, 26а', 'ТЦ Seoul Mun', 'ТРЦ Tashkent City Mall, 2 этаж', 'ул. Шахрисабзская, 3'
    ];

    phones TEXT[] := ARRAY[
        '+998712000101', '+998712000102', '+998712000103', '+998712000104', '+998712000105', '+998712000106',
        '+998712000201', '+998712000202', '+998712000203', '+998712000204', '+998712000205', '+998712000206',
        '+998712000301', '+998712000302', '+998712000303', '+998712000304', '+998712000305', '+998712000306',
        '+998712000401', '+998712000402', '+998712000403', '+998712000404', '+998712000405',
        '+998712000501', '+998712000502', '+998712000503', '+998712000504', '+998712000505', '+998712000506',
        '+998712000601', '+998712000602', '+998712000603', '+998712000604', '+998712000605',
        '+998712000701', '+998712000702', '+998712000703', '+998712000704', '+998712000705', '+998712000706',
        '+998712000801', '+998712000802', '+998712000803', '+998712000804', '+998712000805',
        '+998712000901', '+998712000902', '+998712000903', '+998712000904', '+998712000905'
    ];

    -- Category-specific coupon titles (10 per category = 90 total)
    -- Realistic offers that would appear on a coupon/deals platform
    coupon_titles_flat TEXT[] := ARRAY[
        -- restaurants (cat 1, offsets 0-9) — realistic UZS deals
        'Ужин на двоих с напитками', 'Бизнес-ланч на неделю (5 дней)', 'Банкет на 10 персон',
        'Романтический ужин при свечах', 'Семейный бранч на 4 персоны', 'Завтрак + кофе каждый день (месяц)',
        'Дегустационный сет из 7 блюд', 'Шеф-стол на 6 персон', 'Доставка обедов на неделю', 'Праздничное меню на 8 гостей',
        -- coffee (cat 2, offsets 10-19)
        'Кофе + авторский десерт', 'Абонемент на кофе (30 дней)', 'Мастер-класс по латте-арту',
        'Бранч на двоих + фильтр-кофе', 'Набор зерна (3 сорта × 250г)', 'Кофе-брейк для офиса (10 чел)',
        'Чайная церемония на двоих', 'Абонемент «Завтрак в кофейне» (10 визитов)', 'Холодные напитки — сет из 4', 'Кофейный подарочный набор',
        -- beauty (cat 3, offsets 20-29)
        'Мужская стрижка + оформление бороды', 'Маникюр + педикюр с покрытием', 'Комплекс «Полный уход за лицом»',
        'Окрашивание волос + уходовая процедура', 'Ламинирование бровей + ресниц', 'SPA-маникюр + парафинотерапия',
        'Свадебный образ (макияж + причёска)', 'Абонемент на стрижку (3 визита)', 'Лазерная эпиляция — зона бикини', 'Комплекс «День красоты»',
        -- spa (cat 4, offsets 30-39)
        'Классический массаж 60 мин', 'Хаммам + пилинг + массаж', 'SPA-день (полная программа)',
        'SPA для двоих — романтик', 'Тайский массаж 90 мин', 'Антистресс-программа (2 часа)',
        'Кедровая фитобочка + массаж', 'Обёртывание + массаж лица', 'Абонемент на массаж (5 сеансов)', 'Пенный массаж + скрабирование',
        -- fitness (cat 5, offsets 40-49)
        'Абонемент на месяц (безлимит)', 'Персональные тренировки (10 занятий)', 'Йога-курс (8 занятий)',
        'Бассейн — безлимитный месяц', 'Пробная неделя (все зоны)', 'Групповые занятия (месяц)',
        'Фитнес + SPA (2 месяца)', 'Бокс / MMA — 10 тренировок', 'Пилатес-курс (12 занятий)', 'Персональная программа + нутрициолог',
        -- entertainment (cat 6, offsets 50-59)
        'Боулинг 2 часа на 4 персоны', 'Квест-перформанс для компании (6 чел)', 'Аквапарк — семейный билет (4 чел)',
        'Караоке VIP-кабинка 3 часа', 'Лазертаг — 1 час на 8 человек', 'Детский день рождения (10 детей)',
        'Корпоративный боулинг (20 чел)', 'Батуты — безлимитный день', 'VR-сеанс 45 минут на двоих', 'Картинг 30 мин — 4 заезда',
        -- education (cat 7, offsets 60-69)
        'Python-курс (3 месяца)', 'Английский IELTS (2 месяца)', 'Frontend React-буткемп (6 недель)',
        'Подготовка к экзаменам — 1 месяц', 'Робототехника для детей (8 занятий)', 'Мастер-класс по фотографии',
        'Java-интенсив выходного дня (8 нед)', 'Flutter-курс мобильной разработки', 'Корейский язык (1 месяц)', 'Графический дизайн (Figma, 2 мес)',
        -- services (cat 8, offsets 70-79)
        'Семейная фотосессия 1.5 часа', 'Осмотр + вакцинация питомца', 'Ремонт экрана iPhone',
        'Видеосъёмка события (4 часа)', 'Аренда студии + фотограф (2 часа)', 'Комплексная чистка ноутбука',
        'Стрижка и груминг собаки', 'Печать фотокниги (40 стр)', 'УЗИ-диагностика питомца', 'Портретная фотосессия в студии',
        -- shops (cat 9, offsets 80-89)
        'Сертификат на электронику 500K', 'Букет из 51 розы с доставкой', 'Набор книг — 5 бестселлеров',
        'Кроссовки Nike/Adidas со скидкой', 'Подарочный набор косметики', 'Сертификат на спортивную одежду',
        'Комнатное растение + кашпо', 'Рюкзак для активного отдыха', 'Набор для фитнеса (коврик + резинки + гантели)', 'Букет цветов «Премиум» с доставкой'
    ];

    -- Realistic prices per category: [old_price_base, discount_range_min, discount_range_max]
    -- All in UZS (thousands), based on 2025 Tashkent market research
    cat_old_prices INT[] := ARRAY[
        -- restaurants: 150K-800K
        400, 350, 2500, 500, 600, 450, 700, 900, 350, 1200,
        -- coffee: 50K-200K
        80, 600, 250, 200, 180, 350, 150, 500, 120, 200,
        -- beauty: 150K-500K
        200, 450, 400, 500, 300, 350, 800, 350, 400, 700,
        -- spa: 300K-1500K
        500, 800, 1500, 1200, 980, 900, 700, 600, 2000, 690,
        -- fitness: 500K-4500K
        1500, 2000, 740, 1000, 300, 800, 3500, 1200, 900, 2500,
        -- entertainment: 100K-600K
        400, 350, 500, 300, 250, 600, 800, 200, 250, 300,
        -- education: 500K-3000K
        2500, 2000, 3000, 1500, 800, 300, 2000, 2500, 1000, 2000,
        -- services: 100K-500K
        400, 200, 350, 500, 300, 150, 250, 300, 250, 300,
        -- shops: 200K-1500K
        500, 600, 350, 800, 400, 500, 200, 350, 300, 700
    ];

    i INT;
    j INT;
    m_id BIGINT;
    cat_id BIGINT;
    cat_idx INT;
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
    price_idx INT;
BEGIN
    FOR i IN 1..50 LOOP
        -- Category mapping (same as v2 for compatibility)
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

        SELECT id INTO cat_id FROM categories WHERE slug = cat_slugs[cat_idx] LIMIT 1;
        IF cat_id IS NULL THEN
            SELECT id INTO cat_id FROM categories WHERE active = TRUE ORDER BY id LIMIT 1;
        END IF;

        -- Insert merchant
        INSERT INTO merchants (name, description, email, contact_person, active, user_id)
        SELECT
            merchant_names[i],
            merchant_descriptions[i],
            'merchant' || LPAD(i::TEXT, 3, '0') || '@demo.topdim.uz',
            'Администратор',
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

        -- Primary location (real address)
        INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
        SELECT m_id, 'Основной', addresses[i], phones[i], 'Пн-Вс: 09:00–22:00',
               41.2995 + (random() * 0.05 - 0.025), 69.2401 + (random() * 0.05 - 0.025),
               TRUE, TRUE
        WHERE NOT EXISTS (
            SELECT 1 FROM merchant_locations WHERE merchant_id = m_id AND is_primary = TRUE
        );

        -- Extra branches for first 20 merchants
        IF i <= 20 THEN
            INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
            SELECT m_id, 'Филиал №2', 'ул. Навои, ' || (i * 3 + 10)::TEXT, phones[i], 'Пн-Сб: 10:00–21:00',
                   41.3100 + (random() * 0.03), 69.2600 + (random() * 0.03),
                   FALSE, TRUE
            WHERE NOT EXISTS (
                SELECT 1 FROM merchant_locations WHERE merchant_id = m_id AND title = 'Филиал №2'
            );
        END IF;

        IF i <= 10 THEN
            INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, latitude, longitude, is_primary, active)
            SELECT m_id, 'Филиал №3', 'пр. Мустакиллик, ' || (i * 5 + 20)::TEXT, phones[i], 'Пн-Пт: 08:00–20:00',
                   41.2800 + (random() * 0.02), 69.2200 + (random() * 0.02),
                   FALSE, TRUE
            WHERE NOT EXISTS (
                SELECT 1 FROM merchant_locations WHERE merchant_id = m_id AND title = 'Филиал №3'
            );
        END IF;

        title_offset := (cat_idx - 1) * 10;

        -- 10 coupon offers per merchant
        FOR j IN 1..10 LOOP
            offer_status := CASE
                WHEN j <= 7 THEN 'ACTIVE'
                WHEN j = 8 THEN 'DRAFT'
                WHEN j = 9 THEN 'PAUSED'
                ELSE 'ARCHIVED'
            END;

            offer_title := merchant_names[i] || ' — ' || coupon_titles_flat[title_offset + j];

            offer_desc := '## ' || coupon_titles_flat[title_offset + j] || E'\n\n'
                || 'Воспользуйтесь выгодным предложением от **' || merchant_names[i] || '**!' || E'\n\n'
                || '📍 ' || addresses[i] || E'\n\n'
                || '### Что входит' || E'\n'
                || '- ' || coupon_titles_flat[title_offset + j] || E'\n'
                || '- Действует каждый день в часы работы заведения' || E'\n'
                || '- 1 купон = 1 визит (или указанный объём услуги)' || E'\n\n'
                || '### Как использовать' || E'\n'
                || '1. Покажите купон сотруднику **до** начала обслуживания' || E'\n'
                || '2. Назовите код или покажите QR из приложения' || E'\n'
                || '3. Получите услугу и наслаждайтесь!' || E'\n\n'
                || '> ⚠️ Купон не суммируется с другими акциями. Возврат невозможен после активации.';

            -- Realistic price from category-specific array
            price_idx := title_offset + j;
            old_p := cat_old_prices[price_idx] * 1000;  -- convert to UZS
            disc := 20 + ((j * 7) % 35);  -- 20-55% discount
            IF disc > 55 THEN disc := 55; END IF;
            from_p := old_p * (100 - disc) / 100;

            IF offer_status = 'ACTIVE' THEN
                buy_dt := NOW() + INTERVAL '30 days' + (j * INTERVAL '7 days');
                use_dt := NOW() + INTERVAL '90 days' + (j * INTERVAL '7 days');
            ELSE
                buy_dt := NOW() - INTERVAL '1 day';
                use_dt := NOW() + INTERVAL '30 days';
            END IF;

            -- Deterministic photos via picsum.photos
            cover_url := 'https://picsum.photos/seed/td-c' || cat_idx || 'm' || i || 'j' || j || '/600/400';
            img1_url  := 'https://picsum.photos/seed/td-c' || cat_idx || 'm' || i || 'j' || j || 'a/800/600';
            img2_url  := 'https://picsum.photos/seed/td-c' || cat_idx || 'm' || i || 'j' || j || 'b/800/600';

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
            IF offer_id IS NULL THEN CONTINUE; END IF;

            -- Options: Базовый always, VIP for first 7, Премиум for first 3
            INSERT INTO coupon_options (coupon_offer_id, title, regular_price, coupon_price, quantity_limit, quantity_sold, status)
            SELECT offer_id, 'Стандарт', old_p, from_p, 100, 0, 'ACTIVE'
            WHERE NOT EXISTS (
                SELECT 1 FROM coupon_options WHERE coupon_offer_id = offer_id AND title = 'Стандарт'
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

            -- Images (2-3 per offer)
            INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
            SELECT offer_id, img1_url, 1
            WHERE NOT EXISTS (SELECT 1 FROM coupon_images WHERE coupon_offer_id = offer_id AND sort_order = 1);

            INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
            SELECT offer_id, img2_url, 2
            WHERE NOT EXISTS (SELECT 1 FROM coupon_images WHERE coupon_offer_id = offer_id AND sort_order = 2);

            IF j <= 5 THEN
                INSERT INTO coupon_images (coupon_offer_id, image_url, sort_order)
                SELECT offer_id, 'https://picsum.photos/seed/td-c' || cat_idx || 'm' || i || 'j' || j || 'c/800/600', 3
                WHERE NOT EXISTS (SELECT 1 FROM coupon_images WHERE coupon_offer_id = offer_id AND sort_order = 3);
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- =============================================
-- 3. Verification queries
-- =============================================
SELECT 'Coupon seed v3 (realistic Tashkent) done' AS status;
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
