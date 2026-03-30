-- Partner staff table
CREATE TABLE IF NOT EXISTS staff (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL DEFAULT 'CASHIER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_staff_user_id ON staff(user_id);
CREATE INDEX IF NOT EXISTS idx_staff_user_id_id ON staff(user_id, id);

