-- Categories
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    name_uz     VARCHAR(100),
    slug        VARCHAR(100),
    icon_url    VARCHAR(500),
    sort_order  INT DEFAULT 0,
    active      BOOLEAN DEFAULT TRUE
);

-- Merchants (Partners)
CREATE TABLE merchants (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    logo_url        VARCHAR(500),
    cover_url       VARCHAR(500),
    address         VARCHAR(500),
    phone           VARCHAR(50),
    email           VARCHAR(255),
    website         VARCHAR(255),
    working_hours   VARCHAR(255),
    contact_person  VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    user_id         BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Coupon Offers
CREATE TABLE coupon_offers (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(500) NOT NULL,
    short_description   VARCHAR(1000),
    full_description    TEXT,
    merchant_id         BIGINT NOT NULL REFERENCES merchants(id),
    category_id         BIGINT NOT NULL REFERENCES categories(id),
    old_price           DECIMAL(12, 2),
    from_price          DECIMAL(12, 2),
    discount_percent    INT,
    cover_image_url     VARCHAR(500),
    buy_until           TIMESTAMP,
    use_until           TIMESTAMP,
    terms               TEXT,
    usage_rules         TEXT,
    how_to_use          TEXT,
    address             VARCHAR(500),
    contact_phone       VARCHAR(50),
    working_hours       VARCHAR(255),
    is_gift_available   BOOLEAN DEFAULT FALSE,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_sold          INT DEFAULT 0,
    view_count          INT DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Coupon Options (variants)
CREATE TABLE coupon_options (
    id                  BIGSERIAL PRIMARY KEY,
    coupon_offer_id     BIGINT NOT NULL REFERENCES coupon_offers(id) ON DELETE CASCADE,
    title               VARCHAR(500) NOT NULL,
    regular_price       DECIMAL(12, 2) NOT NULL,
    coupon_price        DECIMAL(12, 2) NOT NULL,
    quantity_limit      INT,
    quantity_sold       INT DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

-- Coupon Images
CREATE TABLE coupon_images (
    id                  BIGSERIAL PRIMARY KEY,
    coupon_offer_id     BIGINT NOT NULL REFERENCES coupon_offers(id) ON DELETE CASCADE,
    image_url           VARCHAR(500) NOT NULL,
    sort_order          INT DEFAULT 0
);

-- Indexes
CREATE INDEX idx_coupon_offers_status ON coupon_offers(status);
CREATE INDEX idx_coupon_offers_category ON coupon_offers(category_id);
CREATE INDEX idx_coupon_offers_merchant ON coupon_offers(merchant_id);
CREATE INDEX idx_coupon_options_offer ON coupon_options(coupon_offer_id);

-- Seed default categories
INSERT INTO categories (name, name_uz, slug, icon_url, sort_order, active) VALUES
    ('Еда и напитки', 'Ovqat va ichimliklar', 'food', NULL, 1, TRUE),
    ('Beauty', 'Go''zallik', 'beauty', NULL, 2, TRUE),
    ('Развлечения', 'Ko''ngilochar', 'entertainment', NULL, 3, TRUE),
    ('Здоровье и спорт', 'Salomatlik va sport', 'health-sport', NULL, 4, TRUE),
    ('Услуги', 'Xizmatlar', 'services', NULL, 5, TRUE),
    ('Сертификаты и подарки', 'Sertifikatlar va sovg''alar', 'gifts', NULL, 6, TRUE);
