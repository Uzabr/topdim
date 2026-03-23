CREATE TABLE carts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE cart_items (
    id                  BIGSERIAL PRIMARY KEY,
    cart_id             BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    coupon_offer_id     BIGINT NOT NULL,
    coupon_option_id    BIGINT NOT NULL,
    coupon_title        VARCHAR(500),
    option_title        VARCHAR(500),
    unit_price          DECIMAL(12, 2) NOT NULL,
    quantity            INT NOT NULL DEFAULT 1,
    is_gift             BOOLEAN DEFAULT FALSE,
    gift_recipient_name VARCHAR(255),
    gift_recipient_phone VARCHAR(50)
);

CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    order_number    VARCHAR(50) NOT NULL UNIQUE,
    user_id         BIGINT NOT NULL,
    user_email      VARCHAR(255),
    user_phone      VARCHAR(50),
    total_amount    DECIMAL(12, 2) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    paid_at         TIMESTAMP
);

CREATE TABLE order_items (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    coupon_offer_id     BIGINT NOT NULL,
    coupon_option_id    BIGINT NOT NULL,
    coupon_title        VARCHAR(500),
    option_title        VARCHAR(500),
    unit_price          DECIMAL(12, 2) NOT NULL,
    quantity            INT NOT NULL DEFAULT 1,
    is_gift             BOOLEAN DEFAULT FALSE,
    gift_recipient_name VARCHAR(255),
    gift_recipient_phone VARCHAR(50)
);

CREATE TABLE purchased_coupons (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    order_id            BIGINT NOT NULL REFERENCES orders(id),
    coupon_offer_id     BIGINT NOT NULL,
    coupon_option_id    BIGINT NOT NULL,
    coupon_title        VARCHAR(500),
    option_title        VARCHAR(500),
    coupon_code         VARCHAR(50) NOT NULL UNIQUE,
    qr_token            VARCHAR(100) UNIQUE,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    purchased_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP,
    used_at             TIMESTAMP,
    is_gift             BOOLEAN DEFAULT FALSE,
    gift_recipient_name VARCHAR(255),
    gift_recipient_phone VARCHAR(50)
);

-- Indexes
CREATE INDEX idx_carts_user ON carts(user_id);
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_number ON orders(order_number);
CREATE INDEX idx_purchased_coupons_user ON purchased_coupons(user_id);
CREATE INDEX idx_purchased_coupons_code ON purchased_coupons(coupon_code);
CREATE INDEX idx_purchased_coupons_status ON purchased_coupons(status);
