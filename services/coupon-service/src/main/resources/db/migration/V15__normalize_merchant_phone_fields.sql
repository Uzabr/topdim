-- V15: Normalize phone fields to canonical format (+digits only).
-- Strips all non-digit characters except leading +, adds + if missing.

-- Normalize merchants.phone
UPDATE merchants
SET phone = '+' || regexp_replace(
    CASE WHEN phone LIKE '+%' THEN substring(phone FROM 2) ELSE phone END,
    '[^0-9]', '', 'g'
)
WHERE phone IS NOT NULL
  AND phone <> ''
  AND phone <> '+' || regexp_replace(
      CASE WHEN phone LIKE '+%' THEN substring(phone FROM 2) ELSE phone END,
      '[^0-9]', '', 'g'
  );

-- Normalize merchant_locations.phone
UPDATE merchant_locations
SET phone = '+' || regexp_replace(
    CASE WHEN phone LIKE '+%' THEN substring(phone FROM 2) ELSE phone END,
    '[^0-9]', '', 'g'
)
WHERE phone IS NOT NULL
  AND phone <> ''
  AND phone <> '+' || regexp_replace(
      CASE WHEN phone LIKE '+%' THEN substring(phone FROM 2) ELSE phone END,
      '[^0-9]', '', 'g'
  );
