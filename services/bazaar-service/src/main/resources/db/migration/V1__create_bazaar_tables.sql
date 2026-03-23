CREATE TABLE shop_categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    name_uz     VARCHAR(100),
    slug        VARCHAR(100),
    icon_url    VARCHAR(500),
    active      BOOLEAN DEFAULT TRUE
);

CREATE TABLE bazaars (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    name_uz         VARCHAR(255),
    type            VARCHAR(30) NOT NULL,
    address         VARCHAR(500),
    city            VARCHAR(100),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    description     TEXT,
    cover_image_url VARCHAR(500),
    working_hours   VARCHAR(255),
    phone           VARCHAR(50),
    active          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE bazaar_maps (
    id              BIGSERIAL PRIMARY KEY,
    bazaar_id       BIGINT NOT NULL REFERENCES bazaars(id) ON DELETE CASCADE,
    floor_number    INT DEFAULT 1,
    floor_name      VARCHAR(100),
    map_image_url   VARCHAR(500) NOT NULL,
    map_svg_url     VARCHAR(500),
    zones_json      TEXT
);

CREATE TABLE shops (
    id                      BIGSERIAL PRIMARY KEY,
    bazaar_id               BIGINT NOT NULL REFERENCES bazaars(id) ON DELETE CASCADE,
    name                    VARCHAR(255) NOT NULL,
    row_number              VARCHAR(50),
    shop_number             VARCHAR(50),
    category_id             BIGINT REFERENCES shop_categories(id),
    goods_description       TEXT,
    working_hours           VARCHAR(255),
    phone                   VARCHAR(50),
    photo_url               VARCHAR(500),
    has_coupon              BOOLEAN DEFAULT FALSE,
    linked_coupon_offer_id  BIGINT,
    floor_number            INT DEFAULT 1,
    zone_id                 VARCHAR(50),
    active                  BOOLEAN DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE shop_product_tags (
    id          BIGSERIAL PRIMARY KEY,
    shop_id     BIGINT NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    tag         VARCHAR(100) NOT NULL,
    tag_uz      VARCHAR(100)
);

-- Indexes
CREATE INDEX idx_bazaars_city ON bazaars(city);
CREATE INDEX idx_bazaars_active ON bazaars(active);
CREATE INDEX idx_shops_bazaar ON shops(bazaar_id);
CREATE INDEX idx_shops_category ON shops(category_id);
CREATE INDEX idx_shops_has_coupon ON shops(has_coupon);
CREATE INDEX idx_shop_tags_shop ON shop_product_tags(shop_id);
CREATE INDEX idx_shop_tags_tag ON shop_product_tags(tag);

-- Seed shop categories
INSERT INTO shop_categories (name, name_uz, slug, active) VALUES
    ('Одежда', 'Kiyim-kechak', 'clothing', TRUE),
    ('Обувь', 'Oyoq kiyim', 'footwear', TRUE),
    ('Электроника', 'Elektronika', 'electronics', TRUE),
    ('Продукты', 'Oziq-ovqat', 'groceries', TRUE),
    ('Бытовая техника', 'Maishiy texnika', 'appliances', TRUE),
    ('Текстиль', 'Tekstil', 'textile', TRUE),
    ('Ювелирные изделия', 'Zargarlik buyumlari', 'jewelry', TRUE),
    ('Специи и приправы', 'Ziravorlar', 'spices', TRUE),
    ('Мясо и рыба', 'Go''sht va baliq', 'meat-fish', TRUE),
    ('Фрукты и овощи', 'Meva va sabzavotlar', 'fruits-vegetables', TRUE);
