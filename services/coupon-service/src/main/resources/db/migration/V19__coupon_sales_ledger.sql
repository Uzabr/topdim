-- Idempotency ledger для регистрации продаж купонов.
-- Один заказ не может зарегистрировать продажу одного и того же купона/опции дважды.
CREATE TABLE IF NOT EXISTS coupon_sales (
    id               BIGSERIAL PRIMARY KEY,
    order_id         BIGINT         NOT NULL,
    coupon_offer_id  BIGINT         NOT NULL,
    coupon_option_id BIGINT         NOT NULL,
    quantity         INT            NOT NULL,
    amount           NUMERIC(12,2)  NOT NULL,
    created_at       TIMESTAMP      DEFAULT NOW(),
    CONSTRAINT uq_coupon_sales_order_offer_option
        UNIQUE (order_id, coupon_offer_id, coupon_option_id)
);

CREATE INDEX IF NOT EXISTS idx_coupon_sales_offer ON coupon_sales(coupon_offer_id);
