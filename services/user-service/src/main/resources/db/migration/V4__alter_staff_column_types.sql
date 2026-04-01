-- Align staff columns with Hibernate entity mappings
ALTER TABLE staff
    ALTER COLUMN phone TYPE VARCHAR(255);

ALTER TABLE staff
    ALTER COLUMN role TYPE VARCHAR(255);

