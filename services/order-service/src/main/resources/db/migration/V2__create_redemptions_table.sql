-- Redemptions table: records when a purchased coupon is used at a merchant
CREATE TABLE IF NOT EXISTS redemptions (
    id                    BIGSERIAL PRIMARY KEY,
    purchased_coupon_id   BIGINT NOT NULL UNIQUE REFERENCES purchased_coupons(id),
    redemption_code       VARCHAR(32) NOT NULL UNIQUE,
    merchant_id           BIGINT NOT NULL,
    redeemed_by_staff     VARCHAR(255),
    note                  TEXT,
    redeemed_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_redemptions_merchant ON redemptions(merchant_id);
CREATE INDEX idx_redemptions_code ON redemptions(redemption_code);
