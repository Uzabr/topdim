DROP INDEX IF EXISTS uq_partner_applications_pending_phone;

CREATE UNIQUE INDEX IF NOT EXISTS uq_partner_applications_active_phone
    ON partner_applications(phone)
    WHERE status IN ('PENDING', 'PROCESSING');
