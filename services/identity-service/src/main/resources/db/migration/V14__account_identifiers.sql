-- Google OAuth идентификатор + факт первой оплаты (для вычисляемого L1).
ALTER TABLE users
    ADD COLUMN google_sub VARCHAR(64),
    ADD COLUMN paid_at    TIMESTAMP;
ALTER TABLE users ADD CONSTRAINT uq_users_google_sub UNIQUE (google_sub);
CREATE INDEX idx_users_google_sub ON users (google_sub);
