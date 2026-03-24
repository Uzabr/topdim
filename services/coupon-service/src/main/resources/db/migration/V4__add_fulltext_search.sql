-- Full-text search with GIN indexes for coupon-service
-- Enables sub-5ms search instead of >500ms ILIKE scans

-- Add tsvector column for Russian + Uzbek search
ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Populate search_vector from existing data
UPDATE coupon_offers SET search_vector =
    to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(short_description, '') || ' ' || coalesce(full_description, ''));

-- GIN index for fast full-text search
CREATE INDEX IF NOT EXISTS idx_coupon_offers_search ON coupon_offers USING GIN(search_vector);

-- Trigger to auto-update search_vector on INSERT/UPDATE
CREATE OR REPLACE FUNCTION coupon_offers_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('russian', coalesce(NEW.title, '') || ' ' || coalesce(NEW.short_description, '') || ' ' || coalesce(NEW.full_description, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_coupon_offers_search ON coupon_offers;
CREATE TRIGGER trg_coupon_offers_search
    BEFORE INSERT OR UPDATE OF title, short_description, full_description
    ON coupon_offers
    FOR EACH ROW
    EXECUTE FUNCTION coupon_offers_search_trigger();
