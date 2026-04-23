-- V17: Align search_vector with canonical coupon schema after legacy column drop.

UPDATE coupon_offers
SET search_vector =
    to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(offer_description, ''));

CREATE OR REPLACE FUNCTION coupon_offers_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('russian', coalesce(NEW.title, '') || ' ' || coalesce(NEW.offer_description, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_coupon_offers_search ON coupon_offers;
CREATE TRIGGER trg_coupon_offers_search
    BEFORE INSERT OR UPDATE OF title, offer_description
    ON coupon_offers
    FOR EACH ROW
    EXECUTE FUNCTION coupon_offers_search_trigger();
