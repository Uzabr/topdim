-- Rows created before this migration may legitimately contain duplicate PENDING
-- complaints because the old application performed no uniqueness check. Keep that
-- history unchanged, while making every future insert participate in enforcement.
ALTER TABLE complaints
    ADD COLUMN enforce_pending_uniqueness BOOLEAN;

UPDATE complaints
SET enforce_pending_uniqueness = FALSE;

ALTER TABLE complaints
    ALTER COLUMN enforce_pending_uniqueness SET DEFAULT TRUE,
    ALTER COLUMN enforce_pending_uniqueness SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_complaints_pending_coupon
    ON complaints (purchased_coupon_id)
    WHERE purchased_coupon_id IS NOT NULL
      AND status = 'PENDING'
      AND enforce_pending_uniqueness = TRUE;
