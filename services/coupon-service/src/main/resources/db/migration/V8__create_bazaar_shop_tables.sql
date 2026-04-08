-- ═══════════════════════════════════════════
-- V8: Создание таблиц bazaars и shops
-- ═══════════════════════════════════════════

CREATE TABLE bazaars (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    name_uz         VARCHAR(255),
    type            VARCHAR(50)     NOT NULL,
    description     TEXT,
    address         VARCHAR(500),
    city            VARCHAR(100),
    latitude        DOUBLE PRECISION NOT NULL DEFAULT 0,
    longitude       DOUBLE PRECISION NOT NULL DEFAULT 0,
    cover_image_url VARCHAR(500),
    working_hours   VARCHAR(255),
    phone           VARCHAR(50),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE shops (
    id                BIGSERIAL PRIMARY KEY,
    merchant_id       BIGINT,
    name              VARCHAR(255)    NOT NULL,
    description       TEXT,
    category          VARCHAR(100),
    subcategory       VARCHAR(100),
    goods_description TEXT,
    phone             VARCHAR(50),
    working_hours     VARCHAR(255),
    photos            TEXT,
    location_type     VARCHAR(20)     NOT NULL, -- BAZAAR | STANDALONE
    bazaar_id         BIGINT          REFERENCES bazaars(id) ON DELETE SET NULL,
    address           VARCHAR(500),
    latitude          DOUBLE PRECISION NOT NULL DEFAULT 0,
    longitude         DOUBLE PRECISION NOT NULL DEFAULT 0,
    pavilion          VARCHAR(50),
    sector            VARCHAR(50),
    row_number        VARCHAR(50),
    shop_number       VARCHAR(50),
    floor_number      INTEGER,
    status            VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_bazaars_status ON bazaars(status);
CREATE INDEX idx_bazaars_geo ON bazaars(latitude, longitude);
CREATE INDEX idx_bazaars_city ON bazaars(city);

CREATE INDEX idx_shops_status ON shops(status);
CREATE INDEX idx_shops_bazaar_id ON shops(bazaar_id);
CREATE INDEX idx_shops_category ON shops(category);
CREATE INDEX idx_shops_location_type ON shops(location_type);
CREATE INDEX idx_shops_geo ON shops(latitude, longitude);
CREATE INDEX idx_shops_merchant_id ON shops(merchant_id);

-- Seed: Ташкентские базары
INSERT INTO bazaars (name, name_uz, type, description, address, city, latitude, longitude, working_hours, phone, status) VALUES
('Чорсу базар', 'Chorsu bozori', 'BAZAAR', 'Один из крупнейших и старейших базаров Центральной Азии. Специализируется на свежих продуктах, специях, сухофруктах и текстиле.', 'ул. Навои, Старый город', 'Ташкент', 41.3265, 69.2289, '06:00 – 18:00', '+998 71 244 00 00', 'ACTIVE'),
('Алайский базар', 'Olay bozori', 'BAZAAR', 'Центральный продовольственный рынок. Фермерские продукты, мясо, молочные продукты и свежая выпечка.', 'ул. Шота Руставели', 'Ташкент', 41.3055, 69.2685, '07:00 – 17:00', '+998 71 252 11 22', 'ACTIVE'),
('Куйлюк базар', 'Quyliq bozori', 'MARKET', 'Крупнейший оптовый рынок Ташкента. Стройматериалы, бытовая техника, одежда, продукты питания.', 'Куйлюкский район', 'Ташкент', 41.2849, 69.3450, '06:00 – 18:00', '+998 71 300 44 55', 'ACTIVE'),
('Tashkent City Mall', NULL, 'SHOPPING_CENTER', 'Крупный торговый центр в деловом центре Ташкента с международными и локальными брендами.', 'Tashkent City', 'Ташкент', 41.3112, 69.2792, '10:00 – 22:00', '+998 71 200 10 20', 'ACTIVE'),
('Samarkand Darvoza', 'Samarqand darvoza', 'TRADE_COMPLEX', 'Торгово-развлекательный комплекс у ворот Самаркандского базара. Одежда, обувь, текстиль.', 'Самарканд Дарвоза', 'Ташкент', 41.3155, 69.2479, '09:00 – 21:00', '+998 71 123 00 07', 'ACTIVE'),
('Абу Сахий', 'Abu Sahiy', 'MARKET', 'Крупнейший оптово-розничный рынок. Одежда, обувь, бытовая химия, электроника.', 'Сергелийский район', 'Ташкент', 41.2558, 69.2175, '07:00 – 19:00', '+998 71 277 33 44', 'ACTIVE');

-- Seed: Магазины в базарах
INSERT INTO shops (name, description, category, goods_description, phone, working_hours, location_type, bazaar_id, latitude, longitude, pavilion, row_number, shop_number, floor_number, status) VALUES
('Специи от Мехмона', 'Лучшие специи Ферганской долины', 'Специи', 'Зира, куркума, паприка, шафран, барбарис, лавровый лист', '+998 90 111 22 33', '08:00 – 17:00', 'BAZAAR', 1, 41.3265, 69.2289, NULL, '3', '25', 1, 'ACTIVE'),
('Ткани Шёлковый путь', 'Натуральные ткани ручной работы', 'Текстиль', 'Атлас, адрас, хан-атлас, шёлк', '+998 90 222 33 44', '09:00 – 18:00', 'BAZAAR', 1, 41.3265, 69.2289, NULL, '5', '10', 2, 'ACTIVE'),
('Фрукты Ферганы', 'Свежие фрукты напрямую от фермера', 'Фрукты', 'Гранат, хурма, виноград, абрикос, черешня', '+998 90 333 44 55', '07:00 – 16:00', 'BAZAAR', 1, 41.3265, 69.2289, NULL, '1', '5', 1, 'ACTIVE'),
('Мясная лавка Алай', 'Свежее мясо каждый день', 'Мясо', 'Говядина, баранина, курица, фарш, субпродукты', '+998 90 444 55 66', '07:00 – 15:00', 'BAZAAR', 2, 41.3055, 69.2685, NULL, '2', '8', 1, 'ACTIVE'),
('Молочная ферма', 'Домашние молочные продукты', 'Молочные', 'Катык, сузма, сливочное масло, курт, творог', '+998 90 555 66 77', '08:00 – 16:00', 'BAZAAR', 2, 41.3055, 69.2685, NULL, '1', '12', 1, 'ACTIVE'),
('Электроника Плюс', 'Бытовая техника и электроника', 'Электроника', 'Телефоны, ноутбуки, наушники, аксессуары', '+998 90 666 77 88', '09:00 – 19:00', 'BAZAAR', 3, 41.2849, 69.3450, 'Павильон А', '7', '3', 1, 'ACTIVE'),
('Zara Home', 'Международный бренд домашнего текстиля', 'Дом и интерьер', 'Постельное бельё, полотенца, декор, свечи', '+998 71 200 10 25', '10:00 – 22:00', 'BAZAAR', 4, 41.3112, 69.2792, NULL, NULL, '215', 2, 'ACTIVE'),
('Samsung Brand Store', 'Официальный магазин Samsung', 'Электроника', 'Смартфоны, ТВ, бытовая техника Samsung', '+998 71 200 10 30', '10:00 – 22:00', 'BAZAAR', 4, 41.3112, 69.2792, NULL, NULL, '118', 1, 'ACTIVE');

-- Seed: Standalone магазины
INSERT INTO shops (name, description, category, goods_description, phone, working_hours, location_type, address, latitude, longitude, status) VALUES
('Korzinka Go', 'Сеть продовольственных магазинов', 'Продукты', 'Полный ассортимент продуктов питания, товары для дома', '+998 71 150 00 00', '08:00 – 23:00', 'STANDALONE', 'ул. Амира Темура, 48', 41.3111, 69.2797, 'ACTIVE'),
('Texnomart Chilanzar', 'Магазин бытовой техники и электроники', 'Электроника', 'Бытовая техника, компьютеры, смартфоны, аудиотехника', '+998 71 230 00 00', '09:00 – 21:00', 'STANDALONE', 'Чиланзар, 10 квартал', 41.2899, 69.2133, 'ACTIVE'),
('Massimo Dutti', 'Европейский бренд одежды', 'Одежда', 'Мужская и женская одежда, обувь, аксессуары', '+998 71 200 10 35', '10:00 – 22:00', 'STANDALONE', 'Tashkent City, блок C', 41.3120, 69.2801, 'ACTIVE');
