-- =============================================================
-- V14: Backfill merchant_locations + offer_description
-- Release 1: deterministic data migration
-- =============================================================

-- ─── Part 1: Backfill merchant_locations ─────────────────────

-- 1a) Create primary location from merchant-level data
--     (where merchant has at least one non-empty contact field)
INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, is_primary, active)
SELECT m.id,
       'Основной адрес',
       NULLIF(TRIM(m.address), ''),
       NULLIF(TRIM(m.phone), ''),
       NULLIF(TRIM(m.working_hours), ''),
       true,
       true
FROM merchants m
WHERE COALESCE(NULLIF(TRIM(m.address), ''), NULLIF(TRIM(m.phone), ''), NULLIF(TRIM(m.working_hours), '')) IS NOT NULL;

-- 1b) Create additional locations from coupon-level data
--     Only for tuples that differ from the merchant-level tuple
--     Uses DISTINCT ON to avoid duplicates per merchant+address combo
INSERT INTO merchant_locations (merchant_id, title, address, phone, working_hours, is_primary, active)
SELECT DISTINCT ON (co.merchant_id, COALESCE(NULLIF(TRIM(co.address), ''), ''), COALESCE(NULLIF(TRIM(co.contact_phone), ''), ''))
       co.merchant_id,
       'Адрес из купона: ' || LEFT(co.title, 50),
       NULLIF(TRIM(co.address), ''),
       NULLIF(TRIM(co.contact_phone), ''),
       NULLIF(TRIM(co.working_hours), ''),
       false,
       true
FROM coupon_offers co
WHERE COALESCE(NULLIF(TRIM(co.address), ''), NULLIF(TRIM(co.contact_phone), ''), NULLIF(TRIM(co.working_hours), '')) IS NOT NULL
  -- Exclude tuples that match the merchant-level data
  AND NOT EXISTS (
      SELECT 1 FROM merchant_locations ml
      WHERE ml.merchant_id = co.merchant_id
        AND ml.is_primary = true
        AND COALESCE(ml.address, '') = COALESCE(NULLIF(TRIM(co.address), ''), '')
        AND COALESCE(ml.phone, '') = COALESCE(NULLIF(TRIM(co.contact_phone), ''), '')
  )
ORDER BY co.merchant_id, COALESCE(NULLIF(TRIM(co.address), ''), ''), COALESCE(NULLIF(TRIM(co.contact_phone), ''), ''), co.created_at ASC;

-- 1c) For merchants that had NO merchant-level data but DO have coupon-level data,
--     promote the earliest location to primary
UPDATE merchant_locations ml
SET is_primary = true
WHERE ml.id = (
    SELECT ml2.id
    FROM merchant_locations ml2
    WHERE ml2.merchant_id = ml.merchant_id
    ORDER BY ml2.created_at ASC
    LIMIT 1
)
AND ml.merchant_id IN (
    -- Merchants that still have no primary location
    SELECT m.id FROM merchants m
    WHERE NOT EXISTS (
        SELECT 1 FROM merchant_locations ml3
        WHERE ml3.merchant_id = m.id AND ml3.is_primary = true
    )
    AND EXISTS (
        SELECT 1 FROM merchant_locations ml4
        WHERE ml4.merchant_id = m.id
    )
);


-- ─── Part 2: Backfill offer_description ──────────────────────

-- Deterministic concatenation in fixed order:
-- 1. short_description (lead paragraph)
-- 2. full_description (main body)
-- 3. terms (under "## Условия")
-- 4. usage_rules (under "## Правила использования")
-- 5. how_to_use (under "## Как использовать")
-- Empty/null parts are skipped.

UPDATE coupon_offers
SET offer_description = CONCAT_WS(
    E'\n\n',
    NULLIF(TRIM(short_description), ''),
    NULLIF(TRIM(full_description), ''),
    CASE WHEN NULLIF(TRIM(terms), '') IS NOT NULL
         THEN '## Условия' || E'\n' || TRIM(terms)
         ELSE NULL END,
    CASE WHEN NULLIF(TRIM(usage_rules), '') IS NOT NULL
         THEN '## Правила использования' || E'\n' || TRIM(usage_rules)
         ELSE NULL END,
    CASE WHEN NULLIF(TRIM(how_to_use), '') IS NOT NULL
         THEN '## Как использовать' || E'\n' || TRIM(how_to_use)
         ELSE NULL END
)
WHERE offer_description IS NULL
  AND COALESCE(
      NULLIF(TRIM(short_description), ''),
      NULLIF(TRIM(full_description), ''),
      NULLIF(TRIM(terms), ''),
      NULLIF(TRIM(usage_rules), ''),
      NULLIF(TRIM(how_to_use), '')
  ) IS NOT NULL;
