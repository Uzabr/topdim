-- Idempotency ledger для погашений купонов.
-- Один purchased_coupon_id не может быть учтён в redeemedCount дважды.
CREATE TABLE IF NOT EXISTS coupon_redemption_ledger (
    id                  BIGSERIAL PRIMARY KEY,
    purchased_coupon_id BIGINT NOT NULL,
    coupon_offer_id     BIGINT NOT NULL,
    coupon_option_id    BIGINT NOT NULL,
    merchant_id         BIGINT,
    created_at          TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_redemption_ledger_purchased_coupon UNIQUE (purchased_coupon_id)
);

CREATE INDEX IF NOT EXISTS idx_redemption_ledger_offer ON coupon_redemption_ledger(coupon_offer_id);
