ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS address TEXT;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS working_hours VARCHAR(255);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS business_category VARCHAR(100);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS website VARCHAR(255);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS telegram_username VARCHAR(100);
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'WEB';
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS linked_user_id BIGINT;
ALTER TABLE partner_applications ADD COLUMN IF NOT EXISTS linked_merchant_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_partner_applications_status_created_at
    ON partner_applications(status, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_partner_applications_pending_phone
    ON partner_applications(phone)
    WHERE status = 'PENDING';
