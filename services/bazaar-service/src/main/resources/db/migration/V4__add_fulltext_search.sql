-- Full-text search with GIN indexes for bazaar-service

-- Shops search vector
ALTER TABLE shops ADD COLUMN IF NOT EXISTS search_vector tsvector;

UPDATE shops SET search_vector =
    to_tsvector('russian', coalesce(name, '') || ' ' || coalesce(goods_description, ''));

CREATE INDEX IF NOT EXISTS idx_shops_search ON shops USING GIN(search_vector);

-- Trigger for auto-update
CREATE OR REPLACE FUNCTION shops_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('russian', coalesce(NEW.name, '') || ' ' || coalesce(NEW.goods_description, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_shops_search ON shops;
CREATE TRIGGER trg_shops_search
    BEFORE INSERT OR UPDATE OF name, goods_description
    ON shops
    FOR EACH ROW
    EXECUTE FUNCTION shops_search_trigger();

-- Bazaar name search
ALTER TABLE bazaars ADD COLUMN IF NOT EXISTS search_vector tsvector;

UPDATE bazaars SET search_vector =
    to_tsvector('russian', coalesce(name, '') || ' ' || coalesce(name_uz, '') || ' ' || coalesce(address, ''));

CREATE INDEX IF NOT EXISTS idx_bazaars_search ON bazaars USING GIN(search_vector);

CREATE OR REPLACE FUNCTION bazaars_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('russian', coalesce(NEW.name, '') || ' ' || coalesce(NEW.name_uz, '') || ' ' || coalesce(NEW.address, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_bazaars_search ON bazaars;
CREATE TRIGGER trg_bazaars_search
    BEFORE INSERT OR UPDATE OF name, name_uz, address
    ON bazaars
    FOR EACH ROW
    EXECUTE FUNCTION bazaars_search_trigger();
