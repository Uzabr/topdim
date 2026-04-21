-- =============================================================
-- V13: Create merchant_locations table + offer_description column
-- Release 1: coupon + merchant data model refactoring
-- =============================================================

-- 1) merchant_locations — normalized location/contact data
CREATE TABLE merchant_locations (
    id            BIGSERIAL    PRIMARY KEY,
    merchant_id   BIGINT       NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    title         VARCHAR(255),
    address       VARCHAR(500),
    phone         VARCHAR(50),
    working_hours VARCHAR(255),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    is_primary    BOOLEAN      NOT NULL DEFAULT false,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Index for merchant FK lookups
CREATE INDEX idx_merchant_locations_merchant_id ON merchant_locations(merchant_id);

-- Partial unique index: max 1 primary location per merchant
CREATE UNIQUE INDEX uq_merchant_locations_primary
    ON merchant_locations(merchant_id) WHERE is_primary = true;

-- 2) Add offer_description to coupon_offers (single canonical text field)
--    Legacy columns (short_description, full_description, terms, usage_rules,
--    how_to_use, address, contact_phone, working_hours) are NOT removed yet.
ALTER TABLE coupon_offers ADD COLUMN IF NOT EXISTS offer_description TEXT;
