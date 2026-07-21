CREATE TABLE coupon_questions (
    id BIGSERIAL PRIMARY KEY,
    coupon_offer_id BIGINT NOT NULL REFERENCES coupon_offers(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(100),
    question TEXT NOT NULL,
    answer TEXT,
    answered_by_user_id BIGINT,
    answered_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_coupon_questions_offer_status ON coupon_questions(coupon_offer_id, status);
CREATE INDEX idx_coupon_questions_status ON coupon_questions(status);
