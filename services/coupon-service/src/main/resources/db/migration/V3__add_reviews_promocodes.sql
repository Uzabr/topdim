CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_offer_id BIGINT NOT NULL REFERENCES coupon_offers(id),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reject_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reviews_status ON reviews(status);
CREATE INDEX idx_reviews_coupon_id ON reviews(coupon_offer_id);

CREATE TABLE promo_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    discount_amount DECIMAL(10,2) NOT NULL,
    is_percentage BOOLEAN NOT NULL DEFAULT FALSE,
    usage_limit INT,
    used_count INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_promo_codes_code ON promo_codes(code);
